package com.feedflow.admin.service;

import com.feedflow.admin.dto.TraceEventDto;
import com.feedflow.admin.dto.TraceabilityDto;
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
import com.feedflow.repository.ProductLotRepository;
import com.feedflow.repository.StockMovementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;

/**
 * 제품 이력 추적 서비스 테스트.
 *
 * <h3>핵심 검증</h3>
 * 입고 → 출고 → 출고취소가 발생한 로트를 조회했을 때
 * <ul>
 *     <li>타임라인이 <b>시간순</b>으로, <b>누락 없이</b> 나오는지</li>
 *     <li>각 시점의 <b>잔여 수량</b>이 정확히 누적되는지</li>
 *     <li>출고취소 복구가 입고와 구분되어 집계되는지</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TraceabilityService 단위 테스트")
class TraceabilityServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 28);
    private static final Long LOT_ID = 10L;

    @Mock
    private ProductLotRepository productLotRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private TraceabilityService traceabilityService;

    /* ==================================================================
     * 타임라인 조립 (핵심)
     * ================================================================== */

    @Nested
    @DisplayName("입고 -> 출고 -> 취소 타임라인")
    class Timeline {

        @Test
        @DisplayName("이력이 시간순으로 누락 없이 조립되고 시점별 잔여 수량이 누적된다")
        void buildsTimelineInChronologicalOrder() {
            // given : 입고 100 -> 출고 30 -> 출고 20 -> 취소 복구 30  => 잔여 80
            Product product = product();
            ProductLot lot = lot(product, 80);
            WarehouseBin bin = bin();

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of(
                    movement(1L, MovementType.INBOUND, lot, bin, 100, null, days(-30)),
                    movement(2L, MovementType.OUTBOUND, lot, bin, 30, 5L, days(-10)),
                    movement(3L, MovementType.OUTBOUND, lot, bin, 20, 7L, days(-5)),
                    movement(4L, MovementType.CANCEL, lot, bin, 30, 5L, days(-1))));
            given(inventoryRepository.findByLotIdWithBin(LOT_ID))
                    .willReturn(List.of(inventory(lot, bin, 80)));

            // when
            TraceabilityDto trace = traceabilityService.trace(LOT_ID, TODAY);

            // then : 4건 모두, 순번과 시간순이 유지된다
            assertThat(trace.getTimeline())
                    .as("이력이 하나라도 빠지면 추적이 성립하지 않는다")
                    .hasSize(4)
                    .extracting(TraceEventDto::getSequence,
                            TraceEventDto::getMovementType,
                            TraceEventDto::getQuantity,
                            TraceEventDto::getBalanceAfter)
                    .containsExactly(
                            tuple(1, MovementType.INBOUND, 100, 100),
                            tuple(2, MovementType.OUTBOUND, 30, 70),
                            tuple(3, MovementType.OUTBOUND, 20, 50),
                            tuple(4, MovementType.CANCEL, 30, 80));

            // 시각이 오름차순인지 직접 확인
            assertThat(trace.getTimeline())
                    .extracting(TraceEventDto::getOccurredAt)
                    .isSorted();

            assertThat(trace.getFirstInboundAt()).isEqualTo(days(-30));
            assertThat(trace.getLastMovedAt()).isEqualTo(days(-1));
            assertThat(trace.getEventCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("누적 집계에서 출고취소 복구를 입고와 구분한다")
        void separatesCancelFromInbound() {
            Product product = product();
            ProductLot lot = lot(product, 80);
            WarehouseBin bin = bin();

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of(
                    movement(1L, MovementType.INBOUND, lot, bin, 100, null, days(-30)),
                    movement(2L, MovementType.OUTBOUND, lot, bin, 30, 5L, days(-10)),
                    movement(3L, MovementType.OUTBOUND, lot, bin, 20, 7L, days(-5)),
                    movement(4L, MovementType.CANCEL, lot, bin, 30, 5L, days(-1))));
            given(inventoryRepository.findByLotIdWithBin(LOT_ID))
                    .willReturn(List.of(inventory(lot, bin, 80)));

            TraceabilityDto trace = traceabilityService.trace(LOT_ID, TODAY);

            assertThat(trace.getTotalOutbound()).isEqualTo(50);
            assertThat(trace.getTotalCanceled())
                    .as("취소 복구는 따로 집계해야 실제 입고 실적이 왜곡되지 않는다")
                    .isEqualTo(30);
            assertThat(trace.getTotalInbound())
                    .as("화면의 '누적 입고(복구 포함)' 는 100 + 30")
                    .isEqualTo(130);
            assertThat(trace.isHasCancellation()).isTrue();
        }

        @Test
        @DisplayName("이력 누적값과 로트 잔여 수량이 일치하면 정합 상태로 표시한다")
        void balanceMatchesLotQuantity() {
            Product product = product();
            ProductLot lot = lot(product, 80);
            WarehouseBin bin = bin();

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of(
                    movement(1L, MovementType.INBOUND, lot, bin, 100, null, days(-30)),
                    movement(2L, MovementType.OUTBOUND, lot, bin, 20, 5L, days(-2))));
            given(inventoryRepository.findByLotIdWithBin(LOT_ID))
                    .willReturn(List.of(inventory(lot, bin, 80)));

            TraceabilityDto trace = traceabilityService.trace(LOT_ID, TODAY);

            assertThat(trace.getCalculatedBalance()).isEqualTo(80);
            assertThat(trace.getLotQuantity()).isEqualTo(80);
            assertThat(trace.isBalanceMatched()).isTrue();
        }

        @Test
        @DisplayName("이력 없이 재고가 바뀌어 누적값이 어긋나면 불일치로 표시한다")
        void balanceMismatch_isDetected() {
            Product product = product();
            ProductLot lot = lot(product, 95);      // 실제 잔여 95
            WarehouseBin bin = bin();

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of(
                    movement(1L, MovementType.INBOUND, lot, bin, 100, null, days(-30)),
                    movement(2L, MovementType.OUTBOUND, lot, bin, 20, 5L, days(-2))));
            given(inventoryRepository.findByLotIdWithBin(LOT_ID))
                    .willReturn(List.of(inventory(lot, bin, 95)));

            TraceabilityDto trace = traceabilityService.trace(LOT_ID, TODAY);

            assertThat(trace.getCalculatedBalance())
                    .as("이력상으로는 80 이어야 한다")
                    .isEqualTo(80);
            assertThat(trace.isBalanceMatched())
                    .as("어긋나면 화면에서 정합성 점검을 안내해야 한다")
                    .isFalse();
        }

        @Test
        @DisplayName("폐기 이력도 감소로 반영한다")
        void includesDisposal() {
            Product product = product();
            ProductLot lot = lot(product, 70);
            WarehouseBin bin = bin();

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of(
                    movement(1L, MovementType.INBOUND, lot, bin, 100, null, days(-40)),
                    movement(2L, MovementType.DISPOSAL, lot, bin, 30, null, days(-3))));
            given(inventoryRepository.findByLotIdWithBin(LOT_ID))
                    .willReturn(List.of(inventory(lot, bin, 70)));

            TraceabilityDto trace = traceabilityService.trace(LOT_ID, TODAY);

            assertThat(trace.getTotalDisposed()).isEqualTo(30);
            assertThat(trace.getTimeline().get(1).getBalanceAfter()).isEqualTo(70);
            assertThat(trace.getTimeline().get(1).isDecrease()).isTrue();
        }

        @Test
        @DisplayName("구역 이동은 잔여 수량을 바꾸지 않는다")
        void moveDoesNotChangeBalance() {
            Product product = product();
            ProductLot lot = lot(product, 100);
            WarehouseBin bin = bin();

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of(
                    movement(1L, MovementType.INBOUND, lot, bin, 100, null, days(-20)),
                    movement(2L, MovementType.MOVE, lot, bin, 40, null, days(-2))));
            given(inventoryRepository.findByLotIdWithBin(LOT_ID))
                    .willReturn(List.of(inventory(lot, bin, 100)));

            TraceabilityDto trace = traceabilityService.trace(LOT_ID, TODAY);

            assertThat(trace.getTimeline().get(1).getBalanceAfter())
                    .as("구역만 바뀌었으므로 총량은 그대로다")
                    .isEqualTo(100);
            assertThat(trace.isBalanceMatched()).isTrue();
        }
    }

    /* ==================================================================
     * 이벤트 표기
     * ================================================================== */

    @Nested
    @DisplayName("이벤트 표기")
    class EventPresentation {

        @Test
        @DisplayName("유형별로 증감 방향 · 색상 · 아이콘을 구분한다")
        void distinguishesEventTypes() {
            Product product = product();
            ProductLot lot = lot(product, 50);
            WarehouseBin bin = bin();

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of(
                    movement(1L, MovementType.INBOUND, lot, bin, 100, null, days(-30)),
                    movement(2L, MovementType.OUTBOUND, lot, bin, 80, 5L, days(-10)),
                    movement(3L, MovementType.CANCEL, lot, bin, 30, 5L, days(-1))));
            given(inventoryRepository.findByLotIdWithBin(LOT_ID))
                    .willReturn(List.of(inventory(lot, bin, 50)));

            List<TraceEventDto> timeline = traceabilityService.trace(LOT_ID, TODAY).getTimeline();

            TraceEventDto inbound = timeline.get(0);
            assertThat(inbound.isIncrease()).isTrue();
            assertThat(inbound.getSignedQuantity()).isEqualTo("+100");
            assertThat(inbound.getQuantityTextClass()).isEqualTo("text-success");
            assertThat(inbound.getMarkerClass()).isEqualTo("ff-trace-dot-inbound");
            assertThat(inbound.isLinkedToOrder())
                    .as("직접 입고는 주문과 무관하다")
                    .isFalse();

            TraceEventDto outbound = timeline.get(1);
            assertThat(outbound.isDecrease()).isTrue();
            assertThat(outbound.getSignedQuantity()).isEqualTo("-80");
            assertThat(outbound.isLinkedToOrder()).isTrue();
            assertThat(outbound.getOrderId()).isEqualTo(5L);

            TraceEventDto cancel = timeline.get(2);
            assertThat(cancel.getTypeLabel()).isEqualTo("출고취소");
            assertThat(cancel.getMarkerClass()).isEqualTo("ff-trace-dot-cancel");
            assertThat(cancel.getIconClass()).isEqualTo("bi-arrow-counterclockwise");
            assertThat(cancel.isIncrease()).isTrue();
        }
    }

    /* ==================================================================
     * 보관 위치 / 예외
     * ================================================================== */

    @Nested
    @DisplayName("보관 위치와 예외 처리")
    class StorageAndErrors {

        @Test
        @DisplayName("여러 구역에 나뉘어 보관된 재고를 모두 보여준다")
        void showsAllStorageBins() {
            Product product = product();
            ProductLot lot = lot(product, 90);

            WarehouseBin binA = bin(1L, "A-01");
            WarehouseBin binB = bin(2L, "B-01");

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of(
                    movement(1L, MovementType.INBOUND, lot, binA, 60, null, days(-20)),
                    movement(2L, MovementType.INBOUND, lot, binB, 30, null, days(-15))));
            given(inventoryRepository.findByLotIdWithBin(LOT_ID)).willReturn(List.of(
                    inventory(lot, binA, 60),
                    inventory(lot, binB, 30)));

            TraceabilityDto trace = traceabilityService.trace(LOT_ID, TODAY);

            assertThat(trace.getStorageBinCount()).isEqualTo(2);
            assertThat(trace.isDepleted()).isFalse();
            assertThat(trace.getCurrentStorage())
                    .extracting("binCode", "quantity")
                    .containsExactly(tuple("A-01", 60), tuple("B-01", 30));
        }

        @Test
        @DisplayName("전량 출고된 로트는 보관 위치가 비고 재고 없음으로 표시한다")
        void depletedLot() {
            Product product = product();
            ProductLot lot = lot(product, 0);
            WarehouseBin bin = bin();

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of(
                    movement(1L, MovementType.INBOUND, lot, bin, 100, null, days(-30)),
                    movement(2L, MovementType.OUTBOUND, lot, bin, 100, 5L, days(-2))));
            given(inventoryRepository.findByLotIdWithBin(LOT_ID)).willReturn(List.of());

            TraceabilityDto trace = traceabilityService.trace(LOT_ID, TODAY);

            assertThat(trace.isDepleted()).isTrue();
            assertThat(trace.getCurrentStorage()).isEmpty();
            assertThat(trace.getCalculatedBalance()).isZero();
            assertThat(trace.isBalanceMatched()).isTrue();
        }

        @Test
        @DisplayName("이력이 없는 로트도 오류 없이 조회된다")
        void lotWithoutHistory() {
            Product product = product();
            ProductLot lot = lot(product, 0);

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of());
            given(inventoryRepository.findByLotIdWithBin(LOT_ID)).willReturn(List.of());

            TraceabilityDto trace = traceabilityService.trace(LOT_ID, TODAY);

            assertThat(trace.isHasHistory()).isFalse();
            assertThat(trace.getFirstInboundAt()).isNull();
            assertThat(trace.getLastMovedAt()).isNull();
            assertThat(trace.getCalculatedBalance()).isZero();
        }

        @Test
        @DisplayName("존재하지 않는 로트를 추적하면 ResourceNotFoundException 이 발생한다")
        void notFound_throwsException() {
            given(productLotRepository.findWithProductById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> traceabilityService.trace(999L, TODAY))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("존재하지 않는 로트");
        }
    }

    /* ==================================================================
     * 로트번호 검색
     * ================================================================== */

    @Nested
    @DisplayName("로트번호 검색")
    class Search {

        @Test
        @DisplayName("대소문자와 앞뒤 공백을 무시하고 검색한다")
        void searchIsCaseInsensitiveAndTrimmed() {
            given(productLotRepository.findAllByLotNo("LOT-CT-2601"))
                    .willReturn(List.of(lot(product(), 50)));

            assertThat(traceabilityService.findCandidates("  lot-ct-2601  ")).hasSize(1);
        }

        @Test
        @DisplayName("검색어가 비어 있으면 조회하지 않고 빈 목록을 반환한다")
        void blankKeyword_returnsEmpty() {
            assertThat(traceabilityService.findCandidates(null)).isEmpty();
            assertThat(traceabilityService.findCandidates("   ")).isEmpty();
        }
    }

    /* ==================================================================
     * 픽스처
     * ================================================================== */

    private LocalDateTime days(int offset) {
        return TODAY.atTime(9, 0).plusDays(offset);
    }

    private Product product() {
        return Product.builder()
                .productId(1L)
                .productCode("FD-CT-001")
                .name("프리미엄 육성우 배합사료")
                .animalType(AnimalType.CATTLE)
                .productType(ProductType.FEED)
                .weightKg(25)
                .price(32000L)
                .totalStock(80)
                .safetyStock(10)
                .shelfLifeDays(180)
                .active(true)
                .build();
    }

    private ProductLot lot(Product product, int lotQuantity) {
        return ProductLot.builder()
                .lotId(LOT_ID)
                .product(product)
                .lotNo("LOT-CT-2601")
                .manufacturedDate(TODAY.minusDays(30))
                .expirationDate(TODAY.plusDays(150))
                .lotQuantity(lotQuantity)
                .build();
    }

    private WarehouseBin bin() {
        return bin(1L, "A-01");
    }

    private WarehouseBin bin(Long binId, String binCode) {
        return WarehouseBin.builder()
                .binId(binId)
                .binCode(binCode)
                .warehouse(Warehouse.WH1)
                .zone(binCode.substring(0, 1))
                .binPurpose(BinPurpose.STORAGE)
                .rack("01")
                .binLevel(1)
                .maxCapacity(500)
                .posX(1)
                .posY(1)
                .posWidth(2)
                .posHeight(2)
                .active(true)
                .build();
    }

    private Inventory inventory(ProductLot lot, WarehouseBin bin, int quantity) {
        return Inventory.builder()
                .inventoryId(100L + bin.getBinId())
                .lot(lot)
                .bin(bin)
                .quantity(quantity)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private StockMovement movement(Long movementId,
                                   MovementType type,
                                   ProductLot lot,
                                   WarehouseBin bin,
                                   int quantity,
                                   Long orderId,
                                   LocalDateTime occurredAt) {
        return StockMovement.builder()
                .movementId(movementId)
                .movementType(type)
                .product(lot.getProduct())
                .lot(lot)
                .bin(bin)
                .quantity(quantity)
                .orderId(orderId)
                .memo(type.getDescription() + " 처리")
                .userId(1L)
                .userName("김책임")
                .createdAt(occurredAt)
                .build();
    }
}
