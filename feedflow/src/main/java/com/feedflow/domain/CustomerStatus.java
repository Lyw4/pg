package com.feedflow.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 농장 고객사의 거래 상태.
 *
 * <h3>왜 두 값뿐인가</h3>
 * '해지' 를 넣지 않았다. 거래가 끝난 농장도 <b>과거 납품 이력의 상대</b>로 남아야 하고,
 * 나중에 거래를 재개하는 일이 흔하다. 목록에서 지우는 것이 아니라 보류로 두면
 * 이력이 끊기지 않고 재개도 한 번의 상태 변경으로 끝난다.
 * <p>
 * 반대로 정말 지워야 하는 데이터라면 상태가 아니라 삭제 절차의 문제다.
 * 상태 값으로 삭제를 흉내내면 목록 쿼리마다 조건이 하나씩 붙는다.
 *
 * <h3>집계에 미치는 영향</h3>
 * <b>월 예상 사료량은 {@code ACTIVE} 만 합산한다.</b> 보류 중인 농장의 물량을 더하면
 * 실제로 나가지 않을 사료를 기준으로 발주 계획을 세우게 된다.
 * 반대로 <b>농장 수는 전체를 센다</b> — 담당 농장이 몇 곳인지는 보류와 무관한 사실이다.
 */
@Getter
@RequiredArgsConstructor
public enum CustomerStatus {

    ACTIVE("거래 중", "bg-primary"),
    PAUSED("거래 보류", "bg-secondary");

    /** 화면 표기용 한글 라벨 */
    private final String description;

    /** Bootstrap 5 뱃지 클래스 */
    private final String badgeClass;

    /**
     * 이 상태에서 전환할 수 있는 반대 상태.
     * <p>
     * 화면의 상태 변경 버튼이 "거래 보류" / "거래 재개" 중 무엇을 보여줄지 결정한다.
     * 값이 둘뿐이라 조건문으로 쓸 수도 있지만, 상태가 늘어나면 화면마다 분기를
     * 고쳐야 하므로 enum 이 답을 갖게 한다.
     */
    public CustomerStatus toggled() {
        return this == ACTIVE ? PAUSED : ACTIVE;
    }
}
