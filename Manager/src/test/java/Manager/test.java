package Manager;

import com.aliyuncs.fc.client.FunctionComputeClient;
import com.aliyuncs.fc.request.InvokeFunctionRequest;
import com.aliyuncs.fc.response.InvokeFunctionResponse;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;

public class test {
    private static final String REGION = "cn-hangzhou";
    private static final String SERVICE_NAME = "demo";
    private static final String ROLE = "acs:ram::1727804214750599:role/AliyunFCLogExecutionRole";

    public static void main(String[] args) throws IOException {
        String accessKey = "LTAI4GKXKDT4w1fQ9ZRfXaM1";
        String accessSecretKey = "fyRmzUgUlWpBX59NkFgik49L3jF0Rj";
        String accountId = "1727804214750599";

        FunctionComputeClient fcClient = new FunctionComputeClient(REGION, accountId, accessKey, accessSecretKey);

        InvokeFunctionRequest invkReq = new InvokeFunctionRequest(SERVICE_NAME, "test5");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("str1", "cccpppp ");
        jsonObject.put("str2", "come on");
        System.out.println(jsonObject);
        String payload = jsonObject.toJSONString();
        System.out.println("payload: " + payload);

        invkReq.setPayload(payload.getBytes());

        InvokeFunctionResponse invkResp = fcClient.invokeFunction(invkReq);

        System.out.println("Function invoke success, requestedId: " + invkResp.getRequestId());
        System.out.println("Run result：" + new String(invkResp.getContent()));
    }
}
