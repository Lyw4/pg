package com.feedflow.admin.dto;

import com.feedflow.domain.DefectStage;
import com.feedflow.domain.DefectStatus;
import lombok.Getter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 불량 목록 + 요약.
 *
 * <h3>요약과 집계의 범위가 다르다</h3>
 * <ul>
 *     <li><b>요약</b>(건수 · 수량 · 방치 건수) — 필터가 걸린 <b>목록에서만</b> 센다.
 *         "지금 보고 있는 것이 몇 건인가" 에 답한다.</li>
 *     <li><b>집계</b>(유형별 · 단계별 · 제조사별) — 필터와 <b>무관하게 전체</b>를 센다.
 *         상태를 하나 고른 순간 집계도 그 상태만 남으면 "다른 유형은 몇 건인가" 를
 *         알 수 없어 비교 자체가 불가능해진다.</li>
 * </ul>
 * 재고 현황이 행 수와 수량을 함께 보여주는 것과 같은 구조다.
 */
@Getter
public class DefectSearchDto {

    private final List<DefectRecordDto> rows;

    private final int rowCount;
    private final int totalQuantity;

    /** 미처리(격리 + 검사 중) 건수 */
    private final int openCount;
    private final int quarantinedCount;
    private final int inspectingCount;
    private final int resolvedCount;

    /** 미처리인데 7일 넘게 방치된 건수 */
    private final int staleCount;

    /** 재고 차감이 남은 건수 (반품 · 폐기로 처리했으나 폐기 화면을 거치지 않았을 수 있다) */
    private final int stockRemovalPendingCount;

    /** 제조사가 등록되지 않은 품목의 불량 건수 — 반품하려 해도 대상을 모르는 상태 */
    private final int manufacturerUnknownCount;

    private final int centerCount;

    /** 유형별 집계 (전체 기준) */
    private final List<DefectStatRow> typeStats;

    /** 단계별 집계 (전체 기준) */
    private final List<DefectStatRow> stageStats;

    /** 제조사별 집계 (전체 기준) */
    private final List<DefectStatRow> manufacturerStats;

    private DefectSearchDto(List<DefectRecordDto> rows,
                            List<DefectStatRow> typeStats,
                            List<DefectStatRow> stageStats,
                            List<DefectStatRow> manufacturerStats) {
        this.rows = rows;
        this.typeStats = typeStats;
        this.stageStats = stageStats;
        this.manufacturerStats = manufacturerStats;

        int quantity = 0;
        int open = 0;
        int quarantined = 0;
        int inspecting = 0;
        int resolved = 0;
        int stale = 0;
        int pending = 0;
        int unknown = 0;
        // 센터는 순서를 유지해 세면 어느 센터가 섞였는지 디버깅할 때 알기 쉽다
        Set<String> centers = new LinkedHashSet<>();

        for (DefectRecordDto row : rows) {
            quantity += row.getQuantity();

            if (row.getStatus() == DefectStatus.QUARANTINED) {
                quarantined++;
            } else if (row.getStatus() == DefectStatus.INSPECTING) {
                inspecting++;
            } else if (row.getStatus() == DefectStatus.RESOLVED) {
                resolved++;
            }
            if (row.isOpen()) {
                open++;
            }
            if (row.isStale()) {
                stale++;
            }
            if (row.isStockRemovalPending()) {
                pending++;
            }
            if (row.isManufacturerUnknown()) {
                unknown++;
            }
            if (row.getCenterName() != null) {
                centers.add(row.getCenterName());
            }
        }

        this.rowCount = rows.size();
        this.totalQuantity = quantity;
        this.openCount = open;
        this.quarantinedCount = quarantined;
        this.inspectingCount = inspecting;
        this.resolvedCount = resolved;
        this.staleCount = stale;
        this.stockRemovalPendingCount = pending;
        this.manufacturerUnknownCount = unknown;
        this.centerCount = centers.size();
    }

    public static DefectSearchDto of(List<DefectRecordDto> rows,
                                     List<DefectStatRow> typeStats,
                                     List<DefectStatRow> stageStats,
                                     List<DefectStatRow> manufacturerStats) {
        return new DefectSearchDto(rows, typeStats, stageStats, manufacturerStats);
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public boolean isHasManufacturerUnknown() {
        return manufacturerUnknownCount > 0;
    }

    /**
     * 입고 검사에서 잡힌 비율(%).
     * <p>
     * <b>높아야 좋은 지표다.</b> 보관 중이나 출고 검사에서 발견되는 비중이 크면
     * 입고 검수가 제 역할을 못 하고 있다는 뜻이고, 그만큼 불량 재고를 보관하는 데
     * 자리와 시간을 쓴 것이다.
     */
    public int getReceivingCatchRate() {
        String receivingLabel = DefectStage.RECEIVING.getDescription();
        int total = 0;
        int receiving = 0;
        for (DefectStatRow stat : stageStats) {
            total += stat.count();
            if (receivingLabel.equals(stat.label())) {
                receiving = stat.count();
            }
        }
        return total == 0 ? 0 : receiving * 100 / total;
    }
}
