package com.feedflow.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 축종 (취급 대상 가축).
 * <p>
 * 기존에는 자유 입력 문자열이라 '닭', '오리', '염소' 처럼 담당자마다 다른 값이 들어갈 수 있었다.
 * 취급 범위를 <b>소 · 돼지 · 조류(닭·오리)</b> 로 확정했으므로 enum 으로 고정한다.
 * <p>
 * 닭과 오리는 사료 배합과 관리 방식이 유사해 <b>조류(POULTRY)</b> 하나로 묶는다.
 * <p>
 * DB 에는 {@code EnumType.STRING} 으로 이름(CATTLE / PIG / POULTRY)이 저장되고,
 * 화면과 JSON 에는 {@link #getDescription()} 의 한글 라벨을 내려준다.
 */
@Getter
@RequiredArgsConstructor
public enum AnimalType {

    CATTLE("소", "한우 · 육우 · 번식우"),
    PIG("돼지", "자돈 · 육성돈 · 임신돈"),
    POULTRY("조류", "닭 · 오리");

    /** 화면 표기용 한글 라벨 */
    private final String description;

    /** 세부 대상 안내 (폼 도움말용) */
    private final String detail;
}
