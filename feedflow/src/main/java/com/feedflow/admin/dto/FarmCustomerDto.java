package com.feedflow.admin.dto;

import com.feedflow.domain.AnimalType;
import com.feedflow.domain.CustomerStatus;
import com.feedflow.domain.FarmCustomer;
import lombok.Builder;
import lombok.Getter;

/**
 * 농장 고객사 목록 행.
 * <p>
 * 엔티티를 화면으로 직접 넘기지 않는다. 특히 {@code FarmCustomer.center} 는 지연 로딩이라
 * 템플릿에서 건드리면 렌더링 중에 쿼리가 나가고, 영속성 컨텍스트가 이미 닫혀 있으면
 * 예외가 된다. 필요한 값(센터명)은 여기서 미리 꺼내 담는다.
 */
@Getter
@Builder
public class FarmCustomerDto {

    private final Long farmCustomerId;
    private final String farmCode;
    private final String farmName;
    private final String representativeName;
    private final String phone;
    private final String postalCode;
    private final String address;

    private final AnimalType animalType;
    private final int livestockCount;
    private final int monthlyFeedQuantity;
    private final String preferredFeed;
    private final int recurringDeliveryDay;

    private final Long centerId;
    private final String centerName;
    private final double distanceKm;

    private final CustomerStatus status;
    private final String notes;

    /**
     * 지도 표시 가능 여부 (위·경도가 둘 다 있는지).
     * <p>
     * <b>좌표 값 자체는 담지 않는다.</b> 이 화면은 목록이고 지도를 그리지 않으므로
     * 표시할 곳이 없다. 화면이 필요한 것은 "몇 곳이 좌표가 없는지" 뿐이다.
     * 농장 핀을 지도에 찍는 기능(P4b)을 만들 때 그 화면 전용 DTO 에 좌표를 담는다.
     */
    private final boolean mappable;

    public static FarmCustomerDto of(FarmCustomer farm) {
        return FarmCustomerDto.builder()
                .farmCustomerId(farm.getFarmCustomerId())
                .farmCode(farm.getFarmCode())
                .farmName(farm.getFarmName())
                .representativeName(farm.getRepresentativeName())
                .phone(farm.getPhone())
                .postalCode(farm.getPostalCode())
                .address(farm.getAddress())
                .animalType(farm.getAnimalType())
                .livestockCount(farm.getLivestockCount())
                .monthlyFeedQuantity(farm.getMonthlyFeedQuantity())
                .preferredFeed(farm.getPreferredFeed())
                .recurringDeliveryDay(farm.getRecurringDeliveryDay())
                .centerId(farm.centerId())
                .centerName(farm.centerName())
                .distanceKm(farm.getDistanceKm())
                .status(farm.getStatus())
                .notes(farm.getNotes())
                .mappable(farm.hasLocation())
                .build();
    }

    /* ------------------------------------------------------------------
     * 화면 표기용
     * ------------------------------------------------------------------ */

    public String getAnimalTypeDescription() {
        return animalType == null ? "-" : animalType.getDescription();
    }

    public String getStatusDescription() {
        return status == null ? "-" : status.getDescription();
    }

    /** Bootstrap 뱃지 클래스 (거래 중: bg-primary / 거래 보류: bg-secondary) */
    public String getStatusBadgeClass() {
        return status == null ? "bg-light text-dark" : status.getBadgeClass();
    }

    public boolean isTrading() {
        return status == CustomerStatus.ACTIVE;
    }

    /** 상태 변경 버튼이 가리킬 반대 상태 (화면이 분기하지 않게 enum 이 답을 준다) */
    public CustomerStatus getToggledStatus() {
        return status == null ? CustomerStatus.ACTIVE : status.toggled();
    }

    public String getToggledStatusDescription() {
        return getToggledStatus().getDescription();
    }

    /** 예: 약 8.2km */
    public String getDistanceLabel() {
        return "약 %.1fkm".formatted(distanceKm);
    }

    /** 예: 매월 15일 */
    public String getRecurringDeliveryLabel() {
        return "매월 " + recurringDeliveryDay + "일";
    }
}
