package com.feedflow.admin.dto;

import com.feedflow.domain.DisposalReason;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 재고 폐기 요청 폼.
 * <p>
 * 폐기는 FEFO 자동 선택이 아니라 <b>특정 로트 × 특정 구역의 재고</b>를
 * 정확히 지정해서 차감한다. (만료된 로트만 콕 집어 처리해야 하기 때문)
 */
@Getter
@Setter
@NoArgsConstructor
public class DisposalForm {

    /** 폐기 대상 재고 (로트 × 구역) */
    @NotNull(message = "폐기 대상 재고를 선택하세요.")
    private Long inventoryId;

    @NotNull(message = "폐기 수량을 입력하세요.")
    @Min(value = 1, message = "폐기 수량은 1 이상이어야 합니다.")
    private Integer quantity;

    @NotNull(message = "폐기 사유를 선택하세요.")
    private DisposalReason reason;

    @Size(max = 200, message = "비고는 200자 이내로 입력하세요.")
    private String memo;
}
