package com.momo.nettyrpc.client;

import com.momo.nettyrpc.common.RpcRequest;
import com.momo.nettyrpc.common.RpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.ReentrantLock;

/**
 * RPCFuture
 * @author Eric
 */
public class RPCFuture implements Future<Object> {
    private static final Logger logger = LoggerFactory.getLogger(RPCFuture.class);

    private Sync sync;
    private RpcRequest request;
    private RpcResponse response;
    private long startTime;
    private long responseTimeThreshold = 5000;

    private List<AsyncRPCCallback> pendingCallbacks = new ArrayList<AsyncRPCCallback>();
    private ReentrantLock lock = new ReentrantLock();

    public RPCFuture(RpcRequest request) {
        this.sync = new Sync();
        this.request = request;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public boolean isDone() {
        return sync.isDone();
    }

    @Override
    public Object get() throws InterruptedException, ExecutionException {
        sync.acquire(-1);// 模板模式，先调用自己实现的tryAcquire方法，返回true,父类中的acquire(null, arg, false, false, false, 0L);就不会再执行了。
        if (this.response != null) {
            return this.response.getResult();// 返回结果，这笔交易就完成了。如果继续调用rpc那么还是会在生成一个RPCFuture，再重复一遍操作（发送，返回，释放锁，get结果。）。
        } else {
            return null;
        }
    }

    @Override
    public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        boolean success = sync.tryAcquireNanos(-1, unit.toNanos(timeout));
        if (success) {
            if (this.response != null) {
                return this.response.getResult();
            } else {
                return null;
            }
        } else {
            throw new RuntimeException("Timeout exception. Request id: " + this.request.getRequestId()
                    + ". Request class name: " + this.request.getClassName()
                    + ". Request method: " + this.request.getMethodName());
        }
    }

    @Override
    public boolean isCancelled() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
    }

    // rpc调用完这个方法之后，会释放锁，然后get方法就可以继续往下执行，返回结果。
    public void done(RpcResponse reponse) {
        this.response = reponse;
        sync.release(1);// 远程接口返回，将state设置为done(1)，此时get方法就可以继续执行了。
        invokeCallbacks();// get方法的执行和这个回调没有关系，如果添加了回调就执行，但是此处没有任何回调方法，后期可以做一些其他的相关事务处理
        // Threshold
        long responseTime = System.currentTimeMillis() - startTime;
        if (responseTime > this.responseTimeThreshold) {
            logger.warn("Service response time is too slow. Request id = " + reponse.getRequestId() + ". Response Time = " + responseTime + "ms");
        }
    }

    // 添加回调函数必须是同步的，此处使用ReentrantLock
    private void invokeCallbacks() {
        lock.lock();
        try {
            for (final AsyncRPCCallback callback : pendingCallbacks) {
                runCallback(callback);// 提交线程任务，线程池的大小是16。
            }
        } finally {// 此处使用ReentrantLock和finally必须是成对出现的。
            lock.unlock();
        }
    }

    // 添加回调函数必须是同步的，此处使用ReentrantLock
    public RPCFuture addCallback(AsyncRPCCallback callback) {
        lock.lock();
        try {
            if (isDone()) {
                runCallback(callback);
            } else {
                this.pendingCallbacks.add(callback);
            }
        } finally {
            lock.unlock();
        }
        return this;
    }

    private void runCallback(final AsyncRPCCallback callback) {
        final RpcResponse res = this.response;
        RpcClient.submit(new Runnable() {
            @Override
            public void run() {
                if (!res.isError()) {
                    callback.success(res.getResult());
                } else {
                    callback.fail(new RuntimeException("Response error", new Throwable(res.getError())));
                }
            }
        });
    }

    static class Sync extends AbstractQueuedSynchronizer {

        private static final long serialVersionUID = 1L;

        //future status
        private final int done = 1;
        private final int pending = 0;

        @Override
        protected boolean tryAcquire(int arg) {
            return getState() == done;
        }

        @Override
        protected boolean tryRelease(int arg) {
            if (getState() == pending) {
                if (compareAndSetState(pending, done)) {// U.compareAndSetInt(this, STATE, expect, update);
                    return true;
                } else {
                    return false;
                }
            } else {
                return true;
            }
        }

        public boolean isDone() {
            getState();
            return getState() == done;
        }
    }
}
