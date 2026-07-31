package com.feedflow.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 바코드 스캔 후 즉시 입고 요청 (JSON 본문).
 * <p>
 * record 대신 클래스를 사용하는 이유 : IDE 컴파일러가 -parameters 옵션을 끄면
 * record 의 생성자 파라미터명이 사라져 JSON 바인딩이 실패할 수 있다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ScanInboundRequest {

    /** 스캔된 코드 (로트번호 또는 품목코드) */
    @NotBlank(message = "스캔 코드가 없습니다.")
    @Size(max = 100, message = "스캔 코드가 너무 깁니다.")
    private String code;

    @NotNull(message = "입고할 창고 구역을 선택하세요.")
    private Long binId;

    @NotNull(message = "입고 수량을 입력하세요.")
    @Min(value = 1, message = "입고 수량은 1 이상이어야 합니다.")
    private Integer quantity;

    /**
     * 제조일자.
     * 품목코드를 스캔해 새 로트를 만드는 경우에만 사용하며, 비우면 오늘로 처리한다.
     * 로트번호를 스캔한 경우에는 기존 로트의 제조일자를 그대로 사용한다.
     */
    private LocalDate manufacturedDate;

    @Size(max = 200, message = "비고는 200자 이내로 입력하세요.")
    private String memo;
}
