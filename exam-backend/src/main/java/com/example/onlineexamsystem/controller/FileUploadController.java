package com.example.onlineexamsystem.controller;

import com.example.onlineexamsystem.annotation.Auth;
import com.example.onlineexamsystem.common.exception.BusinessException;
import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.vo.FileUploadResponseVO;
import com.example.onlineexamsystem.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传控制器
 */
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Auth({1, 2, 3})
public class FileUploadController {
    private final FileUploadService fileUploadService;

    /**
     * 文件上传
     *
     * @return Result<FileUploadResponseVO>
     */
    /**
     * 上传文件
     *
     * @return Result<FileUploadResponseVO>
     */
    @PostMapping("/upload")
    public Result<FileUploadResponseVO> uploadFile(
            @RequestParam("file")MultipartFile file,
            @RequestParam(value = "subDir", required = false) String subDir) {
        try {
            String filePath = fileUploadService.uploadFile(file, subDir);
            FileUploadResponseVO fileUploadResponseVO = FileUploadResponseVO.builder()
                    .fileSize(file.getSize())
                    .fileName(file.getOriginalFilename())
                    .filePath(filePath)
                    .build();
                    return Result.success(fileUploadResponseVO);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage(), 400);
        } catch (Exception e) {
            throw new BusinessException("文件上传失败，请稍后重试");
        }
    }
}
