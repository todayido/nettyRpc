package com.momo.nettyrpc.test.server;


import com.momo.nettyrpc.test.client.OrderService;
import com.momo.nettyrpc.server.RpcService;

@RpcService(OrderService.class)
public class OrderServiceImpl implements OrderService{

    public OrderServiceImpl() {
    }

    @Override
    public int getPrice(int size) {
        System.out.println("进入了OrderServiceImpl的getPrice()方法，结果为"+50*size);
        return 50*size;
    }
}
