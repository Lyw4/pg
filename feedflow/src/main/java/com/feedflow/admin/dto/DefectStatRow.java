package com.feedflow.admin.dto;

/**
 * 불량 집계 결과 (Repository JPQL 전용 DTO).
 *
 * <h3>왜 건수와 수량을 함께 담는가</h3>
 * 둘이 다른 것을 말한다.
 * <ul>
 *     <li><b>건수</b> — 문제가 몇 번 일어났는가. 공급업체 평가와 재발 여부의 기준이다.
 *         1포대 파손이 열 번 나온 것과 100포대가 한 번 나온 것은 다른 문제다.</li>
 *     <li><b>수량</b> — 손실 규모가 얼마인가. 폐기 비용과 직결된다.</li>
 * </ul>
 * 하나만 내려보내면 화면이 다른 하나를 설명할 수 없다. 재고 현황에서
 * 행 수와 수량을 함께 보여주는 것과 같은 이유다.
 *
 * @param label    집계 기준 라벨 (유형명 · 단계명 · 제조사명 등)
 * @param defectCount 건수
 * @param quantity 수량 합계 (포대)
 */
public record DefectStatRow(
        String label,
        Long defectCount,
        Long quantity
) {

    public int count() {
        return defectCount == null ? 0 : defectCount.intValue();
    }

    public int amount() {
        return quantity == null ? 0 : quantity.intValue();
    }

    /** 건당 평균 수량 — 낱개 불량이 잦은지, 대량 불량이 드문지를 가른다 */
    public int averagePerCase() {
        return count() == 0 ? 0 : amount() / count();
    }
}
