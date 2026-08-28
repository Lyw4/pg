package com.feedflow.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 불량 건의 처리 상태.
 *
 * <pre>
 *   QUARANTINED  격리      불량을 발견해 따로 빼 둔 상태
 *        ↓
 *   INSPECTING   검사 중   원인과 처리 방법을 판단하는 중
 *        ↓
 *   RESOLVED     처리 완료 재작업 · 반품 · 폐기 · 특채 중 하나로 마무리
 * </pre>
 *
 * <h3>되돌릴 수 없는 흐름이다</h3>
 * 처리 완료에서 격리로 돌아가지 않는다. 같은 로트에서 불량이 또 나오면
 * <b>새 건으로 등록</b>한다. 한 건을 되돌려 쓰면 "이 로트에서 불량이 몇 번
 * 나왔는가" 를 셀 수 없게 된다. 불량 이력은 공급업체 평가의 근거이므로
 * 건수가 뭉개지면 안 된다.
 *
 * <h3>격리 상태의 재고는 이미 출고되지 않는다</h3>
 * 불량 재고는 검수 구역({@link BinPurpose#INSPECTION})에 두는 것이 원칙이고,
 * 그 구역은 출고 후보에서 제외된다. <b>이 상태 값이 출고를 막는 것이 아니다.</b>
 * 출고 차단은 구역 용도가 하고, 이 값은 처리 진행 상황만 나타낸다.
 * 두 곳에서 같은 것을 막으면 한쪽만 고쳤을 때 어긋난다.
 */
@Getter
@RequiredArgsConstructor
public enum DefectStatus {

    QUARANTINED("격리", "bg-danger"),
    INSPECTING("검사 중", "bg-warning text-dark"),
    RESOLVED("처리 완료", "bg-success");

    /** 화면 표기용 한글 라벨 */
    private final String description;

    /** Bootstrap 5 뱃지 클래스 */
    private final String badgeClass;

    /** 아직 마무리되지 않은 건인지 (목록 기본 필터 · 대시보드 집계 기준) */
    public boolean isOpen() {
        return this != RESOLVED;
    }

    /**
     * 지정한 상태로 넘어갈 수 있는지.
     * <p>
     * 격리에서 곧바로 처리 완료로 가는 것도 허용한다. 원인이 명백한 경우
     * (유통기한 경과처럼) 검사 단계를 거칠 이유가 없다. 다만 <b>거꾸로 가는 것과
     * 같은 상태로 다시 가는 것은 막는다.</b>
     */
    public boolean canMoveTo(DefectStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case QUARANTINED -> target == INSPECTING || target == RESOLVED;
            case INSPECTING -> target == RESOLVED;
            case RESOLVED -> false;
        };
    }
}
