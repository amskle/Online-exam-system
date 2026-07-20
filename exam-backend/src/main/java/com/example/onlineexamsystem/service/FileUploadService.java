package com.example.onlineexamsystem.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 文件上传服务
 */
@Service
@Slf4j
public class FileUploadService {
    private static final String DEFAULT_SUB_DIR = "default";
    private static final String ACCESS_PREFIX = "/files/";

    @Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * 上传文件到指定子目录，返回可访问路径
     *
     * @param file   上传的文件
     * @param subDir 存储子目录，为空时使用默认目录
     * @return 文件可访问路径
     */
    public String uploadFile(MultipartFile file, String subDir) throws IOException {
        if (file == null || file.isEmpty()) {
            log.warn("上传失败，文件为空");
            throw new IllegalArgumentException("文件不能为空");
        }

        String normalizedSubDir = normalizeSubDir(subDir);
        String uniqueFileName = UUID.randomUUID() + getFileExtension(file.getOriginalFilename());
        Path uploadPath = resolveUploadRoot().resolve(normalizedSubDir).normalize();

        Files.createDirectories(uploadPath);

        Path destination = uploadPath.resolve(uniqueFileName);
        try {
            file.transferTo(destination);
            log.info("文件上传成功 {}", destination.toAbsolutePath());
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw e;
        }

        return ACCESS_PREFIX + normalizedSubDir + "/" + uniqueFileName;
    }

    /**
     * 删除文件
     *
     * @param filePath 文件可访问路径
     * @return 是否删除成功
     */
    public boolean deleteFile(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            log.warn("删除文件失败，文件路径为空");
            return false;
        }

        try {
            Path fileToDelete = resolveFilePath(filePath);

            if (Files.deleteIfExists(fileToDelete)) {
                log.info("文件删除成功 {}", fileToDelete.toAbsolutePath());
                return true;
            }

            log.warn("删除文件失败，找不到该文件{}", fileToDelete.toAbsolutePath());
            return false;
        } catch (IOException | IllegalArgumentException e) {
            log.error("删除文件失败{}", filePath, e);
            return false;
        }
    }

    /**
     * 解析上传根目录的绝对路径
     *
     * @return 上传根目录
     */
    private Path resolveUploadRoot() {
        return Paths.get(System.getProperty("user.dir"), uploadDir).toAbsolutePath().normalize();
    }

    /**
     * 将可访问路径解析为文件系统路径，并防止路径穿越
     *
     * @param filePath 文件可访问路径
     * @return 文件系统绝对路径
     */
    private Path resolveFilePath(String filePath) {
        String normalizedFilePath = filePath.replace("\\", "/");
        if (normalizedFilePath.startsWith(ACCESS_PREFIX)) {
            normalizedFilePath = normalizedFilePath.substring(ACCESS_PREFIX.length());
        } else if (normalizedFilePath.startsWith("/")) {
            normalizedFilePath = normalizedFilePath.substring(1);
        }

        Path uploadRoot = resolveUploadRoot();
        Path targetPath = uploadRoot.resolve(normalizedFilePath).normalize();
        if (!targetPath.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid file path");
        }
        return targetPath;
    }

    /**
     * 规范化子目录名称，防止路径穿越
     *
     * @param subDir 原始子目录
     * @return 规范化后的子目录
     */
    private String normalizeSubDir(String subDir) {
        String normalizedSubDir = StringUtils.hasText(subDir) ? subDir.trim().replace("\\", "/") : DEFAULT_SUB_DIR;
        while (normalizedSubDir.startsWith("/")) {
            normalizedSubDir = normalizedSubDir.substring(1);
        }
        while (normalizedSubDir.endsWith("/")) {
            normalizedSubDir = normalizedSubDir.substring(0, normalizedSubDir.length() - 1);
        }
        if (!StringUtils.hasText(normalizedSubDir)) {
            normalizedSubDir = DEFAULT_SUB_DIR;
        }

        Path uploadRoot = resolveUploadRoot();
        Path subDirPath = uploadRoot.resolve(normalizedSubDir).normalize();
        if (!subDirPath.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid upload directory");
        }
        return normalizedSubDir;
    }

    /**
     * 获取文件扩展名（含点号），无扩展名返回空串
     *
     * @param originalFilename 原始文件名
     * @return 文件扩展名
     */
    private String getFileExtension(String originalFilename) {
        String filename = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename);
        int extensionIndex = filename.lastIndexOf(".");
        if (extensionIndex == -1 || extensionIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(extensionIndex);
    }
}
