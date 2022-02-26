package nettyrpc.proxy;


import com.momo.nettyrpc.client.RPCFuture;

/**
 * @author Eric
 */
public interface IAsyncObjectProxy {
    public RPCFuture call(String funcName, Object... args);
}