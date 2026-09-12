import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Disabled("手工压力测试，禁止在单元测试/CI 中执行")
public class pressureTest {
    @Test
    void test() throws InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:8076/"))
                .GET()
                .build();
        int threadCount = 12;
        for (int i = 0; i < threadCount; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            client.send(request, HttpResponse.BodyHandlers.discarding());
                            System.out.println(Thread.currentThread().getName() + " 请求成功");
                        } catch (Exception e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }).start();
        }

        // ⭐ 关键：让主线程一直等待，不结束
        Thread.currentThread().join(); // 主线程等待自己，永远不会结束
        // 或者用 TimeUnit.DAYS.sleep(1); // 睡一天
    }
}
