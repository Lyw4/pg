package com.feedflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배합사료 상품(품목) - 기준 정보(Master Data).
 * <p>
 * imageUrl / description 은 B2C 쇼핑몰 연동용 컬럼이므로 DB 에는 유지하되,
 * 관리자 화면(Thymeleaf)에서는 DTO 로 내려주지 않고 폼에서도 다루지 않는다.
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

    /** 품목 코드 (업무 식별자, 중복 불가) */
    @Column(name = "product_code", nullable = false, unique = true, length = 30)
    private String productCode;

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

    /** 전체 재고 수량 (입·출고로만 변경, 기준정보 수정에서는 변경하지 않음) */
    @Column(name = "total_stock", nullable = false)
    private Integer totalStock;

    /** 안전 재고 기준 수량 */
    @Column(name = "safety_stock", nullable = false)
    private Integer safetyStock;

    /** 사용 여부 (false = 사용 중지. 이력 보존을 위해 물리 삭제하지 않는다) */
    @Column(nullable = false)
    private boolean active;

    /** B2C 쇼핑몰 전용 - 관리자 화면 렌더링 제외 */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** B2C 쇼핑몰 전용 - 관리자 화면 렌더링 제외 */
    @Column(length = 2000)
    private String description;

    @PrePersist
    void prePersist() {
        if (totalStock == null) {
            totalStock = 0;
        }
        if (safetyStock == null) {
            safetyStock = 0;
        }
    }

    /**
     * 기준 정보 수정.
     * 재고(totalStock)는 입·출고 트랜잭션으로만 변경되므로 여기서 다루지 않는다.
     */
    public void updateMasterData(String productCode,
                                 String name,
                                 String animalType,
                                 Integer weightKg,
                                 Long price,
                                 Integer safetyStock) {
        this.productCode = productCode;
        this.name = name;
        this.animalType = animalType;
        this.weightKg = weightKg;
        this.price = price;
        this.safetyStock = safetyStock;
    }

    /** 사용 여부 변경 (사용 중지 / 재사용) */
    public void changeActive(boolean active) {
        this.active = active;
    }

    /** 안전 재고 미달 여부 */
    public boolean isBelowSafetyStock() {
        return totalStock < safetyStock;
    }

    /** 안전 재고까지 부족한 수량 */
    public int shortageQuantity() {
        return Math.max(safetyStock - totalStock, 0);
    }
}
