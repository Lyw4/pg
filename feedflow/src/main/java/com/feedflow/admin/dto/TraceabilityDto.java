package com.feedflow.admin.dto;

import com.feedflow.common.util.DDay;
import com.feedflow.domain.MovementType;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 로트 하나의 생애주기 추적 결과.
 * <p>
 * <b>입고 → 보관 → 출고 / 출고취소 / 폐기</b> 를 한 화면에 모아 CS 대응 시
 * 유통 과정을 즉시 역추적할 수 있게 한다.
 *
 * <h3>구성</h3>
 * <ul>
 *     <li>로트 · 품목 요약 (제조일 · 유통기한 · D-Day)</li>
 *     <li>입고 요약 (최초 입고 일시 · 누적 입고 수량)</li>
 *     <li>현재 보관 위치 (구역별 잔여 수량)</li>
 *     <li>시간순 타임라인 (모든 이동 이력 + 시점별 잔여 수량)</li>
 * </ul>
 */
@Getter
@Builder
public class TraceabilityDto {

    /* ---------------- 로트 · 품목 ---------------- */
    private final Long lotId;
    private final String lotNo;

    private final Long productId;
    private final String productCode;
    private final String productName;
    private final String animalType;
    private final String productType;

    private final LocalDate manufacturedDate;
    private final LocalDate expirationDate;
    private final long remainingDays;
    private final boolean expired;

    /* ---------------- 수량 ---------------- */

    /** 로트에 기록된 현재 잔여 수량 ({@code ProductLot.lotQuantity}) */
    private final int lotQuantity;

    /** 누적 입고 수량 (입고 + 출고취소 복구) */
    private final int totalInbound;

    /** 누적 출고 수량 */
    private final int totalOutbound;

    /** 누적 출고취소 복구 수량 */
    private final int totalCanceled;

    /** 누적 폐기 수량 */
    private final int totalDisposed;

    /**
     * 이력을 누적해 계산한 잔여 수량.
     * <p>
     * {@link #lotQuantity} 와 같아야 정상이다. 다르면 이력과 재고가 어긋난 것이므로
     * 화면에서 경고를 띄운다. (재고 정합성 점검 화면으로 안내)
     */
    private final int calculatedBalance;

    /* ---------------- 이력 ---------------- */

    /** 최초 입고 일시 (이력이 없으면 null) */
    private final LocalDateTime firstInboundAt;

    /** 마지막 이동 일시 */
    private final LocalDateTime lastMovedAt;

    /** 현재 보관 위치 (구역별) */
    private final List<InventoryDto> currentStorage;

    /** 시간순 타임라인 */
    private final List<TraceEventDto> timeline;

    /**
     * 추적 결과를 조립한다.
     *
     * @param lot            fetch join 으로 product 가 초기화된 로트
     * @param currentStorage 현재 구역별 재고
     * @param timeline       시간순 이벤트 (누적 잔여 수량까지 계산된 상태)
     * @param today          D-Day 계산 기준일
     */
    public static TraceabilityDto of(ProductLot lot,
                                     List<InventoryDto> currentStorage,
                                     List<TraceEventDto> timeline,
                                     LocalDate today) {
        Product product = lot.getProduct();

        int totalInbound = sumOf(timeline, MovementType.INBOUND);
        int totalCanceled = sumOf(timeline, MovementType.CANCEL);
        int totalOutbound = sumOf(timeline, MovementType.OUTBOUND);
        int totalDisposed = sumOf(timeline, MovementType.DISPOSAL);

        return TraceabilityDto.builder()
                .lotId(lot.getLotId())
                .lotNo(lot.getLotNo())
                .productId(product.getProductId())
                .productCode(product.getProductCode())
                .productName(product.getName())
                .animalType(product.getAnimalType().getDescription())
                .productType(product.getProductType().getDescription())
                .manufacturedDate(lot.getManufacturedDate())
                .expirationDate(lot.getExpirationDate())
                .remainingDays(lot.daysUntilExpiration(today))
                .expired(lot.isExpired(today))
                .lotQuantity(lot.getLotQuantity() == null ? 0 : lot.getLotQuantity())
                .totalInbound(totalInbound + totalCanceled)
                .totalOutbound(totalOutbound)
                .totalCanceled(totalCanceled)
                .totalDisposed(totalDisposed)
                .calculatedBalance(timeline.isEmpty()
                        ? 0
                        : timeline.get(timeline.size() - 1).getBalanceAfter())
                .firstInboundAt(timeline.isEmpty() ? null : timeline.get(0).getOccurredAt())
                .lastMovedAt(timeline.isEmpty()
                        ? null
                        : timeline.get(timeline.size() - 1).getOccurredAt())
                .currentStorage(currentStorage)
                .timeline(timeline)
                .build();
    }

    private static int sumOf(List<TraceEventDto> timeline, MovementType type) {
        return timeline.stream()
                .filter(event -> event.getMovementType() == type)
                .mapToInt(TraceEventDto::getQuantity)
                .sum();
    }

    /* ------------------------------------------------------------------
     * 화면 표기
     * ------------------------------------------------------------------ */

    public String getDDayLabel() {
        return DDay.label(remainingDays);
    }

    public String getDDayBadgeClass() {
        return DDay.badgeClass(remainingDays);
    }

    public int getEventCount() {
        return timeline.size();
    }

    public boolean isHasHistory() {
        return !timeline.isEmpty();
    }

    /** 현재 어느 구역에도 남아 있지 않은지 (전량 출고 · 폐기됨) */
    public boolean isDepleted() {
        return currentStorage.isEmpty();
    }

    /** 보관 중인 구역 수 */
    public int getStorageBinCount() {
        return currentStorage.size();
    }

    /**
     * 이력 누적값과 로트 잔여 수량이 일치하는지.
     * <p>
     * 어긋나면 이력이 누락됐거나 재고가 이력 없이 변경된 것이다.
     */
    public boolean isBalanceMatched() {
        return calculatedBalance == lotQuantity;
    }

    /** 출고취소가 한 번이라도 있었는지 (타임라인 강조용) */
    public boolean isHasCancellation() {
        return totalCanceled > 0;
    }
}
