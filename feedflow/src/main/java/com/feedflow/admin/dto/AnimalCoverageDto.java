package com.feedflow.admin.dto;

import com.feedflow.domain.AnimalType;
import com.feedflow.domain.CoverageStatus;
import lombok.Getter;

/**
 * 센터 하나의 축종 하나에 대한 수요 · 공급 대조.
 *
 * <h3>이 DTO 가 성립하는 이유</h3>
 * 농장의 축종({@code FarmCustomer.animalType})과 품목의 축종
 * ({@code Product.animalType})이 <b>같은 {@link AnimalType} enum</b> 이기 때문이다.
 * 팀원 모듈의 자유 문자열({@code "조류(닭/오리)"})을 그대로 뒀다면 이 비교 자체가
 * 불가능했다. 축종을 enum 으로 통일한 결정이 화면에서 값을 만드는 지점이다.
 *
 * <h3>수요와 공급의 기준</h3>
 * <ul>
 *     <li><b>수요</b> — 거래 중인 담당 농장의 월 예상 사료량 합계.
 *         거래 보류 농장은 제외한다(그 물량은 이번 달에 나가지 않는다).</li>
 *     <li><b>공급</b> — <b>출고 가능한</b> 재고만. 검수 전(입고 대기) 재고와
 *         운송 중 재고는 이 수요를 채울 수 없으므로 세지 않는다.</li>
 * </ul>
 */
@Getter
public class AnimalCoverageDto {

    private final AnimalType animalType;

    /** 월 예상 사료량 (포대) */
    private final int demandQuantity;

    /** 출고 가능 재고 (포대) */
    private final int supplyQuantity;

    /** 수요 대비 재고 비율 (%). 수요가 0 이면 0 */
    private final int coverageRate;

    private final CoverageStatus status;

    private AnimalCoverageDto(AnimalType animalType, int demandQuantity, int supplyQuantity) {
        this.animalType = animalType;
        this.demandQuantity = demandQuantity;
        this.supplyQuantity = supplyQuantity;
        this.coverageRate = rate(demandQuantity, supplyQuantity);
        this.status = CoverageStatus.of(demandQuantity, this.coverageRate);
    }

    public static AnimalCoverageDto of(AnimalType animalType, int demandQuantity, int supplyQuantity) {
        return new AnimalCoverageDto(animalType, demandQuantity, supplyQuantity);
    }

    /**
     * 수요 대비 비율.
     * <p>
     * 분모가 0 이면 0 을 반환한다. 이 값은 {@link CoverageStatus#NO_DEMAND} 와 함께
     * 쓰이므로 화면에서 비율을 표시하지 않는다. 분모 0 을 100% 로 채우면
     * '적정' 으로 보여 재고가 왜 거기 있는지 묻지 않게 된다.
     */
    private static int rate(int demandQuantity, int supplyQuantity) {
        if (demandQuantity <= 0) {
            return 0;
        }
        return (int) Math.round(supplyQuantity * 100.0 / demandQuantity);
    }

    /** 화면 표기용 축종 라벨 */
    public String getAnimalTypeDescription() {
        return animalType == null ? "-" : animalType.getDescription();
    }

    public String getStatusDescription() {
        return status.getDescription();
    }

    public String getStatusBadgeClass() {
        return status.getBadgeClass();
    }

    public boolean isNeedsAction() {
        return status.needsAction();
    }

    public boolean isHasDemand() {
        return demandQuantity > 0;
    }

    /**
     * 이번 달 수요를 채우려면 더 필요한 수량 (포대).
     * <p>
     * 충족되어 있으면 0 이다. 이 값이 발주량 판단의 근거가 된다.
     */
    public int getShortageQuantity() {
        return Math.max(0, demandQuantity - supplyQuantity);
    }

    /**
     * 진행 막대에 쓸 너비 (%).
     * <p>
     * 비율이 300% 를 넘어도 막대는 100% 에서 멈춘다. 그러지 않으면 과다 재고인
     * 센터의 막대가 화면을 넘어가고, 옆 센터와 길이를 비교할 수 없게 된다.
     */
    public int getBarWidth() {
        return Math.min(100, coverageRate);
    }
}
