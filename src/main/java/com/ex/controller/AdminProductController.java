package com.ex.controller;

import com.ex.dto.AdminProductRequest;
import com.ex.dto.ProductResponse;
import com.ex.service.AdminProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody AdminProductRequest request) {
        return adminProductService.create(request);
    }

    @PutMapping("/{productId}")
    public ProductResponse update(
            @PathVariable(name = "productId") Long productId,
            @Valid @RequestBody AdminProductRequest request
    ) {
        return adminProductService.update(productId, request);
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable(name = "productId") Long productId) {
        adminProductService.delete(productId);
    }
}
