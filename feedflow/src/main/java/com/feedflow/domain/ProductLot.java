package com.feedflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 상품 로트(생산 단위). 유통기한 관리의 기준이 된다.
 * <p>
 * 테이블/컬럼명은 카멜 표기법으로 선언한다.
 */
@Entity
@Table(name = "productLots")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lotId")
    private Long lotId;

    /** products.productId 참조 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productId", nullable = false)
    private Product product;

    @Column(name = "lotNo", nullable = false, length = 50)
    private String lotNo;

    /** 제조일자 */
    @Column(name = "manufacturedDate", nullable = false)
    private LocalDate manufacturedDate;

    /** 유통기한 */
    @Column(name = "expirationDate", nullable = false)
    private LocalDate expirationDate;

    /** 해당 로트의 잔여 수량 */
    @Column(name = "lotQuantity", nullable = false)
    private Integer lotQuantity;
}
