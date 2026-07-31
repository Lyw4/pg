package com.feedflow.admin.dto;

import com.feedflow.domain.DefectResolution;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 불량 처리 폼 (검사 착수 · 처리 완료 공용).
 *
 * <h3>처리 방법이 선택값인 이유</h3>
 * 이 폼이 두 가지 일을 한다. 처리 방법을 비워 보내면 <b>검사 착수</b>(격리 → 검사 중),
 * 채워 보내면 <b>처리 완료</b>다. 두 화면이 로트 · 수량을 똑같이 다시 보여주고
 * 메모만 받으므로 폼을 나누면 같은 것을 두 번 쓰게 된다.
 *
 * <h3>이 폼이 재고를 건드리지 않는다</h3>
 * 반품이나 폐기로 처리해도 재고는 그대로다. 재고 차감은 폐기 화면이 담당한다.
 * 처리 결과에 다음에 할 일을 안내로 붙여 담당자가 폐기 화면으로 가게 한다.
 * 두 곳에서 재고를 줄이면 한쪽만 고쳤을 때 재고는 줄었는데 이력이 없는 상태가 생긴다.
 */
@Getter
@Setter
@NoArgsConstructor
public class DefectResolveForm {

    @NotNull(message = "처리할 불량 건을 선택하세요.")
    private Long defectId;

    /** 처리 방법. 비우면 검사 착수로만 처리한다 */
    private DefectResolution resolution;

    /** 검사 소견 또는 처리 결과 (반품 접수번호, 재작업 내용 등) */
    @Size(max = 300, message = "처리 내용은 300자 이내로 입력하세요.")
    private String resolutionMemo;

    /** 처리 완료 요청인지 (처리 방법을 골랐는지) */
    public boolean isResolveRequest() {
        return resolution != null;
    }
}
