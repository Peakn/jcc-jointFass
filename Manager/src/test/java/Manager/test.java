package Manager;

import com.aliyuncs.fc.client.FunctionComputeClient;
import com.aliyuncs.fc.model.Code;
import com.aliyuncs.fc.request.CreateFunctionRequest;
import com.aliyuncs.fc.request.InvokeFunctionRequest;
import com.aliyuncs.fc.request.UpdateFunctionRequest;
import com.aliyuncs.fc.response.CreateFunctionResponse;
import com.aliyuncs.fc.response.InvokeFunctionResponse;
import com.aliyuncs.fc.response.UpdateFunctionResponse;
import jdk.nashorn.internal.ir.FunctionNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class test {
    private static final String REGION = "cn-hangzhou";
    private static final String SERVICE_NAME = "demo";
    private static final String ROLE = "acs:ram::1727804214750599:role/AliyunFCLogExecutionRole";

    public static void main(String[] args) throws IOException {
        String accessKey = "LTAI4GKXKDT4w1fQ9ZRfXaM1";
        String accessSecretKey = "fyRmzUgUlWpBX59NkFgik49L3jF0Rj";
        String accountId = "1727804214750599";

        FunctionComputeClient fcClient = new FunctionComputeClient(REGION, accountId, accessKey, accessSecretKey);

        CreateFunctionRequest cfRes = new CreateFunctionRequest(SERVICE_NAME);
        cfRes.setFunctionName("test4");
        cfRes.setRuntime("python3");
        cfRes.setHandler("counter.handler");
        Code code = new Code().setDir("/Users/chenpeng/Desktop/jcc-jointFass/code");
        cfRes.setCode(code);

        CreateFunctionResponse cfResp = fcClient.createFunction(cfRes);
        System.out.println(cfResp.getEnvironmentVariables());

        InvokeFunctionRequest invkReq = new InvokeFunctionRequest(SERVICE_NAME, "test4");
        String a = "Hello FunctionCompute!";
        invkReq.setPayload(a.getBytes());
        InvokeFunctionResponse invkResp = fcClient.invokeFunction(invkReq);
        System.out.println("Function invoke success, requestedId: " + invkResp.getRequestId());
        System.out.println("Run result：" + new String(invkResp.getContent()));
        System.out.println("Run logs: " + invkResp.getLogResult());

//
//        System.out.println("******************update******************");
//        UpdateFunctionRequest ufReq = new UpdateFunctionRequest(SERVICE_NAME, "test4");
//        UpdateFunctionResponse ufResp = fcClient.updateFunction(ufReq);
//        System.out.println(ufResp.getEnvironmentVariables());
    }
}
