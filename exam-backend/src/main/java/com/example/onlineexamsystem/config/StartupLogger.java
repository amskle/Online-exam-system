package com.example.onlineexamsystem.config;


import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
@Order(1)

public class StartupLogger implements ApplicationRunner {
    private final AppFeatureConfig appFeatureConfig;


    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("系统启动完成");
        log.info("服务端口: 8077");
        log.info("Swagger 文档: http://localhost:8077/swagger-ui.html");
        log.info("==================================");

        log.info("导出功能：{}, 最大行数：{}", appFeatureConfig.isExportEnabled(), appFeatureConfig.getMaxExportRows());
    }


}
