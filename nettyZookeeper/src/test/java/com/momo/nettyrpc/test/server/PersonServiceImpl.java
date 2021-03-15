package com.momo.nettyrpc.test.server;

import com.momo.nettyrpc.test.client.PersonService;
import com.momo.nettyrpc.test.client.Person;
import com.momo.nettyrpc.server.RpcService;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by luxiaoxun on 2016-03-10.
 */
@RpcService(PersonService.class)
public class PersonServiceImpl implements PersonService {

    @Override
    public List<Person> GetTestPerson(String name, int num) {
        List<Person> persons = new ArrayList<>(num);
        for (int i = 0; i < num; ++i) {
            persons.add(new Person(Integer.toString(i), name));
        }
        return persons;
    }
}
