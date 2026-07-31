package com.feedflow.admin.dto;

import lombok.Getter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 재고 현황 <b>검색 결과</b> — 목록 + 그 목록에 대한 집계.
 *
 * <h3>왜 집계를 따로 담는가</h3>
 * 화면 상단의 요약 카드는 <b>전국 기준</b>이다(적재 위치 수 · 총 보관 수량 · 오늘 입고).
 * 센터 필터를 걸면 목록만 좁혀지고 카드는 그대로여서, 카드 숫자와 목록 합계가
 * 어긋나 보인다. 사용자가 "필터가 안 걸렸나?" 하고 오해하기 쉽다.
 * <p>
 * 그래서 <b>지금 보고 있는 목록의 합계</b>를 따로 계산해 목록 헤더에 표시한다.
 * 이미 읽어온 행을 한 번 훑는 것이므로 추가 쿼리는 없다.
 */
@Getter
public class InventorySearchDto {

    private final List<InventoryDto> rows;

    /** 조회된 행 수 (로트 × 구역 조합 수) */
    private final int rowCount;

    /** 조회된 행의 보관 수량 합계 */
    private final int totalQuantity;

    /** 조회 결과에 포함된 서로 다른 센터 수 */
    private final int centerCount;

    /** 조회 결과에 포함된 서로 다른 구역 수 */
    private final int binCount;

    /** 유통기한이 지난 행 수 (0 보다 크면 화면에서 경고를 띄운다) */
    private final int expiredCount;

    private InventorySearchDto(List<InventoryDto> rows) {
        this.rows = rows;

        int quantity = 0;
        int expired = 0;

        // 센터/구역 수는 순서를 유지해 세면 디버깅 시 어느 센터가 섞였는지 알기 쉽다
        Set<Long> centers = new LinkedHashSet<>();
        Set<Long> bins = new LinkedHashSet<>();

        for (InventoryDto row : rows) {
            quantity += row.getQuantity() == null ? 0 : row.getQuantity();
            if (row.isExpired()) {
                expired++;
            }
            if (row.getCenterId() != null) {
                centers.add(row.getCenterId());
            }
            if (row.getBinId() != null) {
                bins.add(row.getBinId());
            }
        }

        this.rowCount = rows.size();
        this.totalQuantity = quantity;
        this.centerCount = centers.size();
        this.binCount = bins.size();
        this.expiredCount = expired;
    }

    public static InventorySearchDto of(List<InventoryDto> rows) {
        return new InventorySearchDto(rows);
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public boolean isHasExpired() {
        return expiredCount > 0;
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
