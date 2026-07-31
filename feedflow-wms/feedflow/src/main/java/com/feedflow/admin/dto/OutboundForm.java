package com.feedflow.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 직접 출고(주문과 무관한 출고) 요청 폼.
 * 출고할 로트는 사용자가 지정하지 않고 FEFO 규칙으로 서버가 자동 선택한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class OutboundForm {

    @NotNull(message = "품목을 선택하세요.")
    private Long productId;

    @NotNull(message = "출고 수량을 입력하세요.")
    @Min(value = 1, message = "출고 수량은 1 이상이어야 합니다.")
    private Integer quantity;

    @Size(max = 200, message = "비고는 200자 이내로 입력하세요.")
    private String memo;
}
