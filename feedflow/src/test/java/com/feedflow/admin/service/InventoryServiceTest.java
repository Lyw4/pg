package com.feedflow.admin.service;

import com.feedflow.domain.AnimalType;
import com.feedflow.admin.dto.InboundForm;
import com.feedflow.admin.dto.InboundResultDto;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private WarehouseBin bin(Long binId, String binCode, int maxCapacity) {
        return WarehouseBin.builder()
                .binId(binId)
                .binCode(binCode)
                .zone(binCode.substring(0, 1))
                .rack("01")
                .binLevel(1)
                .maxCapacity(maxCapacity)
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

