package com.feedflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사료 제조사 (공급업체) - 기준 정보.
 *
 * <h3>이 엔티티를 들여온 이유</h3>
 * B2C 담당 팀원의 {@code com.ex.entity.Manufacturer} 를 옮긴 것이다.
 * 우리 쪽 {@link Product} 에는 제조사가 없었는데, <b>불량 처리에서 반드시 필요하다.</b>
 * {@link DefectResolution#SUPPLIER_RETURN}(공급업체 반품)을 하려면 어디로 보낼지
 * 알아야 한다.
 * <p>
 * 옮기면서 컬럼명을 카멜 표기법으로 바꾸고({@code business_number} → {@code businessNumber}),
 * {@code BaseTimeEntity} 상속 대신 이 프로젝트의 다른 엔티티처럼
 * {@code createdAt} 을 직접 두었다. 상속으로 얻는 이득이 필드 하나뿐인데
 * 상속 계층이 하나 늘면 엔티티를 읽을 때 두 파일을 봐야 한다.
 *
 * <h3>품목과의 관계는 선택(nullable)이다</h3>
 * 이미 등록된 품목 13개에는 제조사 정보가 없다. FK 를 필수로 만들면 그 품목들을
 * 모두 손대야 하고, 실제로도 <b>제조사를 모르는 상태로 품목을 먼저 등록</b>하는 일이
 * 있다(샘플 입고, 자사 생산). 센터 좌표를 nullable 로 둔 것과 같은 판단이다.
 */
@Entity
@Table(
        name = "manufacturers",
        uniqueConstraints = @UniqueConstraint(name = "ukManufacturerName", columnNames = "name")
)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Manufacturer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "manufacturerId")
    private Long manufacturerId;

    /** 제조사명 (중복 불가). 같은 이름의 제조사를 두 번 등록하면 불량 통계가 갈라진다 */
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    /** 사업자등록번호. 반품 · 정산에 쓰이지만 확보 전에도 등록할 수 있어야 하므로 nullable */
    @Column(name = "businessNumber", length = 20)
    private String businessNumber;

    /** 불량 발생 시 연락할 번호 */
    @Column(name = "phone", length = 30)
    private String phone;

    /** 품질 담당자 */
    @Column(name = "contactName", length = 30)
    private String contactName;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;
}
