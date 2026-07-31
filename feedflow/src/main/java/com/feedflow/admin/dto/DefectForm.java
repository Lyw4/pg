package com.feedflow.admin.dto;

import com.feedflow.domain.DefectStage;
import com.feedflow.domain.DefectType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 불량 발견 등록 폼.
 *
 * <h3>구역과 발견 단계를 모두 받는 이유</h3>
 * 대부분은 구역만 고르면 단계가 정해진다(검수 구역 → 입고 검사). 그래서 단계는
 * 선택값이고, 비워 두면 구역 용도로 추정한다.
 * <p>
 * 그래도 단계를 직접 고를 수 있어야 하는 경우가 있다. 보관 구역에 이미 들여놓은
 * 재고에서 나중에 문제를 발견하면 구역은 보관 구역이지만, 담당자가 판단하기에
 * 입고 때 이미 있던 문제일 수 있다. 이때 단계를 고쳐 쓸 수 있어야 공급업체에
 * 책임을 물을 근거가 된다.
 *
 * <h3>구역이 선택값인 이유</h3>
 * 센터 간 이관 중에 생긴 파손은 어느 구역에서 났다고 말할 수 없다. 출발 센터를
 * 떠났고 도착 센터에 들어오지 않은 상태이기 때문이다. 이때는 구역을 비우고
 * 단계만 '센터 간 이관 중' 으로 남긴다.
 */
@Getter
@Setter
@NoArgsConstructor
public class DefectForm {

    /**
     * 불량이 발생한 로트.
     * <p>
     * 품목이 아니라 로트를 받는다. 같은 품목이어도 제조 단위가 다르면 별개 문제다.
     * 로트를 특정하지 않으면 "이 제조 단위에서 반복되는가" 를 알 수 없다.
     */
    @NotNull(message = "불량이 발생한 로트를 선택하세요.")
    private Long lotId;

    /** 발견 구역. 이관 중 파손처럼 특정할 수 없으면 비운다 */
    private Long binId;

    @NotNull(message = "불량 수량을 입력하세요.")
    @Min(value = 1, message = "불량 수량은 1 이상이어야 합니다.")
    private Integer quantity;

    @NotNull(message = "불량 유형을 선택하세요.")
    private DefectType defectType;

    /** 발견 단계. 비우면 선택한 구역의 용도로 추정한다 */
    private DefectStage stage;

    @Size(max = 300, message = "발견 상황은 300자 이내로 입력하세요.")
    private String memo;

    /** 도메인에 넘길 수량 (null 은 검증에서 걸러진다) */
    public int quantityValue() {
        return quantity == null ? 0 : quantity;
    }
}
