package com.feedflow.admin.service;

import com.feedflow.admin.dto.ProductForm;
import com.feedflow.common.exception.DuplicateCodeException;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.domain.Product;
import com.feedflow.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 품목(기준 정보) 서비스 단위 테스트.
 * <p>
 * DB / Spring 컨텍스트 없이 Repository 를 Mock 으로 대체하여
 * "품목 코드 중복 방지" 비즈니스 규칙과 예외 처리를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService 단위 테스트")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    /* ==================================================================
     * 등록 - 코드 중복 방지
     * ================================================================== */

    @Test
    @DisplayName("[등록] 중복되지 않은 코드로 등록하면 저장되고 생성된 ID를 반환한다")
    void create_success() {
        // given
        ProductForm form = productForm("FD-CT-010", "번식우 유지 배합사료");
        given(productRepository.existsByProductCode("FD-CT-010")).willReturn(false);
        given(productRepository.save(any(Product.class))).willReturn(product(100L, "FD-CT-010"));

        // when
        Long productId = productService.create(form);

        // then
        assertThat(productId).isEqualTo(100L);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());

        Product saved = captor.getValue();
        assertThat(saved.getProductCode()).isEqualTo("FD-CT-010");
        assertThat(saved.getName()).isEqualTo("번식우 유지 배합사료");
        assertThat(saved.getAnimalType()).isEqualTo("소");
        assertThat(saved.getWeightKg()).isEqualTo(25);
        assertThat(saved.getPrice()).isEqualTo(32000L);
        assertThat(saved.getTotalStock()).isEqualTo(100);
        assertThat(saved.getSafetyStock()).isEqualTo(50);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    @DisplayName("[등록] 이미 존재하는 품목 코드로 등록하면 DuplicateCodeException 이 발생하고 저장되지 않는다")
    void create_duplicateCode_throwsException() {
        // given
        ProductForm form = productForm("FD-CT-001", "프리미엄 육성우 배합사료");
        given(productRepository.existsByProductCode("FD-CT-001")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> productService.create(form))
                .isInstanceOf(DuplicateCodeException.class)
                .hasMessageContaining("이미 등록된 품목 코드입니다")
                .hasMessageContaining("FD-CT-001");

        // 중복이면 저장 로직을 절대 타지 않아야 한다
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("[등록] 공백/소문자가 섞인 코드는 대문자로 정규화한 뒤 중복 검사와 저장을 수행한다")
    void create_normalizesCodeBeforeDuplicateCheck() {
        // given : 사용자가 "  fd-ct-010  " 처럼 입력한 상황
        ProductForm form = productForm("  fd-ct-010  ", "번식우 유지 배합사료");
        given(productRepository.existsByProductCode("FD-CT-010")).willReturn(false);
        given(productRepository.save(any(Product.class))).willReturn(product(101L, "FD-CT-010"));

        // when
        productService.create(form);

        // then : 정규화된 코드로 중복 검사를 해야 대소문자만 다른 중복 등록을 막을 수 있다
        verify(productRepository).existsByProductCode("FD-CT-010");

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getProductCode()).isEqualTo("FD-CT-010");
    }

    @Test
    @DisplayName("[등록] 대소문자만 다른 코드도 중복으로 판단하여 예외가 발생한다")
    void create_caseInsensitiveDuplicate_throwsException() {
        // given : DB 에는 FD-CT-001 이 이미 존재
        ProductForm form = productForm("fd-ct-001", "중복 품목");
        given(productRepository.existsByProductCode("FD-CT-001")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> productService.create(form))
                .isInstanceOf(DuplicateCodeException.class);

        verify(productRepository, never()).save(any(Product.class));
    }

    /* ==================================================================
     * 수정
     * ================================================================== */

    @Test
    @DisplayName("[수정] 코드/품목명/안전재고는 변경되지만 재고 수량은 변경되지 않는다")
    void update_success_doesNotChangeTotalStock() {
        // given
        Product target = product(1L, "FD-CT-001");
        given(productRepository.findById(1L)).willReturn(Optional.of(target));
        given(productRepository.existsByProductCodeAndProductIdNot("FD-CT-002", 1L)).willReturn(false);

        ProductForm form = productForm("FD-CT-002", "이름 변경됨");
        form.setSafetyStock(999);
        form.setTotalStock(0);   // 재고를 0으로 바꾸려 해도 무시되어야 한다

        // when
        productService.update(1L, form);

        // then
        assertThat(target.getProductCode()).isEqualTo("FD-CT-002");
        assertThat(target.getName()).isEqualTo("이름 변경됨");
        assertThat(target.getSafetyStock()).isEqualTo(999);
        assertThat(target.getTotalStock())
                .as("재고는 입·출고 처리로만 변경되어야 한다")
                .isEqualTo(100);
    }

    @Test
    @DisplayName("[수정] 다른 품목이 사용 중인 코드로 변경하면 DuplicateCodeException 이 발생한다")
    void update_duplicateCode_throwsException() {
        // given
        Product target = product(1L, "FD-CT-001");
        given(productRepository.findById(1L)).willReturn(Optional.of(target));
        given(productRepository.existsByProductCodeAndProductIdNot("FD-PG-001", 1L)).willReturn(true);

        ProductForm form = productForm("FD-PG-001", "코드 충돌 시도");

        // when & then
        assertThatThrownBy(() -> productService.update(1L, form))
                .isInstanceOf(DuplicateCodeException.class)
                .hasMessageContaining("FD-PG-001");

        // 예외가 발생했으므로 기존 값이 유지되어야 한다
        assertThat(target.getProductCode()).isEqualTo("FD-CT-001");
        assertThat(target.getName()).isEqualTo("프리미엄 육성우 배합사료");
    }

    @Test
    @DisplayName("[수정] 자기 자신의 코드를 그대로 유지하면 중복으로 판단하지 않는다")
    void update_sameCode_isNotDuplicate() {
        // given
        Product target = product(1L, "FD-CT-001");
        given(productRepository.findById(1L)).willReturn(Optional.of(target));
        given(productRepository.existsByProductCodeAndProductIdNot("FD-CT-001", 1L)).willReturn(false);

        ProductForm form = productForm("FD-CT-001", "이름만 변경");

        // when
        productService.update(1L, form);

        // then
        assertThat(target.getName()).isEqualTo("이름만 변경");
        assertThat(target.getProductCode()).isEqualTo("FD-CT-001");
    }

    @Test
    @DisplayName("[수정] 존재하지 않는 품목을 수정하면 ResourceNotFoundException 이 발생한다")
    void update_notFound_throwsException() {
        // given
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> productService.update(999L, productForm("FD-XX-001", "없는 품목")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("존재하지 않는 품목");

        // 대상이 없으므로 중복 검사까지 진행하지 않는다
        verify(productRepository, never()).existsByProductCodeAndProductIdNot(any(), anyLong());
    }

    /* ==================================================================
     * 조회 / 사용 여부 변경
     * ================================================================== */

    @Test
    @DisplayName("[조회] 존재하지 않는 품목을 조회하면 ResourceNotFoundException 이 발생한다")
    void getProduct_notFound_throwsException() {
        // given
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> productService.getProduct(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("[사용여부] 사용 중지하면 물리 삭제 없이 active 만 false 로 변경된다")
    void changeActive_deactivate() {
        // given
        Product target = product(1L, "FD-CT-001");
        given(productRepository.findById(1L)).willReturn(Optional.of(target));

        // when
        String name = productService.changeActive(1L, false);

        // then
        assertThat(target.isActive()).isFalse();
        assertThat(name).isEqualTo("프리미엄 육성우 배합사료");

        // 주문·로트 이력이 참조하므로 물리 삭제해서는 안 된다
        verify(productRepository, never()).delete(any(Product.class));
        verify(productRepository, never()).deleteById(anyLong());
    }

    /* ==================================================================
     * 픽스처
     * ================================================================== */

    private ProductForm productForm(String productCode, String name) {
        ProductForm form = new ProductForm();
        form.setProductCode(productCode);
        form.setName(name);
        form.setAnimalType("소");
        form.setWeightKg(25);
        form.setPrice(32000L);
        form.setTotalStock(100);
        form.setSafetyStock(50);
        form.setActive(true);
        return form;
    }

    private Product product(Long productId, String productCode) {
        return Product.builder()
                .productId(productId)
                .productCode(productCode)
                .name("프리미엄 육성우 배합사료")
                .animalType("소")
                .weightKg(25)
                .price(32000L)
                .totalStock(100)
                .safetyStock(50)
                .active(true)
                .build();
    }
}
