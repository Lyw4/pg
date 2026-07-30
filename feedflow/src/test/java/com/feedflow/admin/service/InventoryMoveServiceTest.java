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
import com.feedflow.domain.Center;
import com.feedflow.domain.WarehouseBin;
import com.feedflow.repository.InventoryRepository;
import com.feedflow.repository.StockMovementRepository;
import com.feedflow.repository.WarehouseBinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    private InventoryMoveService inventoryMoveService;

    /**
     * {@code BinCapacityChecker} 는 mock 이 아니라 <b>실제 인스턴스</b>를 주입한다.
     * <p>
     * 적재 한도 판정은 이 테스트가 검증해야 하는 업무 규칙의 일부다. mock 으로 대체하면
     * {@code sumQuantityByBinId} 스텁이 무의미해지고 '한도를 넘으면 거부한다' 는
     * 검증이 껍데기만 남는다. 조회 결과만 mock 으로 주고 판정은 실제 코드가 하게 둔다.
     */
    /**
     * 한도 검증기는 mock 이 아니라 실제 인스턴스다.
     * <p>
     * 운송 중 가상 구역의 한도 <b>면제</b>가 이 테스트가 검증해야 하는 규칙이므로
     * mock 으로 대체하면 검증이 껍데기만 남는다. 필드로 꺼내 두어 테스트에서
     * 직접 호출할 수도 있게 한다.
     */
    private BinCapacityChecker binCapacityChecker;

    @BeforeEach
    void setUp() {
        binCapacityChecker = new BinCapacityChecker(inventoryRepository);
        inventoryMoveService = new InventoryMoveService(inventoryRepository, warehouseBinRepository, stockMovementRepository,
                binCapacityChecker);
    }

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
            given(warehouseBinRepository.findWithCenterById(2L)).willReturn(Optional.of(toBin));
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

            // 도착 구역에는 다른 로트가 50 이미 쌓여 있다.
            // 이 로트의 수량(30)과 구역 전체 적재량(80)은 다른 값이다.
            assertThat(result.getToBinLoadAfter())
                    .as("구역 전체 적재량 = 기존 50 + 이동 30")
                    .isEqualTo(80);
            assertThat(result.getToRemainingCapacity())
                    .as("한도 600 - 적재 80")
                    .isEqualTo(520);

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
            given(warehouseBinRepository.findWithCenterById(2L)).willReturn(Optional.of(toBin));
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
            given(warehouseBinRepository.findWithCenterById(2L)).willReturn(Optional.of(toBin));
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
            given(warehouseBinRepository.findWithCenterById(2L)).willReturn(Optional.of(toBin));
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
            given(warehouseBinRepository.findWithCenterById(2L)).willReturn(Optional.of(toBin));
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
     * 센터 간 이관 (Epic Phase 3a)
     * ================================================================== */

    @Nested
    @DisplayName("센터 간 이관")
    class CenterTransfer {

        /**
         * 센터가 다르면 MOVE 를 쓸 수 없다. MOVE 는 총량 불변을 전제하지만
         * 센터가 다르면 출발 센터의 재고가 실제로 줄어든다.
         */
        @Test
        @DisplayName("센터가 다르면 TRANSFER_OUT + TRANSFER_IN 두 건을 남긴다")
        void recordsTwoTransferLegs() {
            Product product = product(200);
            ProductLot lot = lot(lotId(), product, 150);

            Center from = center();
            Center to = otherCenter();
            WarehouseBin fromBin = bin(1L, "A-01", 600, true, from);
            WarehouseBin toBin = bin(8L, "N-01", 600, true, to);
            WarehouseBin transit = inTransitBin(41L, from);

            Inventory source = inventory(INVENTORY_ID, lot, fromBin, 100);

            given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(source));
            given(warehouseBinRepository.findWithCenterById(8L)).willReturn(Optional.of(toBin));
            given(warehouseBinRepository.findInTransitBin(1L)).willReturn(Optional.of(transit));
            given(inventoryRepository.findByLot_LotIdAndBin_BinId(lot.getLotId(), 8L))
                    .willReturn(Optional.empty());
            given(inventoryRepository.sumQuantityByBinId(8L)).willReturn(0L);
            given(inventoryRepository.save(any(Inventory.class)))
                    .willAnswer(call -> call.getArgument(0));

            inventoryMoveService.move(form(INVENTORY_ID, 8L, 40), USER_ID, USER_NAME);

            ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
            verify(stockMovementRepository, times(2)).save(captor.capture());

            List<StockMovement> saved = captor.getAllValues();

            assertThat(saved)
                    .extracting(StockMovement::getMovementType)
                    .as("MOVE 한 건이 아니라 출고/입고 두 건이어야 센터별 실적을 집계할 수 있다")
                    .containsExactly(MovementType.TRANSFER_OUT, MovementType.TRANSFER_IN);

            StockMovement out = saved.get(0);
            assertThat(out.getFromBin().getBinCode())
                    .as("출고 구간의 출발지는 실제 구역이다")
                    .isEqualTo("A-01");
            assertThat(out.getBin().getBinCode())
                    .as("출고 구간의 도착지는 운송 중 가상 구역이다")
                    .isEqualTo("TRANSIT-WH1");

            StockMovement in = saved.get(1);
            assertThat(in.getFromBin().getBinCode())
                    .as("입고 구간의 출발지는 운송 중 가상 구역이다")
                    .isEqualTo("TRANSIT-WH1");
            assertThat(in.getBin().getBinCode())
                    .as("입고 구간의 도착지는 도착 센터의 실제 구역이다")
                    .isEqualTo("N-01");

            assertThat(saved).allSatisfy(m -> {
                assertThat(m.getQuantity()).isEqualTo(40);
                assertThat(m.getLot()).isEqualTo(lot);
            });
        }

        /**
         * 두 건의 sign 합이 0 이어야 로트 잔여 수량이 유지된다.
         * 한쪽만 남으면 이력 누적값이 어긋나 이력 추적 화면이 불일치 경고를 띄운다.
         */
        @Test
        @DisplayName("이관 두 건의 증감 방향 합은 0 이라 전국 총 재고가 유지된다")
        void transferSignsCancelOut() {
            assertThat(MovementType.TRANSFER_OUT.getSign()).isEqualTo(-1);
            assertThat(MovementType.TRANSFER_IN.getSign()).isEqualTo(1);
            assertThat(MovementType.TRANSFER_OUT.getSign() + MovementType.TRANSFER_IN.getSign())
                    .as("센터를 옮겨도 전국 합계는 그대로여야 한다")
                    .isZero();
        }

        @Test
        @DisplayName("이관도 로트 잔여 수량과 품목 총 재고를 바꾸지 않는다")
        void transferKeepsTotals() {
            Product product = product(200);
            ProductLot lot = lot(lotId(), product, 150);

            Center from = center();
            WarehouseBin fromBin = bin(1L, "A-01", 600, true, from);
            WarehouseBin toBin = bin(8L, "N-01", 600, true, otherCenter());
            Inventory source = inventory(INVENTORY_ID, lot, fromBin, 100);

            given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(source));
            given(warehouseBinRepository.findWithCenterById(8L)).willReturn(Optional.of(toBin));
            given(warehouseBinRepository.findInTransitBin(1L))
                    .willReturn(Optional.of(inTransitBin(41L, from)));
            given(inventoryRepository.findByLot_LotIdAndBin_BinId(lot.getLotId(), 8L))
                    .willReturn(Optional.empty());
            given(inventoryRepository.sumQuantityByBinId(8L)).willReturn(0L);
            given(inventoryRepository.save(any(Inventory.class)))
                    .willAnswer(call -> call.getArgument(0));

            StockMoveResultDto result =
                    inventoryMoveService.move(form(INVENTORY_ID, 8L, 40), USER_ID, USER_NAME);

            assertThat(lot.getLotQuantity())
                    .as("센터를 옮긴 것이므로 로트 잔여는 그대로다")
                    .isEqualTo(150);
            assertThat(product.getTotalStock())
                    .as("totalStock 은 전국 합계이므로 변하지 않는다")
                    .isEqualTo(200);

            assertThat(source.getQuantity()).isEqualTo(60);
            assertThat(result.isCenterTransfer()).isTrue();
            assertThat(result.getInTransitBinCode()).isEqualTo("TRANSIT-WH1");
            assertThat(result.getFromCenterName()).isEqualTo("제1창고");
            assertThat(result.getToCenterName()).isEqualTo("제2창고");
            assertThat(result.getMovementLabel()).isEqualTo("센터 간 이관");
        }

        /**
         * 센터는 운영 중에 늘어난다. 센터를 만들 때마다 사람이 가상 구역을 함께 만들게 하면
         * 반드시 빠뜨리고, 그러면 첫 이관에서 실패한다.
         */
        @Test
        @DisplayName("운송 중 가상 구역이 없으면 규칙에 맞는 코드로 자동 생성한다")
        void createsInTransitBinWhenMissing() {
            Product product = product(200);
            ProductLot lot = lot(lotId(), product, 150);

            Center from = center();
            WarehouseBin fromBin = bin(1L, "A-01", 600, true, from);
            WarehouseBin toBin = bin(8L, "N-01", 600, true, otherCenter());
            Inventory source = inventory(INVENTORY_ID, lot, fromBin, 100);

            given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(source));
            given(warehouseBinRepository.findWithCenterById(8L)).willReturn(Optional.of(toBin));
            given(warehouseBinRepository.findInTransitBin(1L)).willReturn(Optional.empty());
            given(warehouseBinRepository.save(any(WarehouseBin.class)))
                    .willAnswer(call -> call.getArgument(0));
            given(inventoryRepository.findByLot_LotIdAndBin_BinId(lot.getLotId(), 8L))
                    .willReturn(Optional.empty());
            given(inventoryRepository.sumQuantityByBinId(8L)).willReturn(0L);
            given(inventoryRepository.save(any(Inventory.class)))
                    .willAnswer(call -> call.getArgument(0));

            inventoryMoveService.move(form(INVENTORY_ID, 8L, 40), USER_ID, USER_NAME);

            ArgumentCaptor<WarehouseBin> captor = ArgumentCaptor.forClass(WarehouseBin.class);
            verify(warehouseBinRepository).save(captor.capture());

            WarehouseBin created = captor.getValue();
            assertThat(created.getBinCode())
                    .as("자동 생성 로직이 다시 찾을 수 있는 코드 규칙이어야 한다")
                    .isEqualTo("TRANSIT-WH1");
            assertThat(created.getBinPurpose()).isEqualTo(BinPurpose.IN_TRANSIT);
            assertThat(created.getCenter())
                    .as("운송 중 재고는 아직 출발 센터의 책임 아래 있다")
                    .isEqualTo(from);
            assertThat(created.isActive())
                    .as("사용 중지 상태로 만들면 이관이 곧바로 막힌다")
                    .isTrue();
            assertThat(created.getBinPurpose().isPhysicalSpace())
                    .as("물리적 공간이 아니므로 적재 한도를 검증하지 않는다")
                    .isFalse();
        }

        @Test
        @DisplayName("같은 센터 안의 이동은 이관이 아니라 MOVE 한 건으로 남는다")
        void sameCenterStaysMove() {
            Product product = product(200);
            ProductLot lot = lot(lotId(), product, 150);
            WarehouseBin fromBin = bin(1L, "A-01", 600, true);
            WarehouseBin toBin = bin(2L, "B-02", 600, true);
            Inventory source = inventory(INVENTORY_ID, lot, fromBin, 100);

            given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(source));
            given(warehouseBinRepository.findWithCenterById(2L)).willReturn(Optional.of(toBin));
            given(inventoryRepository.findByLot_LotIdAndBin_BinId(lot.getLotId(), 2L))
                    .willReturn(Optional.empty());
            given(inventoryRepository.sumQuantityByBinId(2L)).willReturn(0L);
            given(inventoryRepository.save(any(Inventory.class)))
                    .willAnswer(call -> call.getArgument(0));

            StockMoveResultDto result =
                    inventoryMoveService.move(form(INVENTORY_ID, 2L, 30), USER_ID, USER_NAME);

            verify(stockMovementRepository, times(1)).save(any(StockMovement.class));
            verify(warehouseBinRepository, never()).findInTransitBin(any());

            assertThat(result.isCenterTransfer()).isFalse();
            assertThat(result.getInTransitBinCode()).isNull();
            assertThat(result.getMovementLabel()).isEqualTo("구역 이동");
        }

        /**
         * 화면 선택 목록에서는 이미 제외했지만, 요청을 직접 조립하면 통과할 수 있다.
         * 사용자가 여기에 재고를 넣으면 어느 센터에서도 팔 수 없는 상태로 갇힌다.
         */
        @Test
        @DisplayName("운송 중 구역을 도착지로 직접 지정하면 거부한다")
        void rejectsDirectMoveIntoInTransitBin() {
            Product product = product(200);
            ProductLot lot = lot(lotId(), product, 150);
            WarehouseBin fromBin = bin(1L, "A-01", 600, true);
            WarehouseBin transit = inTransitBin(41L, center());
            Inventory source = inventory(INVENTORY_ID, lot, fromBin, 100);

            given(inventoryRepository.findWithDetailById(INVENTORY_ID)).willReturn(Optional.of(source));
            given(warehouseBinRepository.findWithCenterById(41L)).willReturn(Optional.of(transit));

            assertThatThrownBy(() ->
                    inventoryMoveService.move(form(INVENTORY_ID, 41L, 10), USER_ID, USER_NAME))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("운송 중 구역으로는 직접 이동할 수 없습니다");

            assertThat(source.getQuantity())
                    .as("거부되었으므로 재고가 움직이지 않아야 한다")
                    .isEqualTo(100);
            verify(stockMovementRepository, never()).save(any(StockMovement.class));
        }

        @Test
        @DisplayName("운송 중 구역은 적재 한도가 0 이어도 이관을 막지 않는다")
        void inTransitBinHasNoCapacityLimit() {
            // maxCapacity 0 인 가상 구역을 한도로 판정하면 모든 이관이 막힌다.
            // BinCapacityChecker 가 물리적 공간이 아닌 구역의 검증을 건너뛰는지 확인한다.
            WarehouseBin transit = inTransitBin(41L, center());

            assertThat(transit.capacityLimit()).isZero();
            assertThat(transit.getBinPurpose().isPhysicalSpace()).isFalse();
            assertThat(binCapacityChecker.checkCanAccept(transit, 9999, "이관"))
                    .as("한도 검증을 건너뛰므로 예외 없이 현재 적재량을 돌려준다")
                    .isZero();
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
            given(warehouseBinRepository.findWithCenterById(1L)).willReturn(Optional.of(bin));

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
            given(warehouseBinRepository.findWithCenterById(2L)).willReturn(Optional.of(toBin));

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
            given(warehouseBinRepository.findWithCenterById(2L)).willReturn(Optional.of(inactiveTo));

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
            given(warehouseBinRepository.findWithCenterById(2L)).willReturn(Optional.of(toBin));
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
            given(warehouseBinRepository.findWithCenterById(2L)).willReturn(Optional.of(toBin));
            given(inventoryRepository.findByLot_LotIdAndBin_BinId(lot.getLotId(), 2L))
                    .willReturn(Optional.empty());
            given(inventoryRepository.sumQuantityByBinId(2L)).willReturn(80L);
            given(inventoryRepository.save(any(Inventory.class)))
                    .willAnswer(call -> call.getArgument(0));

            // 80 + 20 = 100 = 한도
            StockMoveResultDto result =
                    inventoryMoveService.move(form(INVENTORY_ID, 2L, 20), USER_ID, USER_NAME);

            assertThat(result.getMovedQuantity()).isEqualTo(20);

            // 여유 공간은 '구역 전체 적재량' 기준이다.
            // 이 로트의 도착 수량(20)만으로 계산하면 여유가 80 으로 잘못 나온다.
            assertThat(result.getToQuantityAfter())
                    .as("이 로트의 도착 구역 수량")
                    .isEqualTo(20);
            assertThat(result.getToBinLoadAfter())
                    .as("구역 전체 적재량 = 기존 80 + 이동 20")
                    .isEqualTo(100);
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
            given(warehouseBinRepository.findWithCenterById(999L)).willReturn(Optional.empty());

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
        return bin(binId, binCode, maxCapacity, active, center());
    }

    /** 센터를 지정하는 구역 픽스처 (센터 간 이관 검증용) */
    private WarehouseBin bin(Long binId, String binCode, int maxCapacity, boolean active,
                             Center center) {
        return WarehouseBin.builder()
                .binId(binId)
                .binCode(binCode)
                .center(center)
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

    /**
     * 테스트용 센터 픽스처.
     * <p>
     * {@code Warehouse} enum 이 {@link Center} 엔티티로 승격되어 구역마다 센터가 필요하다.
     * DB 에 저장하지 않는 단위 테스트이므로 빌더로 만든 객체를 그대로 쓴다.
     */
    private Center center() {
        return center(1L, "WH1", "제1창고");
    }

    /** 제2창고 — 센터 간 이관 검증용 */
    private Center otherCenter() {
        return center(2L, "WH2", "제2창고");
    }

    private Center center(Long centerId, String centerCode, String name) {
        return Center.builder()
                .centerId(centerId)
                .centerCode(centerCode)
                .name(name)
                .region("수도권")
                .note("상온 · 배합사료")
                .active(true)
                .build();
    }

    /** 센터의 운송 중 가상 구역 픽스처 */
    private WarehouseBin inTransitBin(Long binId, Center center) {
        WarehouseBin bin = WarehouseBin.createInTransit(center);
        return WarehouseBin.builder()
                .binId(binId)
                .binCode(bin.getBinCode())
                .center(center)
                .zone(bin.getZone())
                .binPurpose(BinPurpose.IN_TRANSIT)
                .maxCapacity(0)
                .posX(1)
                .posY(1)
                .posWidth(1)
                .posHeight(1)
                .active(true)
                .build();
    }
}
