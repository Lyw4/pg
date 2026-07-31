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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 농장 고객사 - 기준 정보(Master Data).
 *
 * <h3>이 엔티티의 출처</h3>
 * B2C 담당 팀원이 만든 고객 농장 모듈을 WMS 로 옮긴 것이다. 원본은
 * {@code com.ex.entity.FarmCustomer} 이며, 옮기면서 이 프로젝트의 규칙에 맞췄다.
 * <ul>
 *     <li><b>담당 창고를 {@link Center} 로 참조한다</b> — 원본은 별도 {@code warehouse}
 *         테이블(W01~W05)을 참조했다. 같은 물류 거점이 두 테이블로 갈라지면 구역 · 재고 ·
 *         이력 · 이관 · 2D 도면이 모두 매달린 {@code centers} 와 어긋난다.</li>
 *     <li><b>축종을 {@link AnimalType} enum 으로 바꿨다</b> — 원본은
 *         {@code "조류(닭/오리)"} 같은 자유 문자열이었다. 그러면 "이 센터가 담당하는
 *         농장의 축종" 과 "이 센터가 보유한 사료의 축종"({@code Product.animalType})을
 *         맞춰볼 수 없다. 두 축이 같은 값 체계를 써야 비교가 성립한다.</li>
 *     <li><b>컬럼명을 카멜 표기법으로 바꿨다</b> — 원본은 {@code farm_code} 였다.
 *         이 프로젝트는 물리 네이밍 전략을 표준으로 고정해 선언한 이름을 그대로 쓴다.</li>
 *     <li><b>{@code demoData} 플래그를 제거했다</b> — 시드 데이터를 구분하는 용도였지만
 *         이 프로젝트는 {@code data.sql} 시드가 전부라 값이 항상 참이고 읽는 곳이 없다.</li>
 * </ul>
 *
 * <h3>{@code farmCode} 는 원본 체계를 유지한다</h3>
 * {@code F-W01-01} 의 {@code W01} 은 팀원 쪽 창고 코드다. 우리 코드 체계
 * ({@code C1-YS})와 다르지만 <b>바꾸지 않았다.</b> 팀원 모듈이 {@code farmCode} 를
 * 자연 키로 삼아 데이터를 병합하므로, 코드를 고치면 같은 농장이 두 건으로 늘어난다.
 * 센터를 가리키는 일은 코드 문자열이 아니라 {@link #center} 참조가 한다.
 *
 * <h3>{@code distanceKm} 을 컬럼으로 두는 이유</h3>
 * 좌표가 둘 다 있으므로 계산할 수 있는 파생값이다. 그런데도 저장한다.
 * <ul>
 *     <li>목록 화면이 거리순 정렬 · 필터를 하므로 <b>DB 가 알아야 하는 값</b>이다.
 *         자바에서 계산하면 정렬을 위해 전체를 읽어야 한다.</li>
 *     <li>센터 좌표가 바뀌면 다시 계산해야 하는데, 그 시점을 명시적으로 다룰 수 있다.
 *         조회할 때마다 계산하면 어느 좌표 기준인지 기록이 남지 않는다.</li>
 * </ul>
 * 현재 값은 <b>{@code centers} 의 좌표를 기준으로 다시 계산한 것</b>이다.
 * 원본 값은 팀원 쪽 창고 좌표 기준이어서 최대 17km 차이가 났다(나주).
 */
@Entity
@Table(
        name = "farmCustomers",
        uniqueConstraints = @UniqueConstraint(name = "ukFarmCode", columnNames = "farmCode")
)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FarmCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "farmCustomerId")
    private Long farmCustomerId;

    /**
     * 농장 코드 (업무 식별자, 중복 불가).
     * <p>
     * 팀원 모듈이 이 값을 자연 키로 삼아 데이터를 병합한다. 형식을 바꾸지 않는다.
     */
    @Column(name = "farmCode", nullable = false, unique = true, length = 20)
    private String farmCode;

    @Column(name = "farmName", nullable = false, length = 80)
    private String farmName;

    /** 대표자명 (배송 · 연락 담당) */
    @Column(name = "representativeName", nullable = false, length = 30)
    private String representativeName;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "postalCode", nullable = false, length = 10)
    private String postalCode;

    @Column(name = "address", nullable = false, length = 180)
    private String address;

    /**
     * 농장 위도 (WGS84).
     * <p>
     * {@link Center#getLatitude()} 와 함께 배송 권역 판단의 근거가 된다.
     * 좌표를 모르는 농장도 등록할 수 있어야 하므로 nullable 이다
     * (센터 좌표와 같은 이유 — 계약을 먼저 하고 위치를 뒤에 확인하는 일이 있다).
     */
    @Column(name = "latitude")
    private Double latitude;

    /** 농장 경도 (WGS84) */
    @Column(name = "longitude")
    private Double longitude;

    /**
     * 사육 축종.
     * <p>
     * {@code Product.animalType} 과 <b>같은 enum 을 쓴다.</b> 그래야
     * "이 센터가 담당하는 농장의 축종" 과 "이 센터가 보유한 사료의 축종" 을
     * 맞춰볼 수 있다. 나주 센터가 가금 전용인데 담당 농장에 한우가 있으면
     * 배정이 잘못된 것이고, 그 판단이 이 컬럼으로 가능해진다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "animalType", nullable = false, length = 20)
    private AnimalType animalType;

    /** 사육 두수 (마리) */
    @Column(name = "livestockCount", nullable = false)
    private int livestockCount;

    /**
     * 월 예상 사료량 (포대).
     * <p>
     * 센터별 수요를 가늠하는 값이다. 거래 보류 중인 농장은 이 값을 합산에서 제외한다
     * ({@link CustomerStatus} 참고).
     */
    @Column(name = "monthlyFeedQuantity", nullable = false)
    private int monthlyFeedQuantity;

    /** 선호 사료 (품목 코드가 아니라 담당자가 적는 설명. 품목 확정 전 단계의 메모다) */
    @Column(name = "preferredFeed", nullable = false, length = 80)
    private String preferredFeed;

    /**
     * 정기 배송일 (매월 며칠).
     * <p>
     * <b>1~28 로 제한한다.</b> 29~31 을 허용하면 2월에 그 날짜가 없어 배송일이
     * 밀리거나 건너뛴다. 말일 배송이 필요하면 날짜가 아니라 '말일' 이라는 규칙으로
     * 표현해야 하므로 그때 별도 컬럼을 둔다.
     */
    @Column(name = "recurringDeliveryDay", nullable = false)
    private int recurringDeliveryDay;

    /**
     * 담당 물류센터.
     * <p>
     * {@code optional = false} — 담당 센터가 없는 농장은 배송 계획을 세울 수 없다.
     * 목록에서 센터명을 표시하므로 조회 시 {@code join fetch} 로 함께 읽는다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "centerId", nullable = false)
    private Center center;

    /**
     * 담당 센터까지의 거리 (km).
     * <p>
     * {@code centers} 의 좌표를 기준으로 계산한 값이다. 클래스 주석 참고.
     */
    @Column(name = "distanceKm", nullable = false)
    private double distanceKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CustomerStatus status;

    /** 운영 메모 (보류 사유 · 공급 주기 특이사항 등) */
    @Column(name = "notes", length = 200)
    private String notes;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /* ------------------------------------------------------------------
     * 도메인 로직
     * ------------------------------------------------------------------ */

    /**
     * 거래 상태 변경.
     *
     * @throws IllegalArgumentException 상태가 null 인 경우
     * @throws IllegalStateException    이미 같은 상태인 경우
     */
    public void changeStatus(CustomerStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("변경할 거래 상태를 선택해 주세요.");
        }
        if (this.status == newStatus) {
            throw new IllegalStateException("이미 " + newStatus.getDescription() + " 상태입니다.");
        }
        this.status = newStatus;
    }

    /** 거래 중인지 (월 사료량 합산 대상 판단) */
    public boolean isTrading() {
        return status == CustomerStatus.ACTIVE;
    }

    /**
     * 지도에 표시할 수 있는지.
     * <p>
     * 둘 중 하나만 있으면 찍을 수 없다. {@link Center#hasLocation()} 과 같은 규칙이다.
     */
    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    /** 담당 센터 ID (센터가 지정되지 않은 테스트 픽스처를 위해 null 을 허용한다) */
    public Long centerId() {
        return center == null ? null : center.getCenterId();
    }

    /** 담당 센터 표기명 */
    public String centerName() {
        return center == null ? null : center.displayName();
    }
}
