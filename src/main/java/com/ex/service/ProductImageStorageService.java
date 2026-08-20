package com.ex.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductImageStorageService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif");

    private final Path storageDirectory;

    public ProductImageStorageService(
            @Value("${feedflow.upload.product-image-dir:uploads/product-images}")
            String storageDirectory) {
        this.storageDirectory = Path.of(storageDirectory)
                .toAbsolutePath().normalize();
    }

    public String store(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("첨부할 상품 이미지를 선택해 주세요.");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("상품 이미지는 10MB 이하만 첨부할 수 있습니다.");
        }
        String contentType = image.getContentType() == null
                ? "" : image.getContentType().toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new IllegalArgumentException("JPG, PNG, GIF 이미지 파일만 첨부할 수 있습니다.");
        }
        try (InputStream input = image.getInputStream()) {
            if (ImageIO.read(input) == null) {
                throw new IllegalArgumentException("올바른 이미지 파일이 아닙니다.");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("상품 이미지를 확인할 수 없습니다.", exception);
        }

        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
        Path destination = storageDirectory.resolve(fileName).normalize();
        if (!destination.getParent().equals(storageDirectory)) {
            throw new IllegalStateException("상품 이미지 저장 경로가 올바르지 않습니다.");
        }
        try {
            Files.createDirectories(storageDirectory);
            try (InputStream input = image.getInputStream()) {
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("상품 이미지를 저장하지 못했습니다.", exception);
        }
        return "/uploads/product-images/" + fileName;
    }
}
