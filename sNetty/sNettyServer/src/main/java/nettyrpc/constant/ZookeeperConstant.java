package com.momo.nettyrpc.constant;

/**
 * zookeeper有关常量
 *
 * @author Eric
 */
public interface ZookeeperConstant {
    int ZK_SESSION_TIMEOUT = 5000;
    String ZK_REGISTRY_PATH = "/registry";
    String ZK_DATA_PATH = ZK_REGISTRY_PATH + "/data";
}
