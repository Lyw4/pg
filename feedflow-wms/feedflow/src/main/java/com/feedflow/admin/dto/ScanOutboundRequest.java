package com.feedflow.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 바코드 스캔 후 즉시 출고 요청 (JSON 본문).
 * <p>
 * 출고 로트는 지정하지 않는다. 스캔한 코드로 품목을 찾아
 * 유통기한이 임박한 로트부터 자동 차감(FEFO)한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ScanOutboundRequest {

    @NotBlank(message = "스캔 코드가 없습니다.")
    @Size(max = 100, message = "스캔 코드가 너무 깁니다.")
    private String code;

    @NotNull(message = "출고 수량을 입력하세요.")
    @Min(value = 1, message = "출고 수량은 1 이상이어야 합니다.")
    private Integer quantity;

    @Size(max = 200, message = "비고는 200자 이내로 입력하세요.")
    private String memo;
}
