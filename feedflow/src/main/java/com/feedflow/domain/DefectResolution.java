package com.feedflow.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 불량 처리 방법 (어떻게 마무리했는가).
 *
 * <h3>각 처리가 우리 기존 기능과 어떻게 연결되는가</h3>
 * <table>
 *     <tr><th>처리</th><th>재고에 일어나는 일</th><th>담당 기능</th></tr>
 *     <tr><td>재작업</td><td>정상 재고로 복귀</td><td>구역 간 이동 (검수 구역 → 보관 구역)</td></tr>
 *     <tr><td>공급업체 반품</td><td>창고에서 빠진다</td><td>폐기 (사유는 반품)</td></tr>
 *     <tr><td>폐기</td><td>창고에서 빠진다</td><td>폐기</td></tr>
 *     <tr><td>특채 사용</td><td>정상 재고로 복귀</td><td>구역 간 이동</td></tr>
 * </table>
 *
 * <h3>이 enum 이 재고를 직접 바꾸지 않는다</h3>
 * 불량 건에 "폐기로 처리했다" 고 기록하는 것과 <b>실제로 재고를 차감하는 것은 별개</b>다.
 * 재고 차감은 기존 폐기 기능이 하고, 그 기능이 재고 3계층을 함께 줄이며
 * {@link MovementType#DISPOSAL} 이력을 남긴다.
 * <p>
 * 불량 기록에서 재고를 직접 손대면 <b>같은 일을 하는 코드가 두 곳</b>이 된다.
 * 한쪽만 고치면 재고는 줄었는데 이력이 없거나, 이력은 있는데 재고가 그대로인
 * 상태가 만들어진다. 그래서 이 화면은 <b>다음에 무엇을 해야 하는지 안내</b>만 한다.
 *
 * <h3>반품에 폐기 기능을 쓰는 이유</h3>
 * 반품은 창고 관점에서 폐기와 같다 — 재고가 빠지고 다시 돌아오지 않는다.
 * 별도 이력 유형({@code RETURN})을 만들 수도 있지만, 그러면 매입 실적 집계에서
 * 새 유형을 빠뜨릴 위험이 생긴다. 이력 유형은 이미 여덟 가지이고
 * 그 각각을 세는 곳이 여러 군데다.
 */
@Getter
@RequiredArgsConstructor
public enum DefectResolution {

    REWORK("재작업 후 정상 복귀", "bg-success",
            false, "구역 간 이동으로 보관 구역에 넣으면 다시 출고할 수 있습니다."),

    CONCESSION("특채 사용", "bg-info text-dark",
            false, "품질 기준을 낮춰 사용하기로 한 재고입니다. "
                   + "구역 간 이동으로 보관 구역에 넣어야 출고할 수 있습니다."),

    SUPPLIER_RETURN("공급업체 반품", "bg-warning text-dark",
            true, "재고 폐기 화면에서 해당 수량을 차감해야 장부와 실물이 맞습니다. "
                  + "제조사 연락처를 확인하세요."),

    DISPOSAL("폐기", "bg-dark",
            true, "재고 폐기 화면에서 해당 수량을 차감해야 장부와 실물이 맞습니다.");

    /** 화면 표기용 한글 라벨 */
    private final String description;

    /** Bootstrap 5 뱃지 클래스 */
    private final String badgeClass;

    /**
     * 재고가 창고에서 빠지는 처리인지.
     * <p>
     * 참이면 담당자가 <b>폐기 화면에서 별도로 차감</b>해야 한다. 이 값이 곧
     * "아직 할 일이 남았다" 는 표시가 되어 화면에서 안내 문구를 띄운다.
     */
    private final boolean stockRemoved;

    /** 처리 후 담당자가 해야 할 일 안내 */
    private final String followUp;

    /** 제조사 정보가 필요한 처리인지 (반품할 곳을 알아야 한다) */
    public boolean isRequiresManufacturer() {
        return this == SUPPLIER_RETURN;
    }
}
