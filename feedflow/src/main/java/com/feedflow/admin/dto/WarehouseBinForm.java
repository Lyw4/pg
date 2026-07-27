package com.feedflow.admin.dto;

import com.feedflow.domain.WarehouseBin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 창고 구역 등록 / 수정 폼.
 */
@Getter
@Setter
@NoArgsConstructor
public class WarehouseBinForm {

    /** 수정 시에만 값이 존재 */
    private Long binId;

    @NotBlank(message = "구역 코드를 입력하세요.")
    @Size(max = 30, message = "구역 코드는 30자 이내로 입력하세요.")
    @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "구역 코드는 영문/숫자/하이픈(-)만 사용할 수 있습니다.")
    private String binCode;

    @NotBlank(message = "구역(Zone)을 입력하세요.")
    @Size(max = 20, message = "구역은 20자 이내로 입력하세요.")
    private String zone;

    @Size(max = 20, message = "랙 번호는 20자 이내로 입력하세요.")
    private String rack;

    @NotNull(message = "단(층)을 입력하세요.")
    @Min(value = 1, message = "단(층)은 1 이상이어야 합니다.")
    @Max(value = 99, message = "단(층)은 99 이하로 입력하세요.")
    private Integer binLevel;

    @NotNull(message = "최대 적재 수량을 입력하세요.")
    @Min(value = 1, message = "최대 적재 수량은 1 이상이어야 합니다.")
    private Integer maxCapacity;

    @Size(max = 200, message = "비고는 200자 이내로 입력하세요.")
    private String memo;

    private boolean active = true;

    public static WarehouseBinForm from(WarehouseBin bin) {
        WarehouseBinForm form = new WarehouseBinForm();
        form.binId = bin.getBinId();
        form.binCode = bin.getBinCode();
        form.zone = bin.getZone();
        form.rack = bin.getRack();
        form.binLevel = bin.getBinLevel();
        form.maxCapacity = bin.getMaxCapacity();
        form.memo = bin.getMemo();
        form.active = bin.isActive();
        return form;
    }
}
