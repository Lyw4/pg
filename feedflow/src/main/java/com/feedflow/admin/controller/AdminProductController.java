package com.feedflow.admin.controller;

import com.feedflow.admin.dto.ProductDto;
import com.feedflow.admin.dto.ProductForm;
import com.feedflow.admin.service.ProductService;
import com.feedflow.common.exception.DuplicateCodeException;
import com.feedflow.domain.AnimalType;
import com.feedflow.domain.ProductType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 기준 정보 - 품목(Product) 관리 화면 컨트롤러.
 * <p>
 * 조회/등록/수정은 STAFF·ADMIN 모두 가능하고, 사용 중지(삭제 대체)는 ADMIN 전용이다.
 */
@Controller
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private static final int PAGE_SIZE = 10;
    private static final String LIST_VIEW = "admin/products/list";
    private static final String FORM_VIEW = "admin/products/form";

    private final ProductService productService;

    /** 검색 필터 / 등록 폼용 축종 목록 (이 컨트롤러의 모든 화면 공통) */
    @ModelAttribute("animalTypes")
    public List<AnimalType> animalTypes() {
        return productService.getAnimalTypes();
    }

    /** 검색 필터 / 등록 폼용 품목 구분 목록 (사료 / 영양제) */
    @ModelAttribute("productTypes")
    public List<ProductType> productTypes() {
        return productService.getProductTypes();
    }

    @ModelAttribute("menu")
    public String menu() {
        return "products";
    }

    /* ------------------------------------------------------------------
     * 목록
     * ------------------------------------------------------------------ */

    @GetMapping
    public String list(@RequestParam(name = "keyword", required = false) String keyword,
                       @RequestParam(name = "animalType", required = false) AnimalType animalType,
                       @RequestParam(name = "productType", required = false) ProductType productType,
                       @RequestParam(name = "active", required = false) Boolean active,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       Model model) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("productCode").ascending());
        Page<ProductDto> products =
                productService.getProducts(keyword, animalType, productType, active, pageable);

        model.addAttribute("products", products);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedAnimalType", animalType);
        model.addAttribute("selectedProductType", productType);
        model.addAttribute("selectedActive", active);
        return LIST_VIEW;
    }

    /* ------------------------------------------------------------------
     * 등록
     * ------------------------------------------------------------------ */

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("productForm", new ProductForm());
        prepareForm(model, false, null);
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("productForm") ProductForm productForm,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        prepareForm(model, false, null);

        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }

        try {
            productService.create(productForm);
        } catch (DuplicateCodeException e) {
            bindingResult.rejectValue("productCode", "duplicate", e.getMessage());
            return FORM_VIEW;
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "품목 [" + productForm.getProductCode() + "] 을 등록했습니다.");
        return "redirect:/admin/products";
    }

    /* ------------------------------------------------------------------
     * 수정
     * ------------------------------------------------------------------ */

    @GetMapping("/{productId}/edit")
    public String editForm(@PathVariable("productId") Long productId, Model model) {
        model.addAttribute("productForm", productService.getProductForm(productId));
        prepareForm(model, true, productId);
        return FORM_VIEW;
    }

    @PostMapping("/{productId}")
    public String update(@PathVariable("productId") Long productId,
                         @Valid @ModelAttribute("productForm") ProductForm productForm,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        prepareForm(model, true, productId);
        productForm.setProductId(productId);

        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }

        try {
            productService.update(productId, productForm);
        } catch (DuplicateCodeException e) {
            bindingResult.rejectValue("productCode", "duplicate", e.getMessage());
            return FORM_VIEW;
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "품목 [" + productForm.getProductCode() + "] 을 수정했습니다.");
        return "redirect:/admin/products";
    }

    /**
     * 폼 화면 공통 모델 세팅.
     * 등록/수정 모드에 따라 form 의 action URL 을 서버에서 결정한다.
     */
    private void prepareForm(Model model, boolean editMode, Long productId) {
        model.addAttribute("editMode", editMode);
        model.addAttribute("formAction",
                editMode ? "/admin/products/" + productId : "/admin/products");
    }

    /* ------------------------------------------------------------------
     * 사용 중지 / 재사용 (물리 삭제 대신 사용 - 주문·로트 이력 보존)
     * ------------------------------------------------------------------ */

    @PostMapping("/{productId}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public String changeActive(@PathVariable("productId") Long productId,
                              @RequestParam("active") boolean active,
                              RedirectAttributes redirectAttributes) {

        String name = productService.changeActive(productId, active);
        redirectAttributes.addFlashAttribute("successMessage",
                name + " 품목을 " + (active ? "다시 사용" : "사용 중지") + " 처리했습니다.");
        return "redirect:/admin/products";
    }
}
