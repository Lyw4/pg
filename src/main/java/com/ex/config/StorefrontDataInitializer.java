package com.ex.config;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.ProductRepository;
import com.ex.service.WarehousePlanSeeder;
import com.ex.service.WmsStockCoordinator;

import lombok.RequiredArgsConstructor;

/**
 * 과거 판매 홈페이지에서만 사용하던 별도 샘플 상품을 정리합니다.
 *
 * <p>현재 판매·유통·재고는 {@link DataInitializer}의 40개 기준 상품을
 * 함께 사용합니다. 주문과 이력의 외래키를 보존하기 위해 과거 상품 행은
 * 물리 삭제하지 않고 판매 중지하며, 기준 상품과 이름이 겹쳤던 샘플 LOT만
 * 재고에서 안전하게 제외합니다.</p>
 */
@Component
@Order(200)
@RequiredArgsConstructor
public class StorefrontDataInitializer implements ApplicationRunner {

    private static final List<String> LEGACY_STOREFRONT_PRODUCTS = List.of(
            "한우 마스터 700",
            "데일리 밀크 플러스",
            "포크 밸런스 S",
            "레이어 골드",
            "덕 그로우 밸런스",
            "카프 스타트 케어",
            "스마트 소우 케어");

    private static final List<String> LEGACY_STOREFRONT_LOTS = List.of(
            "FF-HB-260721",
            "FF-DC-260718",
            "FF-PG-260724",
            "FF-CK-260716",
            "FF-DK-260720",
            "FF-CF-260722",
            "FF-MN-260715",
            "FF-SW-260723");

    private final ProductRepository productRepository;
    private final ProductLotRepository productLotRepository;
    private final WmsStockCoordinator wmsStockCoordinator;
    private final WarehousePlanSeeder warehousePlanSeeder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LEGACY_STOREFRONT_PRODUCTS.forEach(name ->
                productRepository.findByName(name).ifPresent(product -> {
                    retireProductLots(product);
                    if (product.isActive()) {
                        product.deactivate();
                    }
                }));

        /*
         * 미네랄 밸런스 플러스는 40개 기준 상품에도 포함되므로 상품은
         * 유지하되 과거 판매 샘플 LOT만 0으로 정리합니다.
         */
        productLotRepository.findByLotNo("FF-MN-260715")
                .ifPresent(this::retireLot);

        warehousePlanSeeder.ensureAllocationsForAllProducts();
        warehousePlanSeeder.fillMissingRecommendationsFromFarmDemand();
    }

    private void retireProductLots(Product product) {
        product.getLots().stream()
                .filter(lot -> LEGACY_STOREFRONT_LOTS.contains(lot.getLotNo()))
                .forEach(this::retireLot);
    }

    private void retireLot(ProductLot lot) {
        int quantity = lot.getLotQuantity();
        if (quantity <= 0) {
            return;
        }
        lot.changeQuantity(-quantity);
        lot.getProduct().changeStock(-quantity);
        wmsStockCoordinator.adjust(
                lot,
                -quantity,
                null,
                "기존 판매 전용 샘플 LOT 정리",
                "SYSTEM");
    }
}
