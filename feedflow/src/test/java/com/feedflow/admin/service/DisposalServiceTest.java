package com.feedflow.admin.service;

import com.feedflow.admin.dto.DisposalForm;
import com.feedflow.admin.dto.DisposalResultDto;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.domain.DisposalReason;
import com.feedflow.domain.Inventory;
import com.feedflow.domain.MovementType;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
import com.feedflow.domain.StockMovement;
import com.feedflow.domain.WarehouseBin;
import com.feedflow.repository.InventoryRepository;
import com.feedflow.repository.ProductLotRepository;
import com.feedflow.repository.ProductRepository;
import com.feedflow.repository.StockMovementRepository;
import com.feedflow.repository.WarehouseBinRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 재고 폐기 로직 단위 테스트.
 * <p>
 * 폐기는 출고(FEFO)와 달리 <b>지정한 로트 × 구역의 재고만</b> 차감하며,
 * 구역 재고 → 로트 수량 → 품목 전체 재고가 모두 함께 줄어들어야 한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService 폐기 처리 단위 테스트")
class DisposalServiceTest {

    private static final Long INVENTORY_ID = 500L;
    private static final Long USER_ID = 1L;
    private static final String USER_NAME = "김책임";

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductLotRepository productLotRepository;
    @Mock
    private WarehouseBinRepository warehouseBinRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    @DisplayName("[폐기] 전량 폐기하면 구역 재고·로트 수량·품목 재고가 모두 차감된다")
    void dispose_allQuantity() {
        // given : 만료 로트 20개가 A-02-01 구역에 보관 중, 품목 전체 재고 100
        Product product = product(100);
        ProductLot lot = lot(product, 20, LocalDate.now().minusDays(5));   // 이미 만료
        Inventory inventory = inventory(lot, bin("A-02-01"), 20);

        given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(inventory));

        // when
        DisposalResultDto result = inventoryService.dispose(
                form(20, DisposalReason.EXPIRED, "폐기물 업체 수거"), USER_ID, USER_NAME);

        // then
        assertThat(inventory.getQuantity()).isZero();
        assertThat(lot.getLotQuantity()).isZero();
        assertThat(product.getTotalStock()).isEqualTo(80);        // 100 - 20

        assertThat(result.getQuantity()).isEqualTo(20);
        assertThat(result.getBinQuantityAfter()).isZero();
        assertThat(result.getLotQuantityAfter()).isZero();
        assertThat(result.getProductTotalStock()).isEqualTo(80);
        assertThat(result.isDepleted()).isTrue();
        assertThat(result.getReasonLabel()).isEqualTo("유통기한 경과");
    }

    @Test
    @DisplayName("[폐기] 일부만 폐기하면 남은 수량이 유지된다")
    void dispose_partialQuantity() {
        Product product = product(100);
        ProductLot lot = lot(product, 20, LocalDate.now().plusDays(10));
        Inventory inventory = inventory(lot, bin("A-01-01"), 20);

        given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(inventory));

        DisposalResultDto result = inventoryService.dispose(
                form(5, DisposalReason.DAMAGED, null), USER_ID, USER_NAME);

        assertThat(inventory.getQuantity()).isEqualTo(15);
        assertThat(lot.getLotQuantity()).isEqualTo(15);
        assertThat(product.getTotalStock()).isEqualTo(95);
        assertThat(result.isDepleted()).isFalse();
    }

    @Test
    @DisplayName("[폐기] 폐기 이력에 사유와 처리자가 DISPOSAL 타입으로 기록된다")
    void dispose_recordsDisposalMovement() {
        Product product = product(100);
        ProductLot lot = lot(product, 20, LocalDate.now().minusDays(3));
        WarehouseBin bin = bin("A-02-01");
        Inventory inventory = inventory(lot, bin, 20);

        given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(inventory));

        inventoryService.dispose(form(7, DisposalReason.CONTAMINATED, "변질 확인"), USER_ID, USER_NAME);

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());

        StockMovement movement = captor.getValue();
        assertThat(movement.getMovementType()).isEqualTo(MovementType.DISPOSAL);
        assertThat(movement.getMovementType().getSign())
                .as("폐기는 재고가 줄어드는 방향이어야 한다")
                .isEqualTo(-1);
        assertThat(movement.getQuantity()).isEqualTo(7);
        assertThat(movement.getReason()).isEqualTo(DisposalReason.CONTAMINATED);
        assertThat(movement.getMemo()).isEqualTo("변질 확인");
        assertThat(movement.getLot()).isSameAs(lot);
        assertThat(movement.getBin()).isSameAs(bin);
        assertThat(movement.getUserName()).isEqualTo(USER_NAME);
    }

    @Test
    @DisplayName("[폐기] 보관 수량보다 많이 폐기하면 예외가 발생하고 아무것도 차감되지 않는다")
    void dispose_exceedsStoredQuantity_throwsException() {
        Product product = product(100);
        ProductLot lot = lot(product, 20, LocalDate.now().minusDays(5));
        Inventory inventory = inventory(lot, bin("A-02-01"), 20);

        given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.dispose(
                form(25, DisposalReason.EXPIRED, null), USER_ID, USER_NAME))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("보관 수량보다 많이 폐기할 수 없습니다");

        // 원본 유지 (트랜잭션 롤백 대상)
        assertThat(inventory.getQuantity()).isEqualTo(20);
        assertThat(lot.getLotQuantity()).isEqualTo(20);
        assertThat(product.getTotalStock()).isEqualTo(100);
        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test
    @DisplayName("[폐기] 폐기 수량이 0 이하면 조회조차 하지 않고 예외가 발생한다")
    void dispose_invalidQuantity_throwsException() {
        assertThatThrownBy(() -> inventoryService.dispose(
                form(0, DisposalReason.EXPIRED, null), USER_ID, USER_NAME))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("1 이상");

        verify(inventoryRepository, never()).findWithDetailById(any());
    }

    @Test
    @DisplayName("[폐기] 존재하지 않는 재고를 폐기하면 ResourceNotFoundException 이 발생한다")
    void dispose_notFound_throwsException() {
        given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.dispose(
                form(5, DisposalReason.EXPIRED, null), USER_ID, USER_NAME))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("폐기 대상 재고를 찾을 수 없습니다");
    }

    /* ------------------------------------------------------------------
     * 픽스처
     * ------------------------------------------------------------------ */

    private DisposalForm form(int quantity, DisposalReason reason, String memo) {
        DisposalForm form = new DisposalForm();
        form.setInventoryId(INVENTORY_ID);
        form.setQuantity(quantity);
        form.setReason(reason);
        form.setMemo(memo);
        return form;
    }

    private Product product(int totalStock) {
        return Product.builder()
                .productId(3L)
                .productCode("FD-CK-001")
                .name("산란계 전용 배합사료")
                .animalType("닭")
                .weightKg(25)
                .price(24000L)
                .totalStock(totalStock)
                .safetyStock(120)
                .shelfLifeDays(90)
                .active(true)
                .build();
    }

    private ProductLot lot(Product product, int lotQuantity, LocalDate expirationDate) {
        return ProductLot.builder()
                .lotId(15L)
                .product(product)
                .lotNo("LOT-CK-2620")
                .manufacturedDate(expirationDate.minusDays(90))
                .expirationDate(expirationDate)
                .lotQuantity(lotQuantity)
                .build();
    }

    private WarehouseBin bin(String binCode) {
        return WarehouseBin.builder()
                .binId(3L)
                .binCode(binCode)
                .zone("A")
                .rack("02")
                .binLevel(1)
                .maxCapacity(400)
                .active(true)
                .build();
    }

    private Inventory inventory(ProductLot lot, WarehouseBin bin, int quantity) {
        return Inventory.builder()
                .inventoryId(INVENTORY_ID)
                .lot(lot)
                .bin(bin)
                .quantity(quantity)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
