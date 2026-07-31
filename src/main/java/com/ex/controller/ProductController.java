package com.ex.controller;

import com.ex.dto.ProductResponse;
import com.ex.entity.AnimalType;
import com.ex.service.ProductCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductCatalogService productCatalogService;

    @GetMapping
    public List<ProductResponse> findProducts(
            @RequestParam(name = "animalType", required = false) AnimalType animalType,
            @RequestParam(name = "query", required = false) String query
    ) {
        return productCatalogService.findProducts(animalType, query);
    }

    @GetMapping("/{productId}")
    public ProductResponse findProduct(
            @PathVariable(name = "productId") Long productId
    ) {
        return productCatalogService.findProduct(productId);
    }
}
