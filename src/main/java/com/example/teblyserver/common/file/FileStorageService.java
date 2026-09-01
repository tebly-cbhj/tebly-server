package com.example.teblyserver.common.file;

import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.base-url}")
    private String baseUrl;

    public String storeProfileImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_FILE);
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // 허용된 이미지 확장자만 통과
        String lowerExt = extension.toLowerCase();
        if (!lowerExt.equals(".jpg") && !lowerExt.equals(".jpeg")
                && !lowerExt.equals(".png") && !lowerExt.equals(".webp")) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }

        String storedFilename = UUID.randomUUID() + extension;

        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path targetPath = uploadPath.resolve(storedFilename);
            Files.copy(file.getInputStream(), targetPath);

        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        return baseUrl + "/" + storedFilename;
    }

    public String storeFile(MultipartFile file, String domainPath) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_FILE);
        }

        // 1. 확장자 추출 및 검증 (기존 로직 유지)
        String originalFilename = file.getOriginalFilename();
        String extension = "";

        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String lowerExt = extension.toLowerCase();
        if (!lowerExt.equals(".jpg") && !lowerExt.equals(".jpeg")
                && !lowerExt.equals(".png") && !lowerExt.equals(".webp")) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }
        // 2. 도메인별 폴더 지정 (예: profile, room, category)
        String storedFilename = UUID.randomUUID() + extension;
        try {
            // uploadDir 하위에 domainPath(예: "room" 등)를 결합
            Path uploadPath = Paths.get(uploadDir).resolve(domainPath);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Path targetPath = uploadPath.resolve(storedFilename);

            // StandardCopyOption.REPLACE_EXISTING를 추가하여 안전하게 덮어쓰기 허용
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }
        // 3. 도메인별 최종 URL 리턴
        return baseUrl + "/" + domainPath + "/" + storedFilename;
    }

}