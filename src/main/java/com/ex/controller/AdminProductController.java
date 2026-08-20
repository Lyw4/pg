package com.ex.controller;

import com.ex.dto.AdminProductRequest;
import com.ex.dto.ProductResponse;
import com.ex.service.AdminProductService;
import com.ex.service.AdminActivityService;
import com.ex.service.ProductImageStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;
    private final AdminActivityService adminActivityService;
    private final ProductImageStorageService productImageStorageService;

    public record ImageUploadResponse(String imageUrl) {
    }

    /**
     * 관리자 상품 목록입니다. 판매 중지된 상품도 함께 돌려줍니다.
     *
     * <p>관리자 화면이 고객 카탈로그(/api/products)를 목록으로 쓰면 판매 중지한
     * 상품이 목록에서 사라져 다시 판매로 되돌릴 방법이 없어집니다.
     */
    @GetMapping
    public java.util.List<ProductResponse> findAll() {
        return adminProductService.findAllForAdmin();
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImageUploadResponse uploadImage(
            @RequestPart(name = "image") MultipartFile image) {
        return new ImageUploadResponse(productImageStorageService.store(image));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody AdminProductRequest request,
            Authentication authentication, HttpServletRequest servletRequest) {
        ProductResponse response = adminProductService.create(request);
        adminActivityService.record(authentication == null ? "system" : authentication.getName(),
                "PRODUCT_CREATED", "PRODUCT", String.valueOf(response.id()),
                "상품 등록: " + response.name(), servletRequest.getRemoteAddr());
        return response;
    }

    @PutMapping("/{productId}")
    public ProductResponse update(
            @PathVariable(name = "productId") Long productId,
            @Valid @RequestBody AdminProductRequest request,
            Authentication authentication, HttpServletRequest servletRequest
    ) {
        ProductResponse response = adminProductService.update(productId, request);
        adminActivityService.record(authentication == null ? "system" : authentication.getName(),
                "PRODUCT_UPDATED", "PRODUCT", String.valueOf(productId),
                "상품 수정: " + response.name(), servletRequest.getRemoteAddr());
        return response;
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable(name = "productId") Long productId,
            Authentication authentication, HttpServletRequest servletRequest) {
        adminProductService.delete(productId);
        adminActivityService.record(authentication == null ? "system" : authentication.getName(),
                "PRODUCT_DEACTIVATED", "PRODUCT", String.valueOf(productId),
                "상품 비활성화", servletRequest.getRemoteAddr());
    }
}
