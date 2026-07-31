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
import java.util.Comparator;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductCatalogService {

    private static final List<String> STOREFRONT_ORDER = List.of(
            "한우 마스터 700",
            "데일리 밀크 플러스",
            "포크 밸런스 S",
            "레이어 골드",
            "덕 그로우 밸런스",
            "카프 스타트 케어",
            "미네랄 밸런스 플러스",
            "스마트 소우 케어");

    private final ProductRepository productRepository;

    public List<ProductResponse> findProducts(AnimalType animalType, String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        return productRepository.findAllByActiveTrueOrderByIdAsc()
                .stream()
                .filter(product -> product.getDisplayTone() != null
                        && !product.getDisplayTone().isBlank())
                .map(ProductResponse::from)
                .filter(product -> animalType == null
                        || animalType.name().equals(product.animalType()))
                .filter(product -> {
                    String haystack = String.join(
                            " ",
                            product.name(),
                            product.animal(),
                            product.stage(),
                            product.description())
                            .toLowerCase(Locale.ROOT);
                    return normalizedQuery.isEmpty()
                            || haystack.contains(normalizedQuery);
                })
                .sorted(Comparator.comparingInt(product -> {
                    int index = STOREFRONT_ORDER.indexOf(product.name());
                    return index < 0 ? Integer.MAX_VALUE : index;
                }))
                .toList();
    }

    public ProductResponse findProduct(Long productId) {
        Product product = productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        return ProductResponse.from(product);
    }
}
