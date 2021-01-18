package com.fc.springcloud.provider.Impl.hcloud;


import com.fc.springcloud.pojo.dto.MetricsEvent;
import com.fc.springcloud.pojo.dto.MetricsResult;
import com.fc.springcloud.pojo.dto.PrometheusResponse;
import com.google.gson.Gson;
import java.sql.Timestamp;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class PrometheusResource {

  private static final Log logger = LogFactory.getLog(PrometheusResource.class);
  private final BlockingQueue<MetricsEvent> queue;
  private final ExecutorService backend;
  @Value("${mesh.prom.target}")
  String target;

  private class PrometheusPoller implements Runnable {

    final private String url;
    final private RestTemplate restTemplate;
    final BlockingQueue<MetricsEvent> queue;
    final HttpHeaders headers;

    PrometheusPoller(BlockingQueue<MetricsEvent> queue) {
      // 我真的懒得写了，简单点好
      this.url = "http://" + target
          + "/api/v1/query";
      restTemplate = new RestTemplate();
      headers = new HttpHeaders();
      headers.add("Content-Type", "application/x-www-form-urlencoded");

      this.queue = queue;
    }

    @SneakyThrows
    @Override
    public void run() {
      while (true) {
        String time = Long.toString(System.currentTimeMillis() / 1000);
        OkHttpClient client = new OkHttpClient().newBuilder()
            .build();
        HttpUrl.Builder httpBuilder = HttpUrl.parse(url).newBuilder();
        httpBuilder.addEncodedQueryParameter("query", "sum by(function)(increase(qps[10s]))")
            .addEncodedQueryParameter("time", time);
        Request request = new Request.Builder().url(httpBuilder.build()).build();
        ResponseBody responseBody = client.newCall(request).execute().body();
        if (responseBody == null) {
          logger.info("response body is null");
          continue;
        }
        Gson gson = new Gson();
        PrometheusResponse requestResult = gson
            .fromJson(responseBody.string(), PrometheusResponse.class);
        if (requestResult != null && requestResult.getStatus().equals("success")) {
          for (MetricsResult result : requestResult.getData().getResult()) {
            String functionName = result.getMetric().getString("function");
            Double timestamp = result.getValue().getDouble(0);
            long value = Long.parseLong(result.getValue().getString(1));
            this.queue.add(new MetricsEvent(functionName, (double) value,
                new Date(new Timestamp(timestamp.longValue()).getTime())));
          }
        }
        Thread.sleep(5000);
      }
    }
  }

  public PrometheusResource() {
    this.queue = new ArrayBlockingQueue<MetricsEvent>(100);
    this.backend = Executors.newCachedThreadPool();
  }

  public BlockingQueue<MetricsEvent> Register() {
    return this.queue;
  }

  public void Start() {
    this.backend.execute(new PrometheusPoller(this.queue));
  }
}
