package com.ex.service;

import com.ex.dto.ProductResponse;
import com.ex.entity.AnimalType;
import com.ex.entity.Product;
import com.ex.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductCatalogService {

    private final ProductRepository productRepository;

    public List<ProductResponse> findProducts(AnimalType animalType, String query) {
        List<Product> products = animalType == null
                ? productRepository.findAllByActiveTrueOrderByIdAsc()
                : productRepository.findAllByActiveTrueAndAnimalTypeOrderByIdAsc(animalType);

        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        return products.stream()
                .filter(product -> normalizedQuery.isEmpty()
                        || product.getName().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || product.getDescription().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || product.getFeedStage().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .map(ProductResponse::from)
                .toList();
    }

    public ProductResponse findProduct(Long productId) {
        Product product = productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        return ProductResponse.from(product);
    }
}
