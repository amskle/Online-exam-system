package com.example.onlineexamsystem.config;


import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * 文件上传配置类
 */
@Configuration
public class FileUploadConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * 初始化上传目录（启动时创建）
     */
    @PostConstruct
    public void init() {
        String projectRoot = System.getProperty("user.dir");
        String fullUploadPath = projectRoot + File.separator + uploadDir;

        File uploadDirectory = new File(fullUploadPath);
        if (!uploadDirectory.exists()) {
            uploadDirectory.mkdirs();
        }
    }

    /**
     * 配置静态资源映射（文件上传访问）
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String projectRoot = System.getProperty("user.dir");
        String fullUploadPath = "file:" + projectRoot + File.separator + uploadDir + File.separatorChar;
        registry.addResourceHandler("/files/**")
                .addResourceLocations(fullUploadPath);

    }

}
