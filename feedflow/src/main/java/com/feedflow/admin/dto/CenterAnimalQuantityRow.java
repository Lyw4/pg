package com.feedflow.admin.dto;

import com.feedflow.domain.AnimalType;

/**
 * 센터 × 축종 수량 집계 (Repository JPQL 전용 DTO).
 *
 * <h3>수요와 공급 양쪽에 쓴다</h3>
 * 같은 축(센터 × 축종)으로 두 가지를 집계하기 때문이다.
 * <ul>
 *     <li><b>공급</b> — 그 센터가 출고할 수 있는 그 축종 사료 재고
 *         ({@code InventoryRepository.findAllocatableStockByCenterAndAnimalType})</li>
 *     <li><b>수요</b> — 그 센터가 담당하는 농장들의 월 예상 사료량
 *         ({@code FarmCustomerRepository.findDemandByCenterAndAnimalType})</li>
 * </ul>
 * 구조가 같아 record 를 두 개 만들 이유가 없다. 대신 <b>쓰는 쪽에서 이름으로
 * 구분</b>한다({@code supplyByKey} / {@code demandByKey}). 이 프로젝트에서
 * {@code s} 와 {@code ss} 처럼 비슷한 이름을 쓴 것이 가장 오래 걸린 버그의
 * 원인이었으므로, 재사용하는 대신 변수명을 길게 쓴다.
 *
 * <h3>두 집계가 맞물리는 이유</h3>
 * 농장의 축종과 품목의 축종이 <b>같은 {@link AnimalType} enum</b> 을 쓰기 때문이다.
 * 팀원 모듈은 축종을 {@code "조류(닭/오리)"} 같은 자유 문자열로 두었는데,
 * 그대로였다면 이 비교 자체가 불가능했다.
 *
 * @param centerId   센터 식별자
 * @param animalType 축종
 * @param quantity   수량 (공급이면 포대 재고, 수요면 월 예상 사료량)
 */
public record CenterAnimalQuantityRow(
        Long centerId,
        AnimalType animalType,
        Long quantity
) {

    public int amount() {
        return quantity == null ? 0 : quantity.intValue();
    }
}
