package com.feedflow.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 구역 간 재고 이동 요청.
 * <p>
 * 출발지는 <b>재고 행({@code inventoryId})</b>으로 지정한다.
 * "로트 + 구역" 을 따로 받으면 존재하지 않는 조합이 들어올 수 있고,
 * 재고 행은 이미 (로트 × 구역 × 수량) 을 하나로 묶고 있어 검증이 단순해진다.
 */
@Getter
@Setter
@NoArgsConstructor
public class StockMoveForm {

    /** 출발 재고 행 (로트 × 구역) */
    @NotNull(message = "이동할 재고를 선택하세요.")
    private Long inventoryId;

    /** 도착 구역 */
    @NotNull(message = "도착 구역을 선택하세요.")
    private Long targetBinId;

    @NotNull(message = "이동 수량을 입력하세요.")
    @Min(value = 1, message = "이동 수량은 1 이상이어야 합니다.")
    private Integer quantity;

    @Size(max = 200, message = "메모는 200자 이내로 입력하세요.")
    private String memo;
}
