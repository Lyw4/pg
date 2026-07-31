package com.feedflow.admin.service;

import com.feedflow.domain.AnimalType;
import com.feedflow.domain.BinPurpose;
import com.feedflow.admin.dto.InboundForm;
import com.feedflow.admin.dto.InboundResultDto;
import com.feedflow.admin.dto.CenterStockDto;
import com.feedflow.admin.dto.CenterStockRow;
import com.feedflow.admin.dto.InventoryDto;
import com.feedflow.admin.dto.InventorySearchDto;
import com.feedflow.domain.Center;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.ResourceNotFoundException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 재고 입고(Inbound) 서비스 단위 테스트.
 * <p>
 * DB / Spring 컨텍스트 없이 Repository 를 Mock 으로 대체하여 아래를 검증한다.
 * <ol>
 *     <li>동일 로트 + 동일 구역 재입고 → 수량 합산(UPDATE), 새 재고 행 생성 안 함</li>
 *     <li>동일 로트 + 새로운 구역 → 새 재고 행 생성(INSERT)</li>
 *     <li>유통기한 자동 계산 및 D-Day 계산 정확성</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService 입고 로직 단위 테스트")
class InventoryServiceTest {

    private static final Long PRODUCT_ID = 1L;
    private static final Long BIN_ID = 10L;
    private static final Long OTHER_BIN_ID = 20L;
    private static final Long LOT_ID = 100L;
    private static final String LOT_NO = "L260701-FD-CT-001-01";

    private static final Long USER_ID = 2L;
    private static final String USER_NAME = "이사원";

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

    private InventoryService inventoryService;

    /**
     * {@code BinCapacityChecker} 는 mock 이 아니라 <b>실제 인스턴스</b>를 주입한다.
     * <p>
     * 적재 한도 판정은 이 테스트가 검증해야 하는 업무 규칙의 일부다. mock 으로 대체하면
     * {@code sumQuantityByBinId} 스텁이 무의미해지고 '한도를 넘으면 거부한다' 는
     * 검증이 껍데기만 남는다. 조회 결과만 mock 으로 주고 판정은 실제 코드가 하게 둔다.
     */
    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(productRepository, productLotRepository, warehouseBinRepository,
                inventoryRepository, stockMovementRepository,
                new BinCapacityChecker(inventoryRepository));
    }

    /* ==================================================================
     * 재고 현황 센터 필터 (Epic Phase 2)
     * ================================================================== */

    @Nested
    @DisplayName("재고 현황 센터 필터")
    class CenterFilter {

        private static final Long CENTER_1 = 1L;
        private static final Long CENTER_2 = 2L;

        @Test
        @DisplayName("선택한 센터를 Repository 조건으로 그대로 전달한다")
        void passesCenterIdToRepository() {
            given(inventoryRepository.search(CENTER_1, null, null, null)).willReturn(List.of());

            inventoryService.getInventories(CENTER_1, null, null, null);

            verify(inventoryRepository).search(CENTER_1, null, null, null);
        }

        @Test
        @DisplayName("센터를 지정하지 않으면 null 을 넘겨 전국을 조회한다")
        void nullCenterMeansNationwide() {
            given(inventoryRepository.search(null, null, null, null)).willReturn(List.of());

            inventoryService.getInventories(null, null, null, null);

            verify(inventoryRepository).search(null, null, null, null);
        }

        @Test
        @DisplayName("구역(Zone) 검색어의 앞뒤 공백은 제거하고 빈 문자열은 조건에서 제외한다")
        void normalizesZoneKeyword() {
            given(inventoryRepository.search(null, null, null, "A")).willReturn(List.of());
            given(inventoryRepository.search(null, null, null, null)).willReturn(List.of());

            inventoryService.getInventories(null, null, null, "  A  ");
            inventoryService.getInventories(null, null, null, "   ");

            verify(inventoryRepository).search(null, null, null, "A");
            verify(inventoryRepository).search(null, null, null, null);
        }

        /**
         * 화면 상단 요약 카드는 전국 기준이라 센터 필터를 걸면 카드와 목록이 어긋나 보인다.
         * 목록 자체의 합계를 함께 내려줘야 사용자가 오해하지 않는다.
         */
        @Test
        @DisplayName("조회 결과의 수량 합계와 센터 수를 함께 집계한다")
        void aggregatesSearchResult() {
            Product product = product(180);
            ProductLot lot = lot(product, 100);

            WarehouseBin here = binIn(10L, "A-01", center(CENTER_1, "WH1", "제1창고"));
            WarehouseBin there = binIn(20L, "N-01", center(CENTER_2, "WH2", "제2창고"));

            given(inventoryRepository.search(null, null, null, null)).willReturn(List.of(
                    inventory(lot, here, 60),
                    inventory(lot, there, 40)));

            InventorySearchDto search = inventoryService.getInventories(null, null, null, null);

            assertThat(search.getRowCount()).isEqualTo(2);
            assertThat(search.getTotalQuantity()).isEqualTo(100);
            assertThat(search.getCenterCount()).isEqualTo(2);
            assertThat(search.getBinCount()).isEqualTo(2);
            assertThat(search.isAcrossCenters())
                    .as("여러 센터가 섞였으면 화면이 센터 컬럼을 강조해야 한다")
                    .isTrue();
            assertThat(search.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("한 센터의 재고만 조회되면 여러 센터에 걸친 것으로 보지 않는다")
        void singleCenter_notAcrossCenters() {
            Product product = product(180);
            ProductLot lot = lot(product, 50);
            Center center = center(CENTER_1, "WH1", "제1창고");

            given(inventoryRepository.search(CENTER_1, null, null, null)).willReturn(List.of(
                    inventory(lot, binIn(10L, "A-01", center), 30),
                    inventory(lot, binIn(11L, "B-01", center), 20)));

            InventorySearchDto search = inventoryService.getInventories(CENTER_1, null, null, null);

            assertThat(search.getCenterCount()).isEqualTo(1);
            assertThat(search.getBinCount())
                    .as("같은 센터의 구역 두 곳은 구역 2개로 센다")
                    .isEqualTo(2);
            assertThat(search.isAcrossCenters()).isFalse();
            assertThat(search.getTotalQuantity()).isEqualTo(50);
        }

        @Test
        @DisplayName("조회 결과가 없으면 집계는 모두 0 이다")
        void emptyResult_zeroAggregates() {
            given(inventoryRepository.search(CENTER_2, null, null, null)).willReturn(List.of());

            InventorySearchDto search = inventoryService.getInventories(CENTER_2, null, null, null);

            assertThat(search.isEmpty()).isTrue();
            assertThat(search.getRowCount()).isZero();
            assertThat(search.getTotalQuantity()).isZero();
            assertThat(search.getCenterCount()).isZero();
            assertThat(search.isAcrossCenters()).isFalse();
            assertThat(search.isHasExpired()).isFalse();
        }

        /**
         * 분포는 목록 필터와 무관하게 집계한다. 목록을 그룹핑하면 센터를 하나 고른 순간
         * 분포도 그 센터 하나로 줄어들어 "다른 센터에도 재고가 있다" 는 사실을 알 수 없다.
         */
        @Test
        @DisplayName("센터별 분포는 전국 합계 대비 비중을 함께 계산한다")
        void calculatesCenterShare() {
            given(inventoryRepository.findStockByCenter(null)).willReturn(List.of(
                    new CenterStockRow(CENTER_1, "제1창고", 700L, 12L),
                    new CenterStockRow(CENTER_2, "제2창고", 300L, 5L)));

            List<CenterStockDto> distribution = inventoryService.getStockByCenter(null);

            assertThat(distribution)
                    .extracting(CenterStockDto::getCenterName,
                            CenterStockDto::getQuantity,
                            CenterStockDto::getSharePercent)
                    .containsExactly(
                            tuple("제1창고", 700, 70),
                            tuple("제2창고", 300, 30));
            assertThat(distribution.get(0).getRowCount()).isEqualTo(12);
        }

        @Test
        @DisplayName("재고가 하나도 없으면 비중을 0 으로 두고 0 으로 나누지 않는다")
        void zeroTotal_noDivisionByZero() {
            given(inventoryRepository.findStockByCenter(null)).willReturn(List.of(
                    new CenterStockRow(CENTER_1, "제1창고", null, 0L)));

            List<CenterStockDto> distribution = inventoryService.getStockByCenter(null);

            assertThat(distribution.get(0).getQuantity()).isZero();
            assertThat(distribution.get(0).getSharePercent()).isZero();
        }

        @Test
        @DisplayName("품목을 지정하면 그 품목의 전국 합계가 비중의 분모가 된다")
        void shareIsRelativeToFilteredTotal() {
            given(inventoryRepository.findStockByCenter(PRODUCT_ID)).willReturn(List.of(
                    new CenterStockRow(CENTER_1, "제1창고", 40L, 2L),
                    new CenterStockRow(CENTER_2, "제2창고", 10L, 1L)));

            List<CenterStockDto> distribution = inventoryService.getStockByCenter(PRODUCT_ID);

            assertThat(distribution)
                    .extracting(CenterStockDto::getSharePercent)
                    .as("이 품목이 제1창고에 쏠려 있다는 사실이 보여야 한다")
                    .containsExactly(80, 20);
        }

        @Test
        @DisplayName("각 행에 센터 식별자와 센터명이 담기고 위치 라벨은 센터명을 중복하지 않는다")
        void rowCarriesCenterWithoutDuplicatingName() {
            Product product = product(180);
            ProductLot lot = lot(product, 30);
            WarehouseBin bin = binIn(10L, "A-01", center(CENTER_1, "WH1", "제1창고"));

            given(inventoryRepository.search(CENTER_1, null, null, null))
                    .willReturn(List.of(inventory(lot, bin, 30)));

            InventoryDto row = inventoryService
                    .getInventories(CENTER_1, null, null, null).getRows().get(0);

            assertThat(row.getCenterId()).isEqualTo(CENTER_1);
            assertThat(row.getCenterName()).isEqualTo("제1창고");
            assertThat(row.getBinLocationLabel())
                    .as("센터를 별도 컬럼으로 보여주므로 위치 라벨에서는 센터명을 뺀다")
                    .isEqualTo("A구역 · 01랙 · 1단")
                    .doesNotContain("제1창고");
            assertThat(row.getLocationLabel())
                    .as("센터 컬럼이 없는 화면을 위해 전체 라벨도 함께 제공한다")
                    .isEqualTo("제1창고 · A구역 · 01랙 · 1단");
        }
    }

    /* ==================================================================
     * 시나리오 1 : 동일 로트가 같은 구역에 들어올 때 수량 합산 (UPDATE)
     * ================================================================== */

    @Test
    @DisplayName("[합산] 동일 로트가 같은 구역에 재입고되면 새 재고 행을 만들지 않고 수량이 합산된다")
    void receive_sameLotSameBin_mergesQuantity() {
        // given : 이미 A-01-01 구역에 해당 로트 30개가 보관 중
        Product product = product(180);
        WarehouseBin bin = bin(BIN_ID, "A-01-01", 500);
        ProductLot existingLot = lot(product, 30);
        Inventory existingInventory = inventory(existingLot, bin, 30);

        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(warehouseBinRepository.findById(BIN_ID)).willReturn(Optional.of(bin));
        given(inventoryRepository.sumQuantityByBinId(BIN_ID)).willReturn(30L);
        given(productLotRepository.findByProduct_ProductIdAndLotNo(PRODUCT_ID, LOT_NO))
                .willReturn(Optional.of(existingLot));
        given(inventoryRepository.findByLot_LotIdAndBin_BinId(LOT_ID, BIN_ID))
                .willReturn(Optional.of(existingInventory));

        // when : 같은 로트번호로 같은 구역에 20개 추가 입고
        InboundResultDto result = inventoryService.receive(
                inboundForm(BIN_ID, LOT_NO, 20), USER_ID, USER_NAME);

        // then : 기존 재고 행의 수량이 30 + 20 = 50 으로 합산된다
        assertThat(existingInventory.getQuantity()).isEqualTo(50);
        assertThat(result.getBinQuantity()).isEqualTo(50);
        assertThat(result.isNewInventory()).isFalse();
        assertThat(result.isNewLot()).isFalse();

        // 로트 전체 수량과 품목 전체 재고도 함께 증가한다
        assertThat(existingLot.getLotQuantity()).isEqualTo(50);
        assertThat(result.getLotQuantity()).isEqualTo(50);
        assertThat(product.getTotalStock()).isEqualTo(120);   // 100 + 20
        assertThat(result.getProductTotalStock()).isEqualTo(120);

        // 새 로트 / 새 재고 행이 생성되어서는 안 된다
        verify(productLotRepository, never()).save(any(ProductLot.class));
        verify(inventoryRepository, never()).save(any(Inventory.class));

        // 입고 이력은 항상 기록된다
        verify(stockMovementRepository).save(any(StockMovement.class));
    }

    /* ==================================================================
     * 시나리오 2 : 새로운 구역에 들어올 때 새 재고 행 생성 (INSERT)
     * ================================================================== */

    @Test
    @DisplayName("[신규] 같은 로트라도 다른 구역에 입고되면 새로운 재고 행이 생성된다")
    void receive_sameLotNewBin_createsNewInventory() {
        // given : 로트는 이미 존재하지만 B-01-01 구역에는 재고가 없다
        Product product = product(180);
        WarehouseBin newBin = bin(OTHER_BIN_ID, "B-01-01", 600);
        ProductLot existingLot = lot(product, 30);

        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(warehouseBinRepository.findById(OTHER_BIN_ID)).willReturn(Optional.of(newBin));
        given(inventoryRepository.sumQuantityByBinId(OTHER_BIN_ID)).willReturn(null);   // 아직 적재 없음
        given(productLotRepository.findByProduct_ProductIdAndLotNo(PRODUCT_ID, LOT_NO))
                .willReturn(Optional.of(existingLot));
        given(inventoryRepository.findByLot_LotIdAndBin_BinId(LOT_ID, OTHER_BIN_ID))
                .willReturn(Optional.empty());
        given(inventoryRepository.save(any(Inventory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        InboundResultDto result = inventoryService.receive(
                inboundForm(OTHER_BIN_ID, LOT_NO, 40), USER_ID, USER_NAME);

        // then : 새로운 재고 행이 저장된다
        ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryRepository).save(captor.capture());

        Inventory saved = captor.getValue();
        assertThat(saved.getLot()).isSameAs(existingLot);
        assertThat(saved.getBin()).isSameAs(newBin);
        assertThat(saved.getQuantity()).isEqualTo(40);

        assertThat(result.isNewInventory()).isTrue();
        assertThat(result.isNewLot()).isFalse();
        assertThat(result.getBinQuantity()).isEqualTo(40);

        // 로트 수량은 구역이 달라도 누적된다 (30 + 40)
        assertThat(existingLot.getLotQuantity()).isEqualTo(70);
        assertThat(product.getTotalStock()).isEqualTo(140);
    }

    @Test
    @DisplayName("[신규] 로트번호가 없는 첫 입고면 로트와 재고가 모두 새로 생성된다")
    void receive_newLotAndNewInventory() {
        // given
        Product product = product(180);
        WarehouseBin bin = bin(BIN_ID, "A-01-01", 500);
        LocalDate manufacturedDate = LocalDate.of(2026, 7, 1);

        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(warehouseBinRepository.findById(BIN_ID)).willReturn(Optional.of(bin));
        given(inventoryRepository.sumQuantityByBinId(BIN_ID)).willReturn(null);
        given(productLotRepository.countByProduct_ProductIdAndManufacturedDate(PRODUCT_ID, manufacturedDate))
                .willReturn(0L);
        given(productLotRepository.findByProduct_ProductIdAndLotNo(anyLong(), any()))
                .willReturn(Optional.empty());
        given(productLotRepository.save(any(ProductLot.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(inventoryRepository.findByLot_LotIdAndBin_BinId(any(), anyLong()))
                .willReturn(Optional.empty());
        given(inventoryRepository.save(any(Inventory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        InboundForm form = inboundForm(BIN_ID, null, 25);
        form.setManufacturedDate(manufacturedDate);

        // when
        InboundResultDto result = inventoryService.receive(form, USER_ID, USER_NAME);

        // then : 로트번호가 자동 부여된다 (L{yyMMdd}-{품목코드}-{순번})
        assertThat(result.isNewLot()).isTrue();
        assertThat(result.isNewInventory()).isTrue();
        assertThat(result.getLotNo()).isEqualTo("L260701-FD-CT-001-01");

        verify(productLotRepository).save(any(ProductLot.class));
        verify(inventoryRepository).save(any(Inventory.class));
    }

    /* ==================================================================
     * 시나리오 2-1 : 입고한 구역이 출고 대상이 아닐 때의 경고
     *
     * 입고 대기 · 검수 구역에 넣은 물건은 검수를 통과하지 않아 출고할 수 없다.
     * 재고 수량은 늘어나는데 출고 가능 재고는 늘어나지 않으므로, 입고 직후에
     * 알려주지 않으면 나중에 출고가 막혔을 때 담당자가 원인을 유통기한에서 찾게 된다.
     * (부족 안내가 제일 먼저 말하는 사유가 그것이다)
     * ================================================================== */

    @Test
    @DisplayName("[경고] 입고 대기 구역에 입고하면 출고 대상이 아니라고 알려준다")
    void receive_intoReceivingBin_flagsNotShippable() {
        InboundResultDto result = receiveInto(bin(BIN_ID, "R-01-01", 500, BinPurpose.RECEIVING));

        assertThat(result.getBinPurpose()).isEqualTo(BinPurpose.RECEIVING);
        assertThat(result.isNotShippableBin())
                .as("검수 전 재고는 출고 후보에서 제외되므로 입고 직후에 알려야 한다")
                .isTrue();
        assertThat(result.getBinId())
                .as("'구역 간 이동' 링크에 넘길 구역 식별자가 있어야 한다")
                .isEqualTo(BIN_ID);
    }

    @Test
    @DisplayName("[경고] 검수 구역도 출고 대상이 아니다")
    void receive_intoInspectionBin_flagsNotShippable() {
        assertThat(receiveInto(bin(BIN_ID, "I-01-01", 500, BinPurpose.INSPECTION))
                .isNotShippableBin()).isTrue();
    }

    @Test
    @DisplayName("[경고] 보관 구역과 출고 대기 구역은 경고하지 않는다")
    void receive_intoShippableBin_doesNotFlag() {
        assertThat(receiveInto(bin(BIN_ID, "A-01-01", 500, BinPurpose.STORAGE))
                .isNotShippableBin())
                .as("보관 구역은 정상 출고 대상이다")
                .isFalse();
        assertThat(receiveInto(bin(BIN_ID, "S-01-01", 500, BinPurpose.SHIPPING))
                .isNotShippableBin())
                .as("출고 대기 구역은 이미 피킹한 물량이라 출고 대상이다")
                .isFalse();
    }

    /**
     * 용도가 비어 있는 구역에서도 터지지 않아야 한다.
     * 같은 이유로 적재 한도 검증이 NPE 로 깨진 적이 있다.
     */
    @Test
    @DisplayName("[경고] 구역 용도가 비어 있으면 경고하지 않는다 (NPE 를 내지 않는다)")
    void receive_intoBinWithoutPurpose_doesNotFlag() {
        assertThat(receiveInto(bin(BIN_ID, "A-01-01", 500)).isNotShippableBin()).isFalse();
    }

    /** 지정한 구역으로 10개 입고하고 결과를 돌려준다 */
    private InboundResultDto receiveInto(WarehouseBin bin) {
        Product product = product(180);
        ProductLot lot = lot(product, 0);

        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(warehouseBinRepository.findById(BIN_ID)).willReturn(Optional.of(bin));
        given(inventoryRepository.sumQuantityByBinId(BIN_ID)).willReturn(0L);
        given(productLotRepository.findByProduct_ProductIdAndLotNo(PRODUCT_ID, LOT_NO))
                .willReturn(Optional.of(lot));
        given(inventoryRepository.findByLot_LotIdAndBin_BinId(LOT_ID, BIN_ID))
                .willReturn(Optional.empty());
        given(inventoryRepository.save(any(Inventory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        return inventoryService.receive(inboundForm(BIN_ID, LOT_NO, 10), USER_ID, USER_NAME);
    }

    /* ==================================================================
     * 시나리오 3 : 유통기한 자동 계산 및 D-Day 계산
     * ================================================================== */

    @Test
    @DisplayName("[유통기한] 신규 로트의 유통기한은 제조일자 + 품목의 유통기한 일수로 자동 계산된다")
    void receive_calculatesExpirationDateFromShelfLifeDays() {
        // given : 유통기한 90일 품목, 제조일자 2026-07-01
        Product product = product(90);
        WarehouseBin bin = bin(BIN_ID, "A-01-01", 500);
        LocalDate manufacturedDate = LocalDate.of(2026, 7, 1);

        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(warehouseBinRepository.findById(BIN_ID)).willReturn(Optional.of(bin));
        given(inventoryRepository.sumQuantityByBinId(BIN_ID)).willReturn(0L);
        given(productLotRepository.findByProduct_ProductIdAndLotNo(PRODUCT_ID, LOT_NO))
                .willReturn(Optional.empty());
        given(productLotRepository.save(any(ProductLot.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(inventoryRepository.findByLot_LotIdAndBin_BinId(any(), anyLong()))
                .willReturn(Optional.empty());
        given(inventoryRepository.save(any(Inventory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        InboundForm form = inboundForm(BIN_ID, LOT_NO, 10);
        form.setManufacturedDate(manufacturedDate);

        // when
        InboundResultDto result = inventoryService.receive(form, USER_ID, USER_NAME);

        // then : 2026-07-01 + 90일 = 2026-09-29
        assertThat(result.getExpirationDate()).isEqualTo(LocalDate.of(2026, 9, 29));

        ArgumentCaptor<ProductLot> captor = ArgumentCaptor.forClass(ProductLot.class);
        verify(productLotRepository).save(captor.capture());
        assertThat(captor.getValue().getExpirationDate()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(captor.getValue().getManufacturedDate()).isEqualTo(manufacturedDate);
    }

    @Test
    @DisplayName("[유통기한] 품목의 유통기한 일수로 만료일을 계산한다")
    void product_calculateExpirationDate() {
        assertThat(product(180).calculateExpirationDate(LocalDate.of(2026, 1, 1)))
                .isEqualTo(LocalDate.of(2026, 6, 30));      // 윤년 아님 : 1/1 + 180일

        assertThat(product(90).calculateExpirationDate(LocalDate.of(2026, 7, 27)))
                .isEqualTo(LocalDate.of(2026, 10, 25));

        assertThat(product(1).calculateExpirationDate(LocalDate.of(2026, 12, 31)))
                .as("연도를 넘어가는 계산도 정확해야 한다")
                .isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    @DisplayName("[D-Day] 유통기한까지 남은 일수가 정확하게 계산된다")
    void productLot_daysUntilExpiration() {
        LocalDate today = LocalDate.of(2026, 7, 27);

        // 30일 뒤 만료 → D-30
        ProductLot after30Days = lotWithExpiration(today.plusDays(30));
        assertThat(after30Days.daysUntilExpiration(today)).isEqualTo(30L);
        assertThat(after30Days.isExpired(today)).isFalse();
        assertThat(after30Days.isExpiringWithin(today, 30))
                .as("경계값 : 30일 이내 조건에 D-30 은 포함된다")
                .isTrue();
        assertThat(after30Days.isExpiringWithin(today, 29))
                .as("경계값 : 29일 이내 조건에 D-30 은 포함되지 않는다")
                .isFalse();

        // 오늘 만료 → D-0 (아직 만료 아님)
        ProductLot today0 = lotWithExpiration(today);
        assertThat(today0.daysUntilExpiration(today)).isZero();
        assertThat(today0.isExpired(today)).isFalse();
        assertThat(today0.isExpiringWithin(today, 30)).isTrue();

        // 3일 전 만료 → -3 (이미 만료)
        ProductLot expired = lotWithExpiration(today.minusDays(3));
        assertThat(expired.daysUntilExpiration(today)).isEqualTo(-3L);
        assertThat(expired.isExpired(today)).isTrue();
        assertThat(expired.isExpiringWithin(today, 30))
                .as("이미 만료된 로트는 임박 대상에서 제외한다")
                .isFalse();

        // 월/연 경계
        ProductLot crossYear = lotWithExpiration(LocalDate.of(2027, 1, 5));
        assertThat(crossYear.daysUntilExpiration(LocalDate.of(2026, 12, 31))).isEqualTo(5L);
    }

    /* ==================================================================
     * 업무 규칙 검증
     * ================================================================== */

    @Test
    @DisplayName("[검증] 구역의 최대 적재 수량을 초과하면 입고가 거부된다")
    void receive_exceedsBinCapacity_throwsException() {
        // given : 최대 500, 현재 480 적재된 구역에 30개 입고 시도
        Product product = product(180);
        WarehouseBin bin = bin(BIN_ID, "A-01-01", 500);

        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(warehouseBinRepository.findById(BIN_ID)).willReturn(Optional.of(bin));
        given(inventoryRepository.sumQuantityByBinId(BIN_ID)).willReturn(480L);

        // when & then
        assertThatThrownBy(() -> inventoryService.receive(
                inboundForm(BIN_ID, LOT_NO, 30), USER_ID, USER_NAME))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("적재 한도를 초과");

        // 재고가 전혀 변경되지 않아야 한다
        assertThat(product.getTotalStock()).isEqualTo(100);
        verify(productLotRepository, never()).save(any(ProductLot.class));
        verify(inventoryRepository, never()).save(any(Inventory.class));
        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test
    @DisplayName("[검증] 사용 중지된 품목에는 입고할 수 없다")
    void receive_inactiveProduct_throwsException() {
        // given
        Product inactive = Product.builder()
                .productId(PRODUCT_ID)
                .productCode("FD-CT-900")
                .name("구형 육성우 사료(단종)")
                .animalType(AnimalType.CATTLE)
                .weightKg(25)
                .price(29000L)
                .totalStock(10)
                .safetyStock(50)
                .shelfLifeDays(180)
                .active(false)
                .build();
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(inactive));

        // when & then
        assertThatThrownBy(() -> inventoryService.receive(
                inboundForm(BIN_ID, LOT_NO, 10), USER_ID, USER_NAME))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("사용 중지된 품목");

        verify(warehouseBinRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("[검증] 사용 중지된 구역에는 입고할 수 없다")
    void receive_inactiveBin_throwsException() {
        // given
        Product product = product(180);
        WarehouseBin inactiveBin = WarehouseBin.builder()
                .binId(BIN_ID)
                .binCode("A-03-01")
                .zone("A")
                .rack("03")
                .binLevel(1)
                .maxCapacity(400)
                .active(false)
                .build();

        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(warehouseBinRepository.findById(BIN_ID)).willReturn(Optional.of(inactiveBin));

        // when & then
        assertThatThrownBy(() -> inventoryService.receive(
                inboundForm(BIN_ID, LOT_NO, 10), USER_ID, USER_NAME))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("사용 중지된 구역");

        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test
    @DisplayName("[검증] 존재하지 않는 품목으로 입고하면 ResourceNotFoundException 이 발생한다")
    void receive_productNotFound_throwsException() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.receive(
                inboundForm(BIN_ID, LOT_NO, 10), USER_ID, USER_NAME))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("존재하지 않는 품목");
    }

    @Test
    @DisplayName("[이력] 입고 시 처리자 정보와 함께 INBOUND 이력이 기록된다")
    void receive_recordsInboundMovement() {
        // given
        Product product = product(180);
        WarehouseBin bin = bin(BIN_ID, "A-01-01", 500);
        ProductLot existingLot = lot(product, 30);
        Inventory existingInventory = inventory(existingLot, bin, 30);

        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(warehouseBinRepository.findById(BIN_ID)).willReturn(Optional.of(bin));
        given(inventoryRepository.sumQuantityByBinId(BIN_ID)).willReturn(30L);
        given(productLotRepository.findByProduct_ProductIdAndLotNo(PRODUCT_ID, LOT_NO))
                .willReturn(Optional.of(existingLot));
        given(inventoryRepository.findByLot_LotIdAndBin_BinId(LOT_ID, BIN_ID))
                .willReturn(Optional.of(existingInventory));

        InboundForm form = inboundForm(BIN_ID, LOT_NO, 15);
        form.setMemo("정기 발주 입고");

        // when
        inventoryService.receive(form, USER_ID, USER_NAME);

        // then
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());

        StockMovement movement = captor.getValue();
        assertThat(movement.getMovementType()).isEqualTo(MovementType.INBOUND);
        assertThat(movement.getQuantity()).isEqualTo(15);
        assertThat(movement.getLot()).isSameAs(existingLot);
        assertThat(movement.getBin()).isSameAs(bin);
        assertThat(movement.getMemo()).isEqualTo("정기 발주 입고");
        assertThat(movement.getUserId()).isEqualTo(USER_ID);
        assertThat(movement.getUserName()).isEqualTo(USER_NAME);
    }

    /* ==================================================================
     * 픽스처
     * ================================================================== */

    private InboundForm inboundForm(Long binId, String lotNo, int quantity) {
        InboundForm form = new InboundForm();
        form.setProductId(PRODUCT_ID);
        form.setBinId(binId);
        form.setLotNo(lotNo);
        form.setManufacturedDate(LocalDate.of(2026, 7, 1));
        form.setQuantity(quantity);
        return form;
    }

    private Product product(int shelfLifeDays) {
        return Product.builder()
                .productId(PRODUCT_ID)
                .productCode("FD-CT-001")
                .name("프리미엄 육성우 배합사료")
                .animalType(AnimalType.CATTLE)
                .weightKg(25)
                .price(32000L)
                .totalStock(100)
                .safetyStock(50)
                .shelfLifeDays(shelfLifeDays)
                .active(true)
                .build();
    }

    /** 용도를 지정하지 않은 구역 (기존 테스트가 쓰던 형태 — 용도 null 도 견뎌야 한다) */
    private WarehouseBin bin(Long binId, String binCode, int maxCapacity) {
        return bin(binId, binCode, maxCapacity, null);
    }

    private WarehouseBin bin(Long binId, String binCode, int maxCapacity, BinPurpose purpose) {
        return WarehouseBin.builder()
                .binId(binId)
                .binCode(binCode)
                .zone(binCode.substring(0, 1))
                .binPurpose(purpose)
                .rack("01")
                .binLevel(1)
                .maxCapacity(maxCapacity)
                .active(true)
                .build();
    }

    /** 센터가 연결된 구역 (재고 현황 조회 검증용) */
    private WarehouseBin binIn(Long binId, String binCode, Center center) {
        return WarehouseBin.builder()
                .binId(binId)
                .binCode(binCode)
                .center(center)
                .zone(binCode.substring(0, 1))
                .rack("01")
                .binLevel(1)
                .maxCapacity(500)
                .active(true)
                .build();
    }

    private Center center(Long centerId, String centerCode, String name) {
        return Center.builder()
                .centerId(centerId)
                .centerCode(centerCode)
                .name(name)
                .region("수도권")
                .active(true)
                .build();
    }

    private ProductLot lot(Product product, int lotQuantity) {
        return ProductLot.builder()
                .lotId(LOT_ID)
                .product(product)
                .lotNo(LOT_NO)
                .manufacturedDate(LocalDate.of(2026, 7, 1))
                .expirationDate(LocalDate.of(2026, 12, 28))
                .lotQuantity(lotQuantity)
                .build();
    }

    private ProductLot lotWithExpiration(LocalDate expirationDate) {
        return ProductLot.builder()
                .lotId(LOT_ID)
                .lotNo(LOT_NO)
                .manufacturedDate(expirationDate.minusDays(180))
                .expirationDate(expirationDate)
                .lotQuantity(10)
                .build();
    }

    private Inventory inventory(ProductLot lot, WarehouseBin bin, int quantity) {
        return Inventory.builder()
                .inventoryId(500L)
                .lot(lot)
                .bin(bin)
                .quantity(quantity)
                .build();
    }
}

