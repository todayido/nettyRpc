package com.momo.nettyrpc.client;

/**
 * @author Eric
 */
public interface AsyncRPCCallback {

    void success(Object result);

    void fail(Exception e);

}
