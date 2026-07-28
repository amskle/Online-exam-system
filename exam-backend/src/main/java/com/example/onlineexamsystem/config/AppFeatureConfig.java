package com.example.onlineexamsystem.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.feature")
public class AppFeatureConfig {
    private boolean exportEnabled;
    private int maxExportRows;
}
