import cn.hutool.http.HttpUtil;
import org.junit.jupiter.api.Test;

public class pressureTest {
    @Test
    void test() throws InterruptedException {
        int threadCount = 12;
        for (int i = 0; i < threadCount; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        String result = HttpUtil.get("http://localhost:8076/");
                        System.out.println(Thread.currentThread().getName() + " 请求成功");
                    }
                }
            }).start();
        }

        // ⭐ 关键：让主线程一直等待，不结束
        Thread.currentThread().join(); // 主线程等待自己，永远不会结束
        // 或者用 TimeUnit.DAYS.sleep(1); // 睡一天
    }
}
