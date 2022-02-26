package com.momo.nettyrpc.common;

import lombok.Data;

/**
 * Rpc Response 服务端返回的结果对象
 *
 * @author Eric
 */
@Data
public class RpcResponse {

    private String requestId;
    private String error;
    private Object result;

    public boolean isError() {
        return error != null;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }
}
