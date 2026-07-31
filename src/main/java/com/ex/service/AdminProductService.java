package com.ex.service;

import com.ex.dto.AdminProductRequest;
import com.ex.dto.ProductResponse;
import com.ex.entity.Manufacturer;
import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.repository.ManufacturerRepository;
import com.ex.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminProductService {

    private final ProductRepository productRepository;
    private final ManufacturerRepository manufacturerRepository;

    @Transactional
    public ProductResponse create(AdminProductRequest request) {
        Manufacturer manufacturer = findOrCreateManufacturer(request.manufacturerName());
        Product product = Product.builder()
                .manufacturer(manufacturer)
                .active(true)
                .build();
        applyProduct(product, request);
        product.getLots().add(createLot(product, request));
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long productId, AdminProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        product.setManufacturer(findOrCreateManufacturer(request.manufacturerName()));
        applyProduct(product, request);

        ProductLot lot = product.getLots().stream().findFirst()
                .orElseGet(() -> {
                    ProductLot newLot = createLot(product, request);
                    product.getLots().add(newLot);
                    return newLot;
                });
        lot.setLotNumber(request.lotNumber());
        lot.setManufacturedDate(request.manufacturedDate());
        lot.setExpirationDate(request.expirationDate());
        lot.setQuantity(request.lotQuantity());
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        product.setActive(false);
    }

    private Manufacturer findOrCreateManufacturer(String name) {
        return manufacturerRepository.findByName(name)
                .orElseGet(() -> manufacturerRepository.save(Manufacturer.builder()
                        .name(name)
                        .build()));
    }

    private void applyProduct(Product product, AdminProductRequest request) {
        product.setName(request.name());
        product.setAnimalType(request.animalType());
        product.setFeedStage(request.feedStage());
        product.setDescription(request.description());
        product.setWeightKg(request.weightKg());
        product.setPrice(request.price());
        product.setOriginalPrice(request.originalPrice());
        product.setProteinPercent(request.proteinPercent());
        product.setFatPercent(request.fatPercent());
        product.setFiberPercent(request.fiberPercent());
        product.setCalciumPercent(request.calciumPercent());
        product.setImageUrl(request.imageUrl());
        product.setBadge(request.badge());
        product.setDisplayTone(request.displayTone());
        product.setDisplayShape(request.displayShape());
        product.setActive(true);
    }

    private ProductLot createLot(Product product, AdminProductRequest request) {
        return ProductLot.builder()
                .product(product)
                .lotNumber(request.lotNumber())
                .manufacturedDate(request.manufacturedDate())
                .expirationDate(request.expirationDate())
                .quantity(request.lotQuantity())
                .build();
    }
}
