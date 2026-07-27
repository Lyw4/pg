package com.feedflow.admin.dto;

import com.feedflow.domain.Product;
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
 * 품목 등록 / 수정 폼.
 * <p>
 * imageUrl / description 은 B2C 쇼핑몰 담당 영역이므로 관리자 폼에서 다루지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProductForm {

    /** 수정 시에만 값이 존재 */
    private Long productId;

    @NotBlank(message = "품목 코드를 입력하세요.")
    @Size(max = 30, message = "품목 코드는 30자 이내로 입력하세요.")
    @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "품목 코드는 영문/숫자/하이픈(-)만 사용할 수 있습니다.")
    private String productCode;

    @NotBlank(message = "품목명을 입력하세요.")
    @Size(max = 200, message = "품목명은 200자 이내로 입력하세요.")
    private String name;

    @NotBlank(message = "축종을 입력하세요.")
    @Size(max = 50, message = "축종은 50자 이내로 입력하세요.")
    private String animalType;

    @NotNull(message = "포장 무게를 입력하세요.")
    @Min(value = 1, message = "포장 무게는 1kg 이상이어야 합니다.")
    @Max(value = 9999, message = "포장 무게는 9999kg 이하로 입력하세요.")
    private Integer weightKg;

    @NotNull(message = "단가를 입력하세요.")
    @Min(value = 0, message = "단가는 0원 이상이어야 합니다.")
    private Long price;

    /** 최초 등록 시에만 반영된다. (수정 시에는 무시 - 재고는 입·출고로만 변경) */
    @NotNull(message = "초기 재고 수량을 입력하세요.")
    @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다.")
    private Integer totalStock;

    @NotNull(message = "안전 재고 수량을 입력하세요.")
    @Min(value = 0, message = "안전 재고 수량은 0 이상이어야 합니다.")
    private Integer safetyStock;

    /** 사용 여부 (등록 시 기본 true) */
    private boolean active = true;

    /** 수정 폼 렌더링용 */
    public static ProductForm from(Product product) {
        ProductForm form = new ProductForm();
        form.productId = product.getProductId();
        form.productCode = product.getProductCode();
        form.name = product.getName();
        form.animalType = product.getAnimalType();
        form.weightKg = product.getWeightKg();
        form.price = product.getPrice();
        form.totalStock = product.getTotalStock();
        form.safetyStock = product.getSafetyStock();
        form.active = product.isActive();
        return form;
    }
}
