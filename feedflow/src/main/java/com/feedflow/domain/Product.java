package com.feedflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배합사료 상품.
 * imageUrl / description 은 B2C 쇼핑몰 연동용 컬럼이므로 DB 에는 유지하되,
 * 관리자 화면(Thymeleaf)에서는 DTO 로 내려주지 않는다.
 */
@Entity
@Table(name = "products")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false, length = 200)
    private String name;

    /** 축종 (소, 돼지, 닭 ...) */
    @Column(name = "animal_type", nullable = false, length = 50)
    private String animalType;

    /** 포장 단위 무게(kg) */
    @Column(name = "weight_kg", nullable = false)
    private Integer weightKg;

    /** 판매 단가(원) */
    @Column(nullable = false)
    private Long price;

    /** 전체 재고 수량 */
    @Column(name = "total_stock", nullable = false)
    private Integer totalStock;

    /** 안전 재고 기준 수량 */
    @Column(name = "safety_stock", nullable = false)
    private Integer safetyStock;

    /** B2C 쇼핑몰 전용 - 관리자 화면 렌더링 제외 */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** B2C 쇼핑몰 전용 - 관리자 화면 렌더링 제외 */
    @Column(length = 2000)
    private String description;

    /** 안전 재고 미달 여부 */
    public boolean isBelowSafetyStock() {
        return totalStock < safetyStock;
    }

    /** 안전 재고까지 부족한 수량 */
    public int shortageQuantity() {
        return Math.max(safetyStock - totalStock, 0);
    }
}
