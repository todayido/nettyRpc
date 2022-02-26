package com.momo.nettyrpc.test.app;


import com.momo.nettyrpc.client.RPCFuture;
import com.momo.nettyrpc.client.RpcClient;
import com.momo.nettyrpc.proxy.IAsyncObjectProxy;
import com.momo.nettyrpc.test.client.HelloService;
import com.momo.nettyrpc.test.client.OrderService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.ExecutionException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:client-spring.xml")
public class ClientTest {

    @Autowired
    private RpcClient rpcClient;

    @Test
    public void getPrice() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
//            OrderService orderService = rpcClient.create(OrderService.class);
            IAsyncObjectProxy async = rpcClient.createAsync(HelloService.class);
            RPCFuture rpcFuture = async.call("hello", "小红");
            try {
                Object o = rpcFuture.get();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
//            int price = orderService.getPrice(i);
//            System.out.println(price);
        }
        System.out.println("========= 调用一千次耗时：" + (System.currentTimeMillis() - start));

    }
}
