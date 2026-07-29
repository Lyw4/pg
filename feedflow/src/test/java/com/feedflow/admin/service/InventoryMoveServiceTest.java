package com.feedflow.admin.service;

import com.feedflow.admin.dto.StockMoveForm;
import com.feedflow.admin.dto.StockMoveResultDto;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.domain.AnimalType;
import com.feedflow.domain.BinPurpose;
import com.feedflow.domain.Inventory;
import com.feedflow.domain.MovementType;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
import com.feedflow.domain.ProductType;
import com.feedflow.domain.StockMovement;
import com.feedflow.domain.Warehouse;
import com.feedflow.domain.WarehouseBin;
import com.feedflow.repository.InventoryRepository;
import com.feedflow.repository.StockMovementRepository;
import com.feedflow.repository.WarehouseBinRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * 구역 간 재고 이동 단위 테스트.
 * <p>
 * 가장 중요한 검증은 <b>총 재고가 변하지 않는다</b>는 것이다.
 * 이동은 위치만 바꾸므로 {@code ProductLot.lotQuantity} 와 {@code Product.totalStock} 이
 * 이동 전후로 같아야 한다. 이 값을 함께 조정하면 재고가 이중 계상된다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryMoveService 단위 테스트")
class InventoryMoveServiceTest {

    private static final Long INVENTORY_ID = 100L;
    private static final Long USER_ID = 1L;
    private static final String USER_NAME = "김책임";

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private WarehouseBinRepository warehouseBinRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @InjectMocks
    private InventoryMoveService inventoryMoveService;

    /* ==================================================================
     * 정상 이동
     * ================================================================== */

    @Nested
    @DisplayName("정상 이동")
    class Move {

        @Test
        @DisplayName("일부 수량을 옮기면 두 구역의 재고만 바뀌고 총 재고는 그대로다")
        void movesPartialQuantity() {
            Product product = product(200);
            ProductLot lot = lot(lotId(), product, 150);       // 로트 잔여 150
            WarehouseBin fromBin = bin(1L, "A-01", 600, true);
            WarehouseBin toBin = bin(2L, "B-02", 600, true);

            Inventory source = inventory(INVENTORY_ID, lot, fromBin, 100);

            given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(source));
            given(warehouseBinRepository.findById(2L)).willReturn(Optional.of(toBin));
            given(inventoryRepository.findByLot_LotIdAndBin_BinId(lot.getLotId(), 2L))
                    .willReturn(Optional.empty());
            given(inventoryRepository.sumQuantityByBinId(2L)).willReturn(50L);
            given(inventoryRepository.save(any(Inventory.class)))
                    .willAnswer(call -> call.getArgument(0));

            StockMoveResultDto result =
                    inventoryMoveService.move(form(INVENTORY_ID, 2L, 30), USER_ID, USER_NAME);

            // 출발 구역은 줄어든다
            assertThat(source.getQuantity()).isEqualTo(70);

            // 총 재고는 변하지 않는다 (핵심)
            assertThat(lot.getLotQuantity())
                    .as("이동은 위치만 바꾸므로 로트 잔여는 그대로여야 한다")
                    .isEqualTo(150);
            assertThat(product.getTotalStock())
                    .as("품목 총 재고도 그대로여야 한다")
                    .isEqualTo(200);

            assertThat(result.getMovedQuantity()).isEqualTo(30);
            assertThat(result.getFromQuantityBefore()).isEqualTo(100);
            assertThat(result.getFromQuantityAfter()).isEqualTo(70);
            assertThat(result.getToQuantityBefore()).isZero();
            assertThat(result.getToQuantityAfter()).isEqualTo(30);
            assertThat(result.getLotQuantity()).isEqualTo(150);
            assertThat(result.getProductTotalStock()).isEqualTo(200);
            assertThat(result.isSourceDepleted()).isFalse();
        }

        @Test
        @DisplayName("도착 구역에 같은 로트가 이미 있으면 새 행을 만들지 않고 합산한다")
        void mergesIntoExistingRow() {
            Product product = product(200);
            ProductLot lot = lot(lotId(), product, 150);
            WarehouseBin fromBin = bin(1L, "A-01", 600, true);
            WarehouseBin toBin = bin(2L, "B-02", 600, true);

            Inventory source = inventory(INVENTORY_ID, lot, fromBin, 100);
            Inventory target = inventory(101L, lot, toBin, 40);

            given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(source));
            given(warehouseBinRepository.findById(2L)).willReturn(Optional.of(toBin));
            given(inventoryRepository.findByLot_LotIdAndBin_BinId(lot.getLotId(), 2L))
                    .willReturn(Optional.of(target));
            given(inventoryRepository.sumQuantityByBinId(2L)).willReturn(40L);

            StockMoveResultDto result =
                    inventoryMoveService.move(form(INVENTORY_ID, 2L, 25), USER_ID, USER_NAME);

            assertThat(source.getQuantity()).isEqualTo(75);
            assertThat(target.getQuantity()).isEqualTo(65);
            assertThat(result.isTargetCreated()).isFalse();

            // 두 구역 합계는 이동 전(100 + 40)과 같다
            assertThat(source.getQuantity() + target.getQuantity()).isEqualTo(140);

            verify(inventoryRepository, never()).save(any(Inventory.class));
        }

        @Test
        @DisplayName("전량을 옮기면 출발 구역 재고가 0 이 되고 행은 남는다")
        void movesAllQuantity() {
            // 재고 행을 삭제하지 않는 것이 이 프로젝트의 기존 정책이다.
            // (출고·폐기로 0 이 되어도 행을 지우지 않는다)
            Product product = product(200);
            ProductLot lot = lot(lotId(), product, 150);
            WarehouseBin fromBin = bin(1L, "A-01", 600, true);
            WarehouseBin toBin = bin(2L, "B-02", 600, true);

            Inventory source = inventory(INVENTORY_ID, lot, fromBin, 60);

            given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(source));
            given(warehouseBinRepository.findById(2L)).willReturn(Optional.of(toBin));
            given(inventoryRepository.findByLot_LotIdAndBin_BinId(lot.getLotId(), 2L))
                    .willReturn(Optional.empty());
            given(inventoryRepository.sumQuantityByBinId(2L)).willReturn(0L);
            given(inventoryRepository.save(any(Inventory.class)))
                    .willAnswer(call -> call.getArgument(0));

            StockMoveResultDto result =
                    inventoryMoveService.move(form(INVENTORY_ID, 2L, 60), USER_ID, USER_NAME);

            assertThat(source.getQuantity()).isZero();
            assertThat(result.isSourceDepleted()).isTrue();
            assertThat(result.isTargetCreated()).isTrue();
            assertThat(lot.getLotQuantity()).isEqualTo(150);
        }

        @Test
        @DisplayName("사용 중지된 구역에서 빼내는 것은 허용한다")
        void allowsMovingOutOfInactiveBin() {
            // 구역을 비우는 작업이 사용 중지의 목적이므로 이를 막으면 재고가 갇힌다.
            Product product = product(200);
            ProductLot lot = lot(lotId(), product, 150);
            WarehouseBin inactiveFrom = bin(1L, "A-01", 600, false);
            WarehouseBin toBin = bin(2L, "B-02", 600, true);

            Inventory source = inventory(INVENTORY_ID, lot, inactiveFrom, 80);

            given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(source));
            given(warehouseBinRepository.findById(2L)).willReturn(Optional.of(toBin));
            given(inventoryRepository.findByLot_LotIdAndBin_BinId(lot.getLotId(), 2L))
                    .willReturn(Optional.empty());
            given(inventoryRepository.sumQuantityByBinId(2L)).willReturn(0L);
            given(inventoryRepository.save(any(Inventory.class)))
                    .willAnswer(call -> call.getArgument(0));

            StockMoveResultDto result =
                    inventoryMoveService.move(form(INVENTORY_ID, 2L, 80), USER_ID, USER_NAME);

            assertThat(result.getMovedQuantity()).isEqualTo(80);
            assertThat(source.getQuantity()).isZero();
        }
    }

    /* ==================================================================
     * 이력 기록
     * ================================================================== */

    @Nested
    @DisplayName("이동 이력")
    class Movement {

        @Test
        @DisplayName("MOVE 유형으로 출발지와 도착지를 함께 남긴다")
        void recordsMoveMovementWithBothBins() {
            Product product = product(200);
            ProductLot lot = lot(lotId(), product, 150);
            WarehouseBin fromBin = bin(1L, "A-01", 600, true);
            WarehouseBin toBin = bin(2L, "B-02", 600, true);

            Inventory source = inventory(INVENTORY_ID, lot, fromBin, 100);

            given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(source));
            given(warehouseBinRepository.findById(2L)).willReturn(Optional.of(toBin));
            given(inventoryRepository.findByLot_LotIdAndBin_BinId(lot.getLotId(), 2L))
                    .willReturn(Optional.empty());
            given(inventoryRepository.sumQuantityByBinId(2L)).willReturn(0L);
            given(inventoryRepository.save(any(Inventory.class)))
                    .willAnswer(call -> call.getArgument(0));

            StockMoveForm form = form(INVENTORY_ID, 2L, 30);
            form.setMemo("저온 구역으로 재배치");

            inventoryMoveService.move(form, USER_ID, USER_NAME);

            ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
            verify(stockMovementRepository).save(captor.capture());

            StockMovement saved = captor.getValue();
            assertThat(saved.getMovementType())
                    .as("입고가 아니라 MOVE 로 남아야 매입 실적이 오염되지 않는다")
                    .isEqualTo(MovementType.MOVE);
            assertThat(saved.getFromBin().getBinCode()).isEqualTo("A-01");
            assertThat(saved.getBin().getBinCode())
                    .as("bin 은 도착지를 가리킨다")
                    .isEqualTo("B-02");
            assertThat(saved.getQuantity()).isEqualTo(30);
            assertThat(saved.getLot()).isEqualTo(lot);
            assertThat(saved.getProduct()).isEqualTo(product);
            assertThat(saved.getMemo()).isEqualTo("저온 구역으로 재배치");
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getUserName()).isEqualTo(USER_NAME);
            assertThat(saved.getOrderId())
                    .as("주문과 무관한 창고 내부 작업이다")
                    .isNull();
        }

        @Test
        @DisplayName("MOVE 는 재고 증감 방향이 0 이라 이력 누적에 영향을 주지 않는다")
        void moveSignIsZero() {
            // 이력 추적 뷰어가 잔여 수량을 누적할 때 이동이 총량을 바꾸면 안 된다.
            assertThat(MovementType.MOVE.getSign()).isZero();
        }
    }

    /* ==================================================================
     * 거부 규칙
     * ================================================================== */

    @Nested
    @DisplayName("이동할 수 없는 경우")
    class Reject {

        @Test
        @DisplayName("출발지와 도착지가 같으면 거부한다")
        void sameBin_throwsException() {
            Product product = product(200);
            ProductLot lot = lot(lotId(), product, 150);
            WarehouseBin bin = bin(1L, "A-01", 600, true);
            Inventory source = inventory(INVENTORY_ID, lot, bin, 100);

            given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(source));
            given(warehouseBinRepository.findById(1L)).willReturn(Optional.of(bin));

            assertThatThrownBy(() ->
                    inventoryMoveService.move(form(INVENTORY_ID, 1L, 10), USER_ID, USER_NAME))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("출발 구역과 도착 구역이 같습니다");

            assertThat(source.getQuantity()).isEqualTo(100);
            verify(stockMovementRepository, never()).save(any(StockMovement.class));
        }

        @Test
        @DisplayName("보관 수량보다 많이 옮기려 하면 거부한다")
        void exceedsStoredQuantity_throwsException() {
            Product product = product(200);
            ProductLot lot = lot(lotId(), product, 150);
            WarehouseBin fromBin = bin(1L, "A-01", 600, true);
            WarehouseBin toBin = bin(2L, "B-02", 600, true);
            Inventory source = inventory(INVENTORY_ID, lot, fromBin, 50);

            given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(source));
            given(warehouseBinRepository.findById(2L)).willReturn(Optional.of(toBin));

            assertThatThrownBy(() ->
                    inventoryMoveService.move(form(INVENTORY_ID, 2L, 51), USER_ID, USER_NAME))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("보관 수량보다 많이 이동할 수 없습니다");

            assertThat(source.getQuantity())
                    .as("예외로 롤백되므로 재고는 그대로여야 한다")
                    .isEqualTo(50);
        }

        @Test
        @DisplayName("사용 중지된 구역으로는 이동할 수 없다")
        void inactiveTargetBin_throwsException() {
            Product product = product(200);
            ProductLot lot = lot(lotId(), product, 150);
            WarehouseBin fromBin = bin(1L, "A-01", 600, true);
            WarehouseBin inactiveTo = bin(2L, "B-02", 600, false);
            Inventory source = inventory(INVENTORY_ID, lot, fromBin, 100);

            given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(source));
            given(warehouseBinRepository.findById(2L)).willReturn(Optional.of(inactiveTo));

            assertThatThrownBy(() ->
                    inventoryMoveService.move(form(INVENTORY_ID, 2L, 10), USER_ID, USER_NAME))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("사용 중지된 구역으로는 이동할 수 없습니다");
        }

        @Test
        @DisplayName("도착 구역의 적재 한도를 넘으면 거부한다")
        void exceedsCapacity_throwsException() {
            Product product = product(200);
            ProductLot lot = lot(lotId(), product, 150);
            WarehouseBin fromBin = bin(1L, "A-01", 600, true);
            WarehouseBin toBin = bin(2L, "B-02", 100, true);   // 한도 100
            Inventory source = inventory(INVENTORY_ID, lot, fromBin, 100);

            given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(source));
            given(warehouseBinRepository.findById(2L)).willReturn(Optional.of(toBin));
            given(inventoryRepository.sumQuantityByBinId(2L)).willReturn(80L);  // 이미 80

            // 80 + 30 = 110 > 100
            assertThatThrownBy(() ->
                    inventoryMoveService.move(form(INVENTORY_ID, 2L, 30), USER_ID, USER_NAME))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("적재 한도를 초과합니다");

            assertThat(source.getQuantity()).isEqualTo(100);
            verify(stockMovementRepository, never()).save(any(StockMovement.class));
        }

        @Test
        @DisplayName("적재 한도와 정확히 같은 수량은 허용한다 (경계값)")
        void exactlyAtCapacity_isAllowed() {
            Product product = product(200);
            ProductLot lot = lot(lotId(), product, 150);
            WarehouseBin fromBin = bin(1L, "A-01", 600, true);
            WarehouseBin toBin = bin(2L, "B-02", 100, true);
            Inventory source = inventory(INVENTORY_ID, lot, fromBin, 100);

            given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(source));
            given(warehouseBinRepository.findById(2L)).willReturn(Optional.of(toBin));
            given(inventoryRepository.findByLot_LotIdAndBin_BinId(lot.getLotId(), 2L))
                    .willReturn(Optional.empty());
            given(inventoryRepository.sumQuantityByBinId(2L)).willReturn(80L);
            given(inventoryRepository.save(any(Inventory.class)))
                    .willAnswer(call -> call.getArgument(0));

            // 80 + 20 = 100 = 한도
            StockMoveResultDto result =
                    inventoryMoveService.move(form(INVENTORY_ID, 2L, 20), USER_ID, USER_NAME);

            assertThat(result.getMovedQuantity()).isEqualTo(20);
            assertThat(result.getToRemainingCapacity())
                    .as("한도를 꽉 채웠으므로 여유는 0")
                    .isZero();
        }

        @Test
        @DisplayName("이동 수량이 0 이하면 거부한다")
        void nonPositiveQuantity_throwsException() {
            assertThatThrownBy(() ->
                    inventoryMoveService.move(form(INVENTORY_ID, 2L, 0), USER_ID, USER_NAME))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("이동 수량은 1 이상이어야 합니다");

            verify(inventoryRepository, never()).findWithDetailById(any());
        }

        @Test
        @DisplayName("존재하지 않는 재고면 ResourceNotFoundException 이 발생한다")
        void inventoryNotFound_throwsException() {
            given(inventoryRepository.findWithDetailById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    inventoryMoveService.move(form(999L, 2L, 10), USER_ID, USER_NAME))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("이동할 재고를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("존재하지 않는 도착 구역이면 ResourceNotFoundException 이 발생한다")
        void targetBinNotFound_throwsException() {
            Product product = product(200);
            ProductLot lot = lot(lotId(), product, 150);
            WarehouseBin fromBin = bin(1L, "A-01", 600, true);
            Inventory source = inventory(INVENTORY_ID, lot, fromBin, 100);

            given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(source));
            given(warehouseBinRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    inventoryMoveService.move(form(INVENTORY_ID, 999L, 10), USER_ID, USER_NAME))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    /* ==================================================================
     * 픽스처
     * ================================================================== */

    private Long lotId() {
        return 10L;
    }

    private StockMoveForm form(Long inventoryId, Long targetBinId, int quantity) {
        StockMoveForm form = new StockMoveForm();
        form.setInventoryId(inventoryId);
        form.setTargetBinId(targetBinId);
        form.setQuantity(quantity);
        return form;
    }

    private Product product(int totalStock) {
        return Product.builder()
                .productId(1L)
                .productCode("FD-CT-001")
                .name("프리미엄 육성우 배합사료")
                .animalType(AnimalType.CATTLE)
                .productType(ProductType.FEED)
                .weightKg(25)
                .price(32000L)
                .totalStock(totalStock)
                .safetyStock(10)
                .shelfLifeDays(180)
                .active(true)
                .build();
    }

    private ProductLot lot(Long lotId, Product product, int lotQuantity) {
        return ProductLot.builder()
                .lotId(lotId)
                .product(product)
                .lotNo("LOT-CT-2601")
                .manufacturedDate(LocalDate.now().minusDays(30))
                .expirationDate(LocalDate.now().plusDays(150))
                .lotQuantity(lotQuantity)
                .build();
    }

    private WarehouseBin bin(Long binId, String binCode, int maxCapacity, boolean active) {
        return WarehouseBin.builder()
                .binId(binId)
                .binCode(binCode)
                .warehouse(Warehouse.WH1)
                .zone(binCode.substring(0, 1))
                .binPurpose(BinPurpose.STORAGE)
                .rack("01")
                .binLevel(1)
                .maxCapacity(maxCapacity)
                .posX(1)
                .posY(1)
                .posWidth(2)
                .posHeight(2)
                .active(active)
                .build();
    }

    private Inventory inventory(Long inventoryId, ProductLot lot, WarehouseBin bin, int quantity) {
        return Inventory.builder()
                .inventoryId(inventoryId)
                .lot(lot)
                .bin(bin)
                .quantity(quantity)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
