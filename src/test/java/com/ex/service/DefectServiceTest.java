package com.ex.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ex.entity.DefectRecord;
import com.ex.entity.DefectRecord.DefectType;
import com.ex.entity.DefectRecord.OccurrenceStage;
import com.ex.entity.DefectRecord.ResolutionType;
import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.repository.DefectRecordRepository;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.StockLogRepository;

class DefectServiceTest {

    private DefectRecordRepository defectRepository;
    private ProductLotRepository lotRepository;
    private StockLogRepository stockLogRepository;
    private InventoryService inventoryService;
    private WmsStockCoordinator wmsStockCoordinator;
    private DefectService service;

    @BeforeEach
    void setUp() {
        defectRepository = mock(DefectRecordRepository.class);
        lotRepository = mock(ProductLotRepository.class);
        stockLogRepository = mock(StockLogRepository.class);
        inventoryService = mock(InventoryService.class);
		wmsStockCoordinator = mock(WmsStockCoordinator.class);
        service = new DefectService(
                defectRepository,
				lotRepository,
				stockLogRepository,
				inventoryService,
				wmsStockCoordinator);
    }

    @Test
    void registerRejectsQuantityGreaterThanAvailableLotStock() {
        ProductLot lot = mock(ProductLot.class);
        when(lot.getLotQuantity()).thenReturn(3);
        when(lotRepository.findById(1L)).thenReturn(Optional.of(lot));
        when(inventoryService.availableLotStock(lot)).thenReturn(3);

        assertThrows(IllegalStateException.class, () -> service.register(
                1L, 4, DefectType.DAMAGE, OccurrenceStage.STORAGE,
                "포장 파손", "김관리", LocalDateTime.now()));

        verify(lot, never()).changeQuantity(-4);
        verify(defectRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reworkResolutionReturnsQuarantinedQuantityToStock() {
        Product product = mock(Product.class);
        ProductLot lot = mock(ProductLot.class);
        when(lot.getProduct()).thenReturn(product);
        DefectRecord record = new DefectRecord(
                lot, 5, DefectType.SPECIFICATION, OccurrenceStage.RECEIVING,
                "중량 편차", "김관리", LocalDateTime.now());
        when(defectRepository.findById(10L)).thenReturn(Optional.of(record));

        service.resolve(10L, ResolutionType.REWORK, "이검사", "재포장 완료");

        verify(lot).changeQuantity(5);
        verify(product).changeStock(5);
        verify(stockLogRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void disposalDoesNotReturnQuantityToStock() {
        ProductLot lot = mock(ProductLot.class);
        DefectRecord record = new DefectRecord(
                lot, 2, DefectType.CONTAMINATION, OccurrenceStage.STORAGE,
                "수분 오염", "김관리", LocalDateTime.now());
        when(defectRepository.findById(11L)).thenReturn(Optional.of(record));

        service.resolve(11L, ResolutionType.DISPOSAL, "이검사", "폐기 승인");

        verify(lot, never()).changeQuantity(2);
        verify(stockLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
