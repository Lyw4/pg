package com.ex.service;

import com.ex.dto.AdminProductRequest;
import com.ex.dto.ProductResponse;
import com.ex.entity.Manufacturer;
import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.repository.ManufacturerRepository;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AdminProductService {

    private final ProductRepository productRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final ProductLotRepository productLotRepository;

    @Transactional
    public ProductResponse create(AdminProductRequest request) {
        Manufacturer manufacturer = findOrCreateManufacturer(request.manufacturerName());
        Product product = new Product(
                manufacturer,
                request.name(),
                categoryLabel(request),
                request.weightKg(),
                BigDecimal.valueOf(request.price()),
                0,
                shelfLifeMonths(request),
                request.description());
        applyProduct(product, manufacturer, request);
        productRepository.save(product);

        ProductLot lot = createLot(product, request);
        productLotRepository.save(lot);
        product.addLot(lot);
        product.changeStock(request.lotQuantity());
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse update(Long productId, AdminProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        Manufacturer manufacturer =
                findOrCreateManufacturer(request.manufacturerName());
        applyProduct(product, manufacturer, request);

        ProductLot lot = productLotRepository
                .findByProductProductIdOrderByExpirationDateAsc(productId)
                .stream()
                .findFirst()
                .orElse(null);
        if (lot == null) {
            lot = createLot(product, request);
            productLotRepository.save(lot);
            product.addLot(lot);
            product.changeStock(request.lotQuantity());
        } else {
            int difference =
                    request.lotQuantity() - lot.getLotQuantity();
            lot.updateDetails(
                    request.lotNumber(),
                    request.manufacturedDate(),
                    request.expirationDate(),
                    request.lotQuantity());
            product.changeStock(difference);
        }
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        product.deactivate();
    }

    private Manufacturer findOrCreateManufacturer(String name) {
        return manufacturerRepository.findByCompanyName(name)
                .orElseGet(() -> manufacturerRepository.save(
                        new Manufacturer(name, "고객몰 등록", "")));
    }

    private void applyProduct(
            Product product,
            Manufacturer manufacturer,
            AdminProductRequest request) {
        product.updateForStorefront(
                manufacturer,
                request.name(),
                categoryLabel(request),
                request.feedStage(),
                request.description(),
                request.weightKg(),
                BigDecimal.valueOf(request.price()),
                request.originalPrice(),
                request.proteinPercent(),
                request.fatPercent(),
                request.fiberPercent(),
                request.calciumPercent(),
                request.imageUrl(),
                request.badge(),
                request.displayTone(),
                request.displayShape());
        product.configureShelfLife(shelfLifeMonths(request));
        product.activate();
    }

    private ProductLot createLot(Product product, AdminProductRequest request) {
        return new ProductLot(
                product,
                request.lotNumber(),
                request.manufacturedDate(),
                request.expirationDate(),
                request.lotQuantity());
    }

    private int shelfLifeMonths(AdminProductRequest request) {
        long months = ChronoUnit.MONTHS.between(
                request.manufacturedDate(),
                request.expirationDate());
        return (int) Math.max(1, months);
    }

    private String categoryLabel(AdminProductRequest request) {
        return switch (request.animalType()) {
            case CATTLE, DAIRY_CATTLE -> "소";
            case PIG -> "돼지";
            case CHICKEN, DUCK -> "조류(닭/오리)";
            case PET -> "반려동물";
            case SUPPLEMENT -> "영양제";
        };
    }
}
