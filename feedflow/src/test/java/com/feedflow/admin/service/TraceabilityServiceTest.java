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
import com.feedflow.domain.Center;
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
     * 물류센터 표기 (Epic Phase 2)
     * ================================================================== */

    @Nested
    @DisplayName("물류센터 표기")
    class CenterPresentation {

        @Test
        @DisplayName("각 이벤트에 그 행위가 일어난 센터가 담긴다")
        void eventCarriesCenter() {
            Product product = product();
            ProductLot lot = lot(product, 100);
            WarehouseBin bin = bin();

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of(
                    movement(1L, MovementType.INBOUND, lot, bin, 100, null, days(-10))));
            given(inventoryRepository.findByLotIdWithBin(LOT_ID))
                    .willReturn(List.of(inventory(lot, bin, 100)));

            TraceEventDto event = traceabilityService.trace(LOT_ID, TODAY).getTimeline().get(0);

            assertThat(event.getCenterId()).isEqualTo(1L);
            assertThat(event.getCenterName()).isEqualTo("제1창고");
            assertThat(event.isCenterKnown()).isTrue();
        }

        /**
         * 센터를 별도 뱃지로 표시하므로 위치 라벨에 센터명이 또 들어가면
         * 한 줄에 "제1창고 제1창고 · A구역" 처럼 같은 정보가 두 번 나온다.
         */
        @Test
        @DisplayName("위치 라벨에는 센터명이 포함되지 않는다")
        void binLocationExcludesCenterName() {
            Product product = product();
            ProductLot lot = lot(product, 100);
            WarehouseBin bin = bin();

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of(
                    movement(1L, MovementType.INBOUND, lot, bin, 100, null, days(-10))));
            given(inventoryRepository.findByLotIdWithBin(LOT_ID))
                    .willReturn(List.of(inventory(lot, bin, 100)));

            TraceEventDto event = traceabilityService.trace(LOT_ID, TODAY).getTimeline().get(0);

            assertThat(event.getBinLocation())
                    .isEqualTo("A구역 · 01랙 · 1단")
                    .doesNotContain("제1창고");
        }

        @Test
        @DisplayName("같은 센터 안에서의 구역 이동은 센터 간 이동으로 보지 않는다")
        void withinCenterMove() {
            Product product = product();
            ProductLot lot = lot(product, 100);
            WarehouseBin from = bin(1L, "A-01");
            WarehouseBin to = bin(2L, "B-02");

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of(
                    movement(1L, MovementType.INBOUND, lot, from, 100, null, days(-10)),
                    moveMovement(2L, lot, from, to, 40, days(-2))));
            given(inventoryRepository.findByLotIdWithBin(LOT_ID))
                    .willReturn(List.of(inventory(lot, from, 60), inventory(lot, to, 40)));

            TraceabilityDto trace = traceabilityService.trace(LOT_ID, TODAY);
            TraceEventDto move = trace.getTimeline().get(1);

            assertThat(move.isRelocation()).isTrue();
            assertThat(move.isWithinCenterMove())
                    .as("출발지와 도착지가 같은 센터면 센터명을 한 번만 표시한다")
                    .isTrue();
            assertThat(move.isCenterTransfer()).isFalse();
            assertThat(trace.isHasCenterTransfer()).isFalse();
        }

        /**
         * MOVE 는 총 재고 불변을 전제하지만 센터가 다르면 출발 센터의 재고가 실제로 줄어든다.
         * Phase 3 까지는 나와서는 안 되는 이력이므로 화면이 경고할 수 있어야 한다.
         */
        @Test
        @DisplayName("센터를 넘는 구역 이동은 센터 간 이동으로 판정해 경고 대상이 된다")
        void detectsCenterTransfer() {
            Product product = product();
            ProductLot lot = lot(product, 100);
            WarehouseBin from = bin(1L, "A-01", center());
            WarehouseBin to = bin(8L, "N-01", otherCenter());

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of(
                    movement(1L, MovementType.INBOUND, lot, from, 100, null, days(-10)),
                    moveMovement(2L, lot, from, to, 40, days(-2))));
            given(inventoryRepository.findByLotIdWithBin(LOT_ID))
                    .willReturn(List.of(inventory(lot, from, 60), inventory(lot, to, 40)));

            TraceabilityDto trace = traceabilityService.trace(LOT_ID, TODAY);
            TraceEventDto move = trace.getTimeline().get(1);

            assertThat(move.isCenterTransfer()).isTrue();
            assertThat(move.isWithinCenterMove())
                    .as("센터가 다르면 양쪽 센터를 모두 표시해야 한다")
                    .isFalse();
            assertThat(move.getFromCenterName()).isEqualTo("제1창고");
            assertThat(move.getCenterName()).isEqualTo("제2창고");
            assertThat(trace.isHasCenterTransfer())
                    .as("로트 단위 경고를 띄울 수 있어야 한다")
                    .isTrue();
        }

        @Test
        @DisplayName("구역이 기록되지 않은 이력은 센터를 알 수 없음으로 표시한다")
        void binlessEventHasNoCenter() {
            Product product = product();
            ProductLot lot = lot(product, 20);

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of(
                    movement(1L, MovementType.INBOUND, lot, bin(), 100, null, days(-10)),
                    // 구역 없이 기록된 출고 (로트에서만 차감된 경로)
                    movement(2L, MovementType.OUTBOUND, lot, null, 80, 5L, days(-1))));
            given(inventoryRepository.findByLotIdWithBin(LOT_ID))
                    .willReturn(List.of(inventory(lot, bin(), 20)));

            TraceEventDto outbound = traceabilityService.trace(LOT_ID, TODAY).getTimeline().get(1);

            assertThat(outbound.isCenterKnown()).isFalse();
            assertThat(outbound.getCenterName()).isNull();
            assertThat(outbound.getBinCode())
                    .as("구역 없는 이력도 타임라인에서 빠지지 않아야 한다")
                    .isNull();
        }

        @Test
        @DisplayName("거쳐 간 센터를 발생 순서대로 중복 없이 모은다")
        void collectsInvolvedCentersInOrder() {
            Product product = product();
            ProductLot lot = lot(product, 100);
            WarehouseBin first = bin(1L, "A-01", center());
            WarehouseBin second = bin(8L, "N-01", otherCenter());

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of(
                    movement(1L, MovementType.INBOUND, lot, first, 100, null, days(-10)),
                    moveMovement(2L, lot, first, second, 40, days(-2))));
            given(inventoryRepository.findByLotIdWithBin(LOT_ID))
                    .willReturn(List.of(inventory(lot, first, 60), inventory(lot, second, 40)));

            TraceabilityDto trace = traceabilityService.trace(LOT_ID, TODAY);

            assertThat(trace.getInvolvedCenterNames())
                    .as("통과 순서를 알 수 있어야 회수 범위를 정할 수 있다")
                    .containsExactly("제1창고", "제2창고");
            assertThat(trace.getInvolvedCenterSummary()).isEqualTo("제1창고 → 제2창고");
        }

        @Test
        @DisplayName("재고가 여러 센터에 남아 있으면 분산 상태로 표시한다")
        void reportsSplitAcrossCenters() {
            Product product = product();
            ProductLot lot = lot(product, 100);
            WarehouseBin here = bin(1L, "A-01", center());
            WarehouseBin there = bin(8L, "N-01", otherCenter());

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of(
                    movement(1L, MovementType.INBOUND, lot, here, 100, null, days(-10))));
            given(inventoryRepository.findByLotIdWithBin(LOT_ID))
                    .willReturn(List.of(inventory(lot, here, 60), inventory(lot, there, 40)));

            TraceabilityDto trace = traceabilityService.trace(LOT_ID, TODAY);

            assertThat(trace.getCurrentCenterNames()).containsExactly("제1창고", "제2창고");
            assertThat(trace.getCurrentCenterCount()).isEqualTo(2);
            assertThat(trace.isSplitAcrossCenters()).isTrue();
            assertThat(trace.getCurrentCenterSummary()).isEqualTo("제1창고, 제2창고");
        }

        @Test
        @DisplayName("한 센터에만 있으면 분산으로 보지 않는다")
        void singleCenter_notSplit() {
            Product product = product();
            ProductLot lot = lot(product, 100);
            WarehouseBin binA = bin(1L, "A-01");
            WarehouseBin binB = bin(2L, "B-02");

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of(
                    movement(1L, MovementType.INBOUND, lot, binA, 100, null, days(-10))));
            given(inventoryRepository.findByLotIdWithBin(LOT_ID))
                    .willReturn(List.of(inventory(lot, binA, 60), inventory(lot, binB, 40)));

            TraceabilityDto trace = traceabilityService.trace(LOT_ID, TODAY);

            assertThat(trace.getCurrentCenterNames())
                    .as("같은 센터의 구역 두 곳은 센터 하나로 센다")
                    .containsExactly("제1창고");
            assertThat(trace.isSplitAcrossCenters()).isFalse();
        }

        @Test
        @DisplayName("이력이 없는 로트는 센터 목록이 비어 있고 요약은 '-' 이다")
        void noHistory_emptyCenters() {
            Product product = product();
            ProductLot lot = lot(product, 0);

            given(productLotRepository.findWithProductById(LOT_ID)).willReturn(Optional.of(lot));
            given(stockMovementRepository.findLotHistory(LOT_ID)).willReturn(List.of());
            given(inventoryRepository.findByLotIdWithBin(LOT_ID)).willReturn(List.of());

            TraceabilityDto trace = traceabilityService.trace(LOT_ID, TODAY);

            assertThat(trace.getInvolvedCenterNames()).isEmpty();
            assertThat(trace.getCurrentCenterNames()).isEmpty();
            assertThat(trace.getInvolvedCenterSummary()).isEqualTo("-");
            assertThat(trace.getCurrentCenterSummary()).isEqualTo("-");
            assertThat(trace.isSplitAcrossCenters()).isFalse();
            assertThat(trace.isHasCenterTransfer()).isFalse();
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
        return bin(binId, binCode, center());
    }

    private WarehouseBin bin(Long binId, String binCode, Center center) {
        return WarehouseBin.builder()
                .binId(binId)
                .binCode(binCode)
                .center(center)
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
        return movement(movementId, type, lot, bin, null, quantity, orderId, occurredAt);
    }

    /** 출발 구역이 있는 이력 (구역 간 이동) */
    private StockMovement moveMovement(Long movementId,
                                       ProductLot lot,
                                       WarehouseBin fromBin,
                                       WarehouseBin toBin,
                                       int quantity,
                                       LocalDateTime occurredAt) {
        return movement(movementId, MovementType.MOVE, lot, toBin, fromBin, quantity, null, occurredAt);
    }

    private StockMovement movement(Long movementId,
                                   MovementType type,
                                   ProductLot lot,
                                   WarehouseBin bin,
                                   WarehouseBin fromBin,
                                   int quantity,
                                   Long orderId,
                                   LocalDateTime occurredAt) {
        return StockMovement.builder()
                .movementId(movementId)
                .movementType(type)
                .product(lot.getProduct())
                .lot(lot)
                .bin(bin)
                .fromBin(fromBin)
                .quantity(quantity)
                .orderId(orderId)
                .memo(type.getDescription() + " 처리")
                .userId(1L)
                .userName("김책임")
                .createdAt(occurredAt)
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

    /** 제2창고 — 센터를 넘는 이동을 검증할 때 쓴다 */
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

}
