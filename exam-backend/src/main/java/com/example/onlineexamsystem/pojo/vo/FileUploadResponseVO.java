package com.example.onlineexamsystem.pojo.vo;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 文件上传响应
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileUploadResponseVO {
    private String fileName; // 原始文件名
    private Long fileSize; // 文件大小（字节）
    private String filePath; // 文件存储地址
}
