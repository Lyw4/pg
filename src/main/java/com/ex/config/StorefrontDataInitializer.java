package com.ex.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.Manufacturer;
import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.repository.ManufacturerRepository;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class StorefrontDataInitializer implements ApplicationRunner {

    private record StorefrontSeed(
            String name,
            String category,
            String animalType,
            String stage,
            String description,
            String weightKg,
            int price,
            Integer originalPrice,
            String protein,
            String fat,
            String fiber,
            String calcium,
            String tone,
            String badge,
            String shape,
            String lotNo,
            int quantity) {
    }

    private static final List<StorefrontSeed> SEEDS = List.of(
            seed("한우 마스터 700", "소", "CATTLE", "비육후기",
                    "육질과 증체 균형을 위한 고에너지 배합",
                    "25", 33_800, 36_500, "13", "4.2", "8.0",
                    "0.8", "amber", "베스트", "700",
                    "FF-HB-260721", 84),
            seed("데일리 밀크 플러스", "소", "DAIRY_CATTLE", "착유기",
                    "산유량과 반추위 건강을 함께 설계한 균형 사료",
                    "25", 35_200, null, "18", "4.5", "7.5",
                    "0.9", "blue", "신상품", "MILK+",
                    "FF-DC-260718", 46),
            seed("포크 밸런스 S", "돼지", "PIG", "육성돈",
                    "고른 성장과 사료 효율을 위한 프리미엄 포뮬러",
                    "25", 29_700, 31_200, "17", "5.0", "6.5",
                    "0.75", "coral", "묶음할인", "S",
                    "FF-PG-260724", 128),
            seed("레이어 골드", "조류(닭/오리)", "CHICKEN", "산란계",
                    "난각 품질과 산란 지속성을 고려한 영양 설계",
                    "20", 26_400, null, "16", "3.8", "5.5",
                    "3.8", "gold", null, "GOLD",
                    "FF-CK-260716", 63),
            seed("덕 그로우 밸런스", "조류(닭/오리)", "DUCK", "육성오리",
                    "오리의 균일한 성장과 소화율을 고려한 배합",
                    "20", 27_100, null, "18", "4.0", "6.0",
                    "0.9", "lime", "추천", "DUCK",
                    "FF-DK-260720", 58),
            seed("카프 스타트 케어", "소", "CATTLE", "어린송아지",
                    "초기 성장과 면역 균형을 돕는 기호성 배합",
                    "20", 38_900, null, "20", "5.2", "5.0",
                    "1.0", "mint", "추천", "START",
                    "FF-CF-260722", 31),
            seed("미네랄 밸런스 플러스", "영양제", "SUPPLEMENT", "전 축종",
                    "농장 가축의 무기질 균형을 위한 복합 영양제",
                    "10", 42_900, null, "8", "2.0", "4.0",
                    "12.0", "navy", "정기배송", "MIN+",
                    "FF-MN-260715", 22),
            seed("스마트 소우 케어", "돼지", "PIG", "임신돈",
                    "모돈 컨디션과 번식 성적을 위한 맞춤 영양",
                    "25", 31_600, null, "15", "4.0", "7.0",
                    "0.9", "rose", null, "CARE",
                    "FF-SW-260723", 54));

    private final ManufacturerRepository manufacturerRepository;
    private final ProductRepository productRepository;
    private final ProductLotRepository productLotRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Manufacturer manufacturer = manufacturerRepository
                .findByCompanyName("피드플로우 천안공장")
                .orElseGet(() -> manufacturerRepository.save(
                        new Manufacturer(
                                "피드플로우 천안공장",
                                "고객몰 담당",
                                "1588-0427")));

        SEEDS.forEach(seed -> {
            Product product = productRepository.findByName(seed.name())
                    .orElseGet(() -> productRepository.save(
                            new Product(
                                    manufacturer,
                                    seed.name(),
                                    seed.category(),
                                    decimal(seed.weightKg()),
                                    BigDecimal.valueOf(seed.price()),
                                    5,
                                    6,
                                    seed.description())));
            product.updateForStorefront(
                    manufacturer,
                    seed.name(),
                    seed.category(),
                    seed.stage(),
                    seed.description(),
                    decimal(seed.weightKg()),
                    BigDecimal.valueOf(seed.price()),
                    seed.originalPrice(),
                    decimal(seed.protein()),
                    decimal(seed.fat()),
                    decimal(seed.fiber()),
                    decimal(seed.calcium()),
                    null,
                    seed.badge(),
                    seed.tone(),
                    seed.shape());
            product.configureShelfLife(6);
            product.activate();

            if (!productLotRepository.existsByLotNo(seed.lotNo())) {
                ProductLot lot = new ProductLot(
                        product,
                        seed.lotNo(),
                        LocalDate.now().minusDays(7),
                        LocalDate.now().plusMonths(6),
                        seed.quantity());
                productLotRepository.save(lot);
                product.addLot(lot);
                product.changeStock(seed.quantity());
            }
        });
    }

    private static StorefrontSeed seed(
            String name,
            String category,
            String animalType,
            String stage,
            String description,
            String weightKg,
            int price,
            Integer originalPrice,
            String protein,
            String fat,
            String fiber,
            String calcium,
            String tone,
            String badge,
            String shape,
            String lotNo,
            int quantity) {
        return new StorefrontSeed(
                name,
                category,
                animalType,
                stage,
                description,
                weightKg,
                price,
                originalPrice,
                protein,
                fat,
                fiber,
                calcium,
                tone,
                badge,
                shape,
                lotNo,
                quantity);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
