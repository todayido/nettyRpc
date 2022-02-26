package nettyrpc.common;

import lombok.Data;

/**
 * Rpc Request 发送请求的对象
 *
 * @author Eric
 */
@Data
public class RpcRequest {

    private String requestId;
    private String className;
    private String methodName;
    private Class<?>[] parameterTypes;
    private Object[] parameters;
}
