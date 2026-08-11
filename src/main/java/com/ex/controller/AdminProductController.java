package com.ex.controller;

import com.ex.dto.AdminProductRequest;
import com.ex.dto.ProductResponse;
import com.ex.service.AdminProductService;
import com.ex.service.AdminActivityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;
    private final AdminActivityService adminActivityService;

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
