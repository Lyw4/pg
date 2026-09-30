package com.feedflow.admin.dto;

import lombok.Getter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 농장 고객사 <b>검색 결과</b> — 목록 + 그 목록에 대한 집계.
 *
 * <h3>왜 목록의 집계를 따로 담는가</h3>
 * 화면 상단의 센터 카드는 <b>전국 기준</b>이다. 필터를 걸면 목록만 좁혀지고 카드는
 * 그대로여서 두 숫자가 어긋나 보인다. 그래서 <b>지금 보고 있는 목록의 합계</b>를
 * 목록 헤더에 따로 표시한다. 이미 읽어온 행을 한 번 훑는 것이라 추가 쿼리는 없다.
 * (재고 현황의 {@link InventorySearchDto} 와 같은 구조다)
 *
 * <h3>월 사료량은 거래 중만 더한다</h3>
 * 목록에 거래 보류 농장이 섞여 있어도 합계에는 넣지 않는다. 대신
 * {@link #pausedCount} 를 함께 내려보내 "20곳인데 왜 18곳 물량인가" 를
 * 화면이 설명할 수 있게 한다.
 */
@Getter
public class FarmSearchDto {

    private final List<FarmCustomerDto> rows;

    /** 조회된 농장 수 (거래 보류 포함) */
    private final int rowCount;

    /** 그중 거래 중인 농장 수 */
    private final int activeCount;

    /** 그중 거래 보류인 농장 수 */
    private final int pausedCount;

    /** 거래 중 농장의 월 예상 사료량 합계 (포대) */
    private final int activeFeedQuantity;

    /** 조회된 농장의 사육 두수 합계 (거래 보류 포함) */
    private final int livestockCount;

    /** 조회 결과에 포함된 서로 다른 센터 수 */
    private final int centerCount;

    /** 좌표가 없어 지도에 표시할 수 없는 농장 수 */
    private final int missingLocationCount;

    private FarmSearchDto(List<FarmCustomerDto> rows) {
        this.rows = rows;

        int active = 0;
        int feed = 0;
        int livestock = 0;
        int missing = 0;
        Set<Long> centers = new LinkedHashSet<>();

        for (FarmCustomerDto row : rows) {
            if (row.isTrading()) {
                active++;
                feed += row.getMonthlyFeedQuantity();
            }
            livestock += row.getLivestockCount();
            if (!row.isMappable()) {
                missing++;
            }
            if (row.getCenterId() != null) {
                centers.add(row.getCenterId());
            }
        }

        this.rowCount = rows.size();
        this.activeCount = active;
        this.pausedCount = rows.size() - active;
        this.activeFeedQuantity = feed;
        this.livestockCount = livestock;
        this.centerCount = centers.size();
        this.missingLocationCount = missing;
    }

    public static FarmSearchDto of(List<FarmCustomerDto> rows) {
        return new FarmSearchDto(rows);
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /** 거래 보류가 섞여 있는지 (섞여 있을 때만 화면이 합계 기준을 안내한다) */
    public boolean isHasPaused() {
        return pausedCount > 0;
    }

    /**
     * 결과가 여러 센터에 걸쳐 있는지.
     * <p>
     * 걸쳐 있으면 화면이 센터 컬럼을 강조해야 한다. 한 센터뿐이면 모든 행에
     * 같은 값이 반복되므로 강조할 이유가 없다.
     */
    public boolean isAcrossCenters() {
        return centerCount > 1;
    }
}
