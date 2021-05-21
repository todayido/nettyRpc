package com.momo.nettyrpc.test.server;

import com.momo.nettyrpc.server.RpcService;
import com.momo.nettyrpc.test.client.HelloService;
import com.momo.nettyrpc.test.client.Person;

@RpcService(HelloService.class)
public class HelloServiceImpl implements HelloService {

    public HelloServiceImpl(){

    }

    @Override
    public String hello(String name) {
        return "Hello! " + name;
    }

    @Override
    public String hello(Person person) {
        return "Hello! " + person.getFirstName() + " " + person.getLastName();
    }
}
