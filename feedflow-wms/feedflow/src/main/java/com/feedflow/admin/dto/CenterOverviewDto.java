package com.feedflow.admin.dto;

import com.feedflow.common.StockPolicy;
import com.feedflow.common.util.Numbers;
import com.feedflow.domain.BinLoadStatus;
import com.feedflow.domain.MovementType;
import lombok.Builder;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 전국 대시보드의 센터 카드 하나.
 * <p>
 * 재고 · 적재율 · 경보 · 기간 실적을 센터 단위로 묶는다. 화면이 여러 집계 결과를
 * 센터별로 다시 짝지어야 하는 일을 없애기 위해 서비스에서 조립해 내려준다.
 */
@Getter
@Builder
public class CenterOverviewDto {

    private final Long centerId;
    private final String centerCode;
    private final String centerName;
    private final String region;

    /** 운영 방향 (예: 닭 · 오리 최우선) */
    private final String note;

    /* ---------------- 재고 ---------------- */

    /**
     * 이 센터에 있는 재고 수량 <b>전체</b>.
     * <p>
     * 보관 구역뿐 아니라 입고 · 출고 대기 구역, 운송 중 가상 구역까지 포함한다.
     * 분포와 비중을 낼 때는 이 값을 써야 전국 합계가 맞는다.
     */
    private final int quantity;

    /**
     * 그중 <b>보관 구역</b>에 있는 수량 — 적재율의 분자.
     * <p>
     * {@link #quantity} 를 분자로 쓰면 대기 구역 물건까지 보관 공간을 차지한 것으로
     * 계산되어 적재율이 부풀려지고 100% 를 넘길 수도 있다. 분모인 {@link #capacity}
     * 가 보관 구역만 세기 때문이다. 2D 도면도 같은 기준으로 대기분을 따로 표시한다.
     */
    private final int storageQuantity;

    /** 전국 합계 대비 비중 (%) */
    private final int sharePercent;

    /** 보관 구역의 수용량 합계 (사용 중지 · 대기 · 운송 중 제외) */
    private final int capacity;

    /** 재고 행 수 (로트 × 구역) */
    private final int rowCount;

    /* ---------------- 경보 ---------------- */

    /** 유통기한 임박 재고 행 수 (경과분 포함) */
    private final int expiringCount;

    /** 이미 경과한 재고 행 수 */
    private final int expiredCount;

    /* ---------------- 기간 실적 ---------------- */

    /**
     * 유형별 수량 합계. 유형이 없으면 키 자체가 없다.
     * <p>
     * {@code Map} 으로 둔 이유 — 유형이 추가될 때마다 필드를 늘리지 않아도 된다.
     * P3a 에서 {@code TRANSFER_OUT}/{@code TRANSFER_IN} 두 개가 늘었고, 앞으로도 늘 수 있다.
     */
    private final Map<MovementType, Integer> activity;

    /* ------------------------------------------------------------------
     * 화면 표기
     * ------------------------------------------------------------------ */

    /**
     * 보관 구역 적재율 (%).
     * <p>
     * <b>분자는 보관 구역 재고({@link #storageQuantity})</b>다. 대기 구역이나 운송 중
     * 재고까지 더하면 보관 공간을 차지하지 않는 물건이 적재율을 올린다.
     * 수용량이 0 이면 나눗셈이 불가능하므로 0% 로 본다. 2D 도면의 적재율과 같은 규칙이다.
     */
    public int getUsageRate() {
        if (capacity <= 0) {
            return 0;
        }
        return (int) Math.round(storageQuantity * 100.0 / capacity);
    }

    /**
     * 보관 구역 밖에 있는 수량 (입고 · 출고 대기 + 운송 중).
     * <p>
     * 적재율에서는 빠지지만 실물은 이 센터에 있으므로 화면에 따로 알려준다.
     * 조용히 빼면 "재고 합계와 적재율 기준 수량이 왜 다른가" 를 설명할 수 없다.
     */
    public int getWaitingQuantity() {
        return Math.max(quantity - storageQuantity, 0);
    }

    /** 대기 · 운송 중 재고가 있는지 (없으면 화면에서 그 줄을 숨긴다) */
    public boolean isHasWaitingStock() {
        return getWaitingQuantity() > 0;
    }

    /** 진행바 너비용 (100% 초과 시에도 막대는 100 에서 멈춘다) */
    public int getUsageRateCapped() {
        return Numbers.cappedPercent(getUsageRate());
    }

    /**
     * 적재율 구간별 진행바 색.
     * <p>
     * 2D 도면과 <b>같은 경계 상수</b>({@link BinLoadStatus#NORMAL_THRESHOLD} / 
     * {@link BinLoadStatus#FULL_THRESHOLD})를 재사용한다. 여기서 60 / 90 을 다시 적으면
     * 한쪽만 고쳐졌을 때 같은 센터가 어떤 화면에서는 '보통', 다른 화면에서는 '포화' 로 보인다.
     */
    public String getUsageBarClass() {
        return BinLoadStatus.of(storageQuantity, getUsageRate()).getBadgeClass();
    }

    /** 적재 상태 라벨 (여유 / 보통 / 포화 / 비어있음) — 2D 도면과 같은 분류를 쓴다 */
    public String getUsageLabel() {
        return BinLoadStatus.of(storageQuantity, getUsageRate()).getDescription();
    }

    /** 남은 여유 수량 (초과 적재 시 0) — 적재율과 같은 기준(보관 구역)으로 뺀다 */
    public int getRemainingCapacity() {
        return Math.max(capacity - storageQuantity, 0);
    }

    public int quantityOf(MovementType type) {
        return activity.getOrDefault(type, 0);
    }

    public int getInboundQuantity() {
        return quantityOf(MovementType.INBOUND);
    }

    public int getOutboundQuantity() {
        return quantityOf(MovementType.OUTBOUND);
    }

    /** 이관으로 이 센터에서 나간 수량 */
    public int getTransferOutQuantity() {
        return quantityOf(MovementType.TRANSFER_OUT);
    }

    /** 이관으로 이 센터에 들어온 수량 */
    public int getTransferInQuantity() {
        return quantityOf(MovementType.TRANSFER_IN);
    }

    public int getDisposalQuantity() {
        return quantityOf(MovementType.DISPOSAL);
    }

    /** 기간 중 이관이 있었는지 (없으면 화면에서 이관 줄을 숨긴다) */
    public boolean isHasTransfer() {
        return getTransferOutQuantity() > 0 || getTransferInQuantity() > 0;
    }

    /**
     * 이관 순증감 (들어온 것 − 나간 것).
     * <p>
     * 양수면 이 센터로 재고가 모이고 있고, 음수면 다른 센터로 보내고 있다.
     * 전국 물류 흐름의 방향을 한 숫자로 보여준다.
     */
    public int getTransferNet() {
        return getTransferInQuantity() - getTransferOutQuantity();
    }

    public boolean isHasExpiring() {
        return expiringCount > 0;
    }

    public boolean isHasExpired() {
        return expiredCount > 0;
    }

    /** 재고가 하나도 없는 센터인지 (신설 직후 등) */
    public boolean isEmpty() {
        return quantity <= 0;
    }

    /**
     * 유통기한 임박 기준 일수 — 화면 문구에 쓴다.
     * <p>
     * 대시보드 · 2D 도면 · D-Day 뱃지와 같은 기준을 써야 숫자가 어긋나지 않는다.
     */
    public int getExpiringSoonDays() {
        return StockPolicy.EXPIRING_SOON_DAYS;
    }

    /**
     * 축종별 보관 수량. 센터의 운영 방향이 실제 재고로 지켜지는지 보여준다.
     * <p>
     * 순서를 유지해야 화면에서 축종 순서가 뒤바뀌지 않으므로 {@link LinkedHashMap} 을 쓴다.
     */
    private final Map<String, Integer> animalMix;

    public boolean isHasAnimalMix() {
        return animalMix != null && !animalMix.isEmpty();
    }

    /** 축종 구성 요약 문구 (예: 가금 470) */
    public String getAnimalMixSummary() {
        if (!isHasAnimalMix()) {
            return "-";
        }
        List<String> parts = animalMix.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue())
                .toList();
        return String.join(" · ", parts);
    }
}
