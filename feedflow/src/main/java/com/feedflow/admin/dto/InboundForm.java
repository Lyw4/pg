package com.feedflow.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 입고 등록 폼.
 * <p>
 * lotNo 를 비워두면 서버에서 자동으로 로트번호를 부여한다.
 * 유통기한은 입력받지 않고 제조일자 + 품목의 유통기한 일수로 자동 계산한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InboundForm {

    @NotNull(message = "품목을 선택하세요.")
    private Long productId;

    @NotNull(message = "입고할 창고 구역을 선택하세요.")
    private Long binId;

    /** 비워두면 자동 생성 (예: L260727-FD-CT-001-01) */
    @Size(max = 50, message = "로트번호는 50자 이내로 입력하세요.")
    @Pattern(regexp = "^$|^[A-Za-z0-9-]+$", message = "로트번호는 영문/숫자/하이픈(-)만 사용할 수 있습니다.")
    private String lotNo;

    @NotNull(message = "제조일자를 입력하세요.")
    @PastOrPresent(message = "제조일자는 오늘 이전 날짜여야 합니다.")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate manufacturedDate;

    @NotNull(message = "입고 수량을 입력하세요.")
    @Min(value = 1, message = "입고 수량은 1 이상이어야 합니다.")
    private Integer quantity;

    @Size(max = 200, message = "비고는 200자 이내로 입력하세요.")
    private String memo;
}
