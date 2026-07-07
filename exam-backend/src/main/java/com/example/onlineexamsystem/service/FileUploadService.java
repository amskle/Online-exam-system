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
 * File upload service.
 */
@Service
@Slf4j
public class FileUploadService {
    private static final String DEFAULT_SUB_DIR = "default";
    private static final String ACCESS_PREFIX = "/files/";

    @Value("${file.upload-dir}")
    private String uploadDir;

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

    private Path resolveUploadRoot() {
        return Paths.get(System.getProperty("user.dir"), uploadDir).toAbsolutePath().normalize();
    }

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

    private String getFileExtension(String originalFilename) {
        String filename = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename);
        int extensionIndex = filename.lastIndexOf(".");
        if (extensionIndex == -1 || extensionIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(extensionIndex);
    }
}
