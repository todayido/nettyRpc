package nettyrpc;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class StartServer {

    public static void main(String[] args) {
        new ClassPathXmlApplicationContext("server-spring.xml");
    }

}
