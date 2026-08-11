package com.ex.service;

import com.ex.dto.ProductResponse;
import com.ex.entity.AnimalType;
import com.ex.entity.Product;
import com.ex.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductCatalogService {

    private final ProductRepository productRepository;
    private final ExpirySaleService expirySaleService;

    public List<ProductResponse> findProducts(AnimalType animalType, String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        List<Product> products = productRepository
                .findAllByActiveTrueOrderByIdAsc()
                .stream()
                .filter(product -> product.getDisplayTone() != null
                        && !product.getDisplayTone().isBlank())
                .toList();
        Map<Long, ExpirySaleService.SaleOffer> saleOffers =
                expirySaleService.offersFor(products);

        return products.stream()
                .map(product -> ProductResponse.from(product)
                        .withExpirySale(saleOffers.get(
                                product.getProductId())))
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
                .toList();
    }

    public ProductResponse findProduct(Long productId) {
        Product product = productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        return ProductResponse.from(product)
                .withExpirySale(expirySaleService.offerFor(product)
                        .orElse(null));
    }
}
