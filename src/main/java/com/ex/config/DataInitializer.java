package com.ex.config;

import com.ex.entity.*;
import com.ex.repository.ManufacturerRepository;
import com.ex.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ManufacturerRepository manufacturerRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (productRepository.count() > 0) {
            return;
        }

        Manufacturer manufacturer = manufacturerRepository.save(Manufacturer.builder()
                .name("피드플로우 천안공장")
                .businessNumber("123-45-67890")
                .phone("1588-0427")
                .build());

        productRepository.saveAll(List.of(
                createProduct(manufacturer, "한우 마스터 700", AnimalType.CATTLE, "비육후기",
                        "육질과 증체 균형을 위한 고에너지 배합", "25", 33_800, 36_500,
                        "13", "4.2", "8.0", "0.8", "amber", "베스트", "700",
                        "FF-HB-260721", 84),
                createProduct(manufacturer, "데일리 밀크 플러스", AnimalType.DAIRY_CATTLE, "착유기",
                        "산유량과 반추위 건강을 함께 설계한 균형 사료", "25", 35_200, null,
                        "18", "4.5", "7.5", "0.9", "blue", "신상품", "MILK+",
                        "FF-DC-260718", 46),
                createProduct(manufacturer, "포크 밸런스 S", AnimalType.PIG, "육성돈",
                        "고른 성장과 사료 효율을 위한 프리미엄 포뮬러", "25", 29_700, 31_200,
                        "17", "5.0", "6.5", "0.75", "coral", "묶음할인", "S",
                        "FF-PG-260724", 128),
                createProduct(manufacturer, "레이어 골드", AnimalType.CHICKEN, "산란계",
                        "난각 품질과 산란 지속성을 고려한 영양 설계", "20", 26_400, null,
                        "16", "3.8", "5.5", "3.8", "gold", null, "GOLD",
                        "FF-CK-260716", 63),
                createProduct(manufacturer, "덕 그로우 밸런스", AnimalType.DUCK, "육성오리",
                        "오리의 균일한 성장과 소화율을 고려한 배합", "20", 27_100, null,
                        "18", "4.0", "6.0", "0.9", "lime", "추천", "DUCK",
                        "FF-DK-260720", 58),
                createProduct(manufacturer, "카프 스타트 케어", AnimalType.CATTLE, "어린송아지",
                        "초기 성장과 면역 균형을 돕는 기호성 배합", "20", 38_900, null,
                        "20", "5.2", "5.0", "1.0", "mint", "추천", "START",
                        "FF-CF-260722", 31),
                createProduct(manufacturer, "미네랄 밸런스 플러스", AnimalType.SUPPLEMENT, "전 축종",
                        "농장 가축의 무기질 균형을 위한 복합 영양제", "10", 42_900, null,
                        "8", "2.0", "4.0", "12.0", "navy", "정기배송", "MIN+",
                        "FF-MN-260715", 22),
                createProduct(manufacturer, "스마트 소우 케어", AnimalType.PIG, "임신돈",
                        "모돈 컨디션과 번식 성적을 위한 맞춤 영양", "25", 31_600, null,
                        "15", "4.0", "7.0", "0.9", "rose", null, "CARE",
                        "FF-SW-260723", 54)
        ));
    }

    private Product createProduct(
            Manufacturer manufacturer,
            String name,
            AnimalType animalType,
            String feedStage,
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
            String lotNumber,
            int quantity
    ) {
        Product product = Product.builder()
                .manufacturer(manufacturer)
                .name(name)
                .animalType(animalType)
                .feedStage(feedStage)
                .description(description)
                .weightKg(decimal(weightKg))
                .price(price)
                .originalPrice(originalPrice)
                .proteinPercent(decimal(protein))
                .fatPercent(decimal(fat))
                .fiberPercent(decimal(fiber))
                .calciumPercent(decimal(calcium))
                .displayTone(tone)
                .badge(badge)
                .displayShape(shape)
                .active(true)
                .build();

        ProductLot lot = ProductLot.builder()
                .product(product)
                .lotNumber(lotNumber)
                .manufacturedDate(LocalDate.now().minusDays(7))
                .expirationDate(LocalDate.now().plusMonths(6))
                .quantity(quantity)
                .build();
        product.getLots().add(lot);
        return product;
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
