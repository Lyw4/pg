package com.feedflow.admin.dto;

import java.util.List;

/**
 * 센터별 재고 분포 차트 응답 (Chart.js 도넛).
 * <p>
 * 매출 차트와 같은 방식으로 {@code /api/admin/center-stock} 에서 JSON 으로만 제공한다.
 * HTML 렌더링 시 넘기지 않는 이유 — 차트 요청이 실패해도 화면 본문(센터 카드)은
 * 그대로 그려져야 한다. 재고 현황은 차트 없이도 카드로 읽을 수 있다.
 *
 * <h3>권한</h3>
 * 이 데이터는 <b>매출이 아니라 재고</b>다. 창고 담당자(STAFF)도 봐야 하는 정보이므로
 * 매출 차트와 달리 {@code ADMIN} 으로 제한하지 않는다.
 *
 * @param labels     센터명 (centerCode 순)
 * @param quantities 센터별 보관 수량
 * @param total      전국 합계
 */
public record CenterStockChartDto(
        List<String> labels,
        List<Integer> quantities,
        int total
) {

    public static CenterStockChartDto of(List<CenterStockRow> rows) {
        List<String> labels = rows.stream().map(CenterStockRow::centerName).toList();
        List<Integer> quantities = rows.stream().map(CenterStockRow::totalQuantity).toList();
        int total = quantities.stream().mapToInt(Integer::intValue).sum();
        return new CenterStockChartDto(labels, quantities, total);
    }
}
