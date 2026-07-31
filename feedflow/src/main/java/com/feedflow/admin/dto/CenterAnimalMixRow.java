package com.feedflow.admin.dto;

import com.feedflow.domain.AnimalType;

/**
 * 센터별 · 축종별 보관 수량 집계 결과 (Repository JPQL 전용 DTO).
 * <p>
 * 센터의 운영 방향(예: 나주 = 닭 · 오리 최우선)이 <b>실제 재고로 지켜지는지</b> 보여준다.
 * 방향과 재고가 어긋나면 배차 계획이나 발주가 잘못되고 있다는 신호다.
 *
 * @param centerId   센터 식별자
 * @param animalType 축종
 * @param quantity   보관 수량 합계
 */
public record CenterAnimalMixRow(
        Long centerId,
        AnimalType animalType,
        Long quantity
) {

    public int totalQuantity() {
        return quantity == null ? 0 : quantity.intValue();
    }
}
