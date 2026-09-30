package com.feedflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 배합사료 상품(품목) - 기준 정보(Master Data).
 * <p>
 * imageUrl / description 은 B2C 쇼핑몰 연동용 컬럼이므로 DB 에는 유지하되,
 * 관리자 화면(Thymeleaf)에서는 DTO 로 내려주지 않고 폼에서도 다루지 않는다.
 * <p>
 * 테이블/컬럼명은 카멜 표기법으로 선언한다.
 */
@Entity
@Table(name = "products")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    /** 유통기한 일수 기본값 (품목 등록 시 미입력 대비) */
    public static final int DEFAULT_SHELF_LIFE_DAYS = 180;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "productId")
    private Long productId;

    /** 품목 코드 (업무 식별자, 중복 불가) */
    @Column(name = "productCode", nullable = false, unique = true, length = 30)
    private String productCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * 제조사 (공급업체).
     * <p>
     * <b>선택(nullable)이다.</b> 이미 등록된 품목에는 제조사 정보가 없고, 실제로도
     * 제조사를 모르는 상태로 품목을 먼저 등록하는 일이 있다(샘플 입고, 자사 생산).
     * 필수로 만들면 기존 품목을 모두 손대야 한다.
     * <p>
     * 불량 처리에서 {@link DefectResolution#SUPPLIER_RETURN}(공급업체 반품)을 할 때
     * 이 값이 필요하다. 없으면 반품 대상을 알 수 없으므로 화면에서 안내한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturerId")
    private Manufacturer manufacturer;

    /** 축종 (소 / 돼지 / 조류) - 취급 범위를 고정하기 위해 enum 으로 관리 */
    @Enumerated(EnumType.STRING)
    @Column(name = "animalType", nullable = false, length = 20)
    private AnimalType animalType;

    /** 품목 구분 (사료 / 영양제) - 이 둘만 취급한다 */
    @Enumerated(EnumType.STRING)
    @Column(name = "productType", nullable = false, length = 20)
    private ProductType productType;

    /** 포장 단위 무게(kg) */
    @Column(name = "weightKg", nullable = false)
    private Integer weightKg;

    /** 판매 단가(원) */
    @Column(name = "price", nullable = false)
    private Long price;

    /** 전체 재고 수량 (입·출고 트랜잭션으로만 변경) */
    @Column(name = "totalStock", nullable = false)
    private Integer totalStock;

    /** 안전 재고 기준 수량 */
    @Column(name = "safetyStock", nullable = false)
    private Integer safetyStock;

    /** 유통기한 일수 (제조일자 + 이 일수 = 유통기한) */
    @Column(name = "shelfLifeDays", nullable = false)
    private Integer shelfLifeDays;

    /** 사용 여부 (false = 사용 중지. 이력 보존을 위해 물리 삭제하지 않는다) */
    @Column(name = "active", nullable = false)
    private boolean active;


    /**
     * 낙관적 락(Optimistic Lock) 버전.
     * <p>
     * 재고(totalStock) 는 입고 / 출고 / 폐기가 동시에 일어날 수 있으므로
     * 두 트랜잭션이 같은 행을 수정하면 나중 커밋이 실패하도록 한다.
     * (실패 시 ObjectOptimisticLockingFailureException 이 발생하고 전체가 롤백된다)
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** B2C 쇼핑몰 전용 - 관리자 화면 렌더링 제외 */
    @Column(name = "imageUrl", length = 500)
    private String imageUrl;

    /** B2C 쇼핑몰 전용 - 관리자 화면 렌더링 제외 */
    @Column(name = "description", length = 2000)
    private String description;

    @PrePersist
    void prePersist() {
        if (productType == null) {
            productType = ProductType.FEED;
        }
        if (totalStock == null) {
            totalStock = 0;
        }
        if (safetyStock == null) {
            safetyStock = 0;
        }
        if (shelfLifeDays == null) {
            shelfLifeDays = DEFAULT_SHELF_LIFE_DAYS;
        }
    }

    /**
     * 기준 정보 수정.
     * 재고(totalStock)는 입·출고 트랜잭션으로만 변경되므로 여기서 다루지 않는다.
     */
    public void updateMasterData(String productCode,
                                 String name,
                                 AnimalType animalType,
                                 ProductType productType,
                                 Integer weightKg,
                                 Long price,
                                 Integer safetyStock,
                                 Integer shelfLifeDays) {
        this.productCode = productCode;
        this.name = name;
        this.animalType = animalType;
        this.productType = productType;
        this.weightKg = weightKg;
        this.price = price;
        this.safetyStock = safetyStock;
        this.shelfLifeDays = shelfLifeDays;
    }

    /** 사용 여부 변경 (사용 중지 / 재사용) */
    public void changeActive(boolean active) {
        this.active = active;
    }

    /**
     * 재고 정합성 보정.
     * <p>
     * totalStock 은 조회 성능을 위해 비정규화한 값이므로,
     * 로트 수량 합계와 어긋났을 때 계산값으로 강제 동기화한다.
     * 입·출고 경로가 아니라 <b>정합성 재계산 전용</b>이다.
     *
     * @param calculatedStock 로트 수량 합계
     * @return 실제로 값이 바뀌었으면 true
     */
    public boolean syncTotalStock(int calculatedStock) {
        if (calculatedStock < 0) {
            throw new IllegalArgumentException("재고 합계는 0 이상이어야 합니다. 값=" + calculatedStock);
        }
        int current = totalStock == null ? 0 : totalStock;
        if (current == calculatedStock) {
            return false;
        }
        this.totalStock = calculatedStock;
        return true;
    }

    /* ------------------------------------------------------------------
     * 재고 / 유통기한 도메인 로직
     * ------------------------------------------------------------------ */

    /**
     * 제조일자로부터 유통기한을 계산한다.
     * 품목마다 유통기한 일수가 다르므로 계산 책임을 Product 가 가진다.
     *
     * @param manufacturedDate 제조일자
     * @return 제조일자 + shelfLifeDays
     */
    public LocalDate calculateExpirationDate(LocalDate manufacturedDate) {
        if (manufacturedDate == null) {
            throw new IllegalArgumentException("제조일자는 필수입니다.");
        }
        int days = shelfLifeDays == null ? DEFAULT_SHELF_LIFE_DAYS : shelfLifeDays;
        return manufacturedDate.plusDays(days);
    }

    /** 입고 등 재고 증가 */
    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("증가 수량은 1 이상이어야 합니다.");
        }
        this.totalStock = (totalStock == null ? 0 : totalStock) + quantity;
    }

    /** 출고 등 재고 감소 */
    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("감소 수량은 1 이상이어야 합니다.");
        }
        int current = totalStock == null ? 0 : totalStock;
        if (current < quantity) {
            throw new IllegalStateException(
                    "재고가 부족합니다. 현재 재고=" + current + ", 요청 수량=" + quantity);
        }
        this.totalStock = current - quantity;
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
