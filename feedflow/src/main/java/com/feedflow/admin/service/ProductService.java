package com.feedflow.admin.service;

import com.feedflow.common.util.Texts;
import com.feedflow.admin.dto.ProductDto;
import com.feedflow.admin.dto.ProductForm;
import com.feedflow.common.exception.DuplicateCodeException;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.domain.Product;
import com.feedflow.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 품목(기준 정보) 관리 서비스.
 * <p>
 * 품목 코드는 업무 식별자이므로 등록/수정 시 중복을 허용하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    /* ------------------------------------------------------------------
     * 조회
     * ------------------------------------------------------------------ */

    /** 품목 목록 검색 (빈 문자열 조건은 무시) */
    public Page<ProductDto> getProducts(String keyword,
                                        String animalType,
                                        Boolean active,
                                        Pageable pageable) {
        return productRepository
                .search(Texts.trimToNull(keyword), Texts.trimToNull(animalType), active, pageable)
                .map(ProductDto::from);
    }

    public ProductDto getProduct(Long productId) {
        return ProductDto.from(findProduct(productId));
    }

    /** 수정 폼 렌더링용 */
    public ProductForm getProductForm(Long productId) {
        return ProductForm.from(findProduct(productId));
    }

    /** 검색 필터용 축종 목록 */
    public List<String> getAnimalTypes() {
        return productRepository.findDistinctAnimalTypes();
    }

    /** 입고 화면 등의 품목 선택 목록 (사용 중인 품목만) */
    public List<ProductDto> getActiveProducts() {
        return productRepository.findByActiveTrueOrderByProductCodeAsc().stream()
                .map(ProductDto::from)
                .toList();
    }

    /* ------------------------------------------------------------------
     * 등록 / 수정
     * ------------------------------------------------------------------ */

    /**
     * 품목 등록.
     *
     * @return 생성된 품목 ID
     * @throws DuplicateCodeException 품목 코드가 이미 존재하는 경우
     */
    @Transactional
    public Long create(ProductForm form) {
        String productCode = Texts.code(form.getProductCode());

        if (productRepository.existsByProductCode(productCode)) {
            throw DuplicateCodeException.ofProductCode(productCode);
        }

        Product product = Product.builder()
                .productCode(productCode)
                .name(Texts.trim(form.getName()))
                .animalType(Texts.trim(form.getAnimalType()))
                .weightKg(form.getWeightKg())
                .price(form.getPrice())
                .totalStock(form.getTotalStock())
                .safetyStock(form.getSafetyStock())
                .shelfLifeDays(form.getShelfLifeDays())
                .active(form.isActive())
                .build();

        return productRepository.save(product).getProductId();
    }

    /**
     * 품목 수정.
     * 재고(totalStock)는 입·출고 트랜잭션으로만 변경되므로 여기서 반영하지 않는다.
     *
     * @throws ResourceNotFoundException 품목이 존재하지 않는 경우
     * @throws DuplicateCodeException    변경한 품목 코드가 다른 품목에서 이미 사용 중인 경우
     */
    @Transactional
    public void update(Long productId, ProductForm form) {
        Product product = findProduct(productId);
        String productCode = Texts.code(form.getProductCode());

        if (productRepository.existsByProductCodeAndProductIdNot(productCode, productId)) {
            throw DuplicateCodeException.ofProductCode(productCode);
        }

        product.updateMasterData(
                productCode,
                Texts.trim(form.getName()),
                Texts.trim(form.getAnimalType()),
                form.getWeightKg(),
                form.getPrice(),
                form.getSafetyStock(),
                form.getShelfLifeDays());
        product.changeActive(form.isActive());
    }

    /**
     * 사용 여부 변경 (사용 중지 / 재사용).
     * 주문·로트 이력이 참조하므로 물리 삭제하지 않는다.
     */
    @Transactional
    public String changeActive(Long productId, boolean active) {
        Product product = findProduct(productId);
        product.changeActive(active);
        return product.getName();
    }

    /* ------------------------------------------------------------------
     * 내부 헬퍼
     * ------------------------------------------------------------------ */

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> ResourceNotFoundException.ofProduct(productId));
    }
}
