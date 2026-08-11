package com.ex.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DisposalReason {
    EXPIRED("유통기한 경과"),
    DAMAGED("파손·포장 손상"),
    CONTAMINATED("변질·오염"),
    WET("침수·습기"),
    SAMPLE("검사·샘플 사용"),
    LOSS("재고 실사 손실"),
    OTHER("기타");

    private final String label;
}
