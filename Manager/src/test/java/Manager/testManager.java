package Manager;

import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

public class testManager {
    public static void main(String[] args) throws IOException {
        OkHttpClient client = new OkHttpClient().newBuilder()
            .build();
        String time = Long.toString(System.currentTimeMillis() /1000);

        String nowUrl = "http://prometheus:9090/api/v1/query?query=sum+by%28function%29%28increase%28qps%5B10s%5D%29%29&time=" + time;
        System.out.println(nowUrl);
        Request request = new Request.Builder().url(nowUrl).build();
        ResponseBody responseBody = client.newCall(request).execute().body();
        System.out.println(responseBody.string());
    }
}
