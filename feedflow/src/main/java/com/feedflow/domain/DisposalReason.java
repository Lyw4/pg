package com.feedflow.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 재고 폐기 사유.
 * 폐기는 재고 손실로 이어지므로 사유를 반드시 남긴다.
 */
@Getter
@RequiredArgsConstructor
public enum DisposalReason {

    EXPIRED("유통기한 경과", "bg-dark"),
    DAMAGED("파손 / 포장 손상", "bg-warning text-dark"),
    CONTAMINATED("변질 / 오염", "bg-danger"),
    WET("침수 / 습기", "bg-danger"),
    SAMPLE("품질검사 / 샘플 사용", "bg-info text-dark"),
    LOSS("재고 실사 손실", "bg-secondary"),
    OTHER("기타", "bg-secondary");

    private final String description;
    private final String badgeClass;
}
