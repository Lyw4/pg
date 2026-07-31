package com.feedflow.admin.dto;

import com.feedflow.domain.BinPurpose;
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
 * <p>
 * 2D 도면 배치 좌표까지 함께 입력받는다. 좌표가 없으면 도면에 그릴 수 없다.
 */
@Getter
@Setter
@NoArgsConstructor
public class WarehouseBinForm {

    /**
     * 도면 격자 크기 (화면 입력 상한과 도면 렌더링이 같은 값을 써야 한다).
     * <p>
     * 행을 줄이고 열을 늘렸다. 행이 많으면 한 행의 높이가 얇아져
     * 사각형이 <b>가로로 긴 막대</b>처럼 보이기 때문이다.
     * 26 x 14 격자에서 5칸 x 3칸 사각형이 대략 정사각형에 가깝게 그려진다.
     */
    public static final int GRID_COLUMNS = 26;
    public static final int GRID_ROWS = 14;

    /** 수정 시에만 값이 존재 */
    private Long binId;

    @NotBlank(message = "구역 코드를 입력하세요.")
    @Size(max = 30, message = "구역 코드는 30자 이내로 입력하세요.")
    @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "구역 코드는 영문/숫자/하이픈(-)만 사용할 수 있습니다.")
    private String binCode;

    /**
     * 소속 물류센터.
     * <p>
     * 기본값을 두지 않는다. 예전에는 {@code Warehouse.WH1} 을 기본값으로 뒀지만,
     * 센터가 여러 곳이 되면 '기본 센터' 라는 개념 자체가 성립하지 않는다.
     * 등록자가 반드시 고르게 해 엉뚱한 센터에 구역이 생기는 것을 막는다.
     */
    @NotNull(message = "센터를 선택하세요.")
    private Long centerId;

    @NotBlank(message = "구역(Zone)을 입력하세요.")
    @Size(max = 20, message = "구역은 20자 이내로 입력하세요.")
    private String zone;

    @NotNull(message = "구역 용도를 선택하세요.")
    private BinPurpose binPurpose = BinPurpose.STORAGE;

    @Size(max = 20, message = "랙 번호는 20자 이내로 입력하세요.")
    private String rack;

    @NotNull(message = "단(층)을 입력하세요.")
    @Min(value = 1, message = "단(층)은 1 이상이어야 합니다.")
    @Max(value = 99, message = "단(층)은 99 이하로 입력하세요.")
    private Integer binLevel;

    @NotNull(message = "최대 적재 수량을 입력하세요.")
    @Min(value = 1, message = "최대 적재 수량은 1 이상이어야 합니다.")
    private Integer maxCapacity;

    /* ---------------- 2D 도면 배치 ---------------- */

    @NotNull(message = "도면 가로 위치를 입력하세요.")
    @Min(value = 1, message = "가로 위치는 1 이상이어야 합니다.")
    @Max(value = GRID_COLUMNS, message = "가로 위치는 " + GRID_COLUMNS + " 이하로 입력하세요.")
    private Integer posX = 1;

    @NotNull(message = "도면 세로 위치를 입력하세요.")
    @Min(value = 1, message = "세로 위치는 1 이상이어야 합니다.")
    @Max(value = GRID_ROWS, message = "세로 위치는 " + GRID_ROWS + " 이하로 입력하세요.")
    private Integer posY = 1;

    @NotNull(message = "도면 가로 크기를 입력하세요.")
    @Min(value = 1, message = "가로 크기는 1 이상이어야 합니다.")
    @Max(value = GRID_COLUMNS, message = "가로 크기는 " + GRID_COLUMNS + " 이하로 입력하세요.")
    private Integer posWidth = 2;

    @NotNull(message = "도면 세로 크기를 입력하세요.")
    @Min(value = 1, message = "세로 크기는 1 이상이어야 합니다.")
    @Max(value = GRID_ROWS, message = "세로 크기는 " + GRID_ROWS + " 이하로 입력하세요.")
    private Integer posHeight = 2;

    @Size(max = 200, message = "비고는 200자 이내로 입력하세요.")
    private String memo;

    private boolean active = true;

    public static WarehouseBinForm from(WarehouseBin bin) {
        WarehouseBinForm form = new WarehouseBinForm();
        form.binId = bin.getBinId();
        form.binCode = bin.getBinCode();
        form.centerId = bin.getCenter().getCenterId();
        form.zone = bin.getZone();
        form.binPurpose = bin.getBinPurpose();
        form.rack = bin.getRack();
        form.binLevel = bin.getBinLevel();
        form.maxCapacity = bin.getMaxCapacity();
        form.posX = bin.getPosX();
        form.posY = bin.getPosY();
        form.posWidth = bin.getPosWidth();
        form.posHeight = bin.getPosHeight();
        form.memo = bin.getMemo();
        form.active = bin.isActive();
        return form;
    }

    /**
     * 도면 격자를 벗어나지 않는지 검사한다.
     * <p>
     * 좌표와 크기를 각각 검증해도 {@code posX + posWidth} 가 격자를 넘을 수 있다.
     * (예: 23열에서 폭 4칸 → 26열까지 필요)
     */
    public boolean isWithinGrid() {
        if (posX == null || posY == null || posWidth == null || posHeight == null) {
            return true;    // 개별 @NotNull 이 먼저 걸러낸다
        }
        return posX + posWidth - 1 <= GRID_COLUMNS
                && posY + posHeight - 1 <= GRID_ROWS;
    }
}
