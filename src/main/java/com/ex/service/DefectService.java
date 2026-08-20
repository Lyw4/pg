package com.ex.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.DefectRecord;
import com.ex.entity.DefectRecord.DefectStatus;
import com.ex.entity.DefectRecord.DefectType;
import com.ex.entity.DefectRecord.OccurrenceStage;
import com.ex.entity.DefectRecord.ResolutionType;
import com.ex.entity.ProductLot;
import com.ex.entity.StockLog;
import com.ex.entity.StockLog.ChangeType;
import com.ex.repository.DefectRecordRepository;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.StockLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefectService {

    public record DefectStat(String label, long count, int quantity) {
    }

    public record DefectAnalytics(
            List<DefectRecord> staleRecords,
            List<DefectStat> typeStats,
            List<DefectStat> stageStats,
            List<DefectStat> manufacturerStats,
            long resolvedCount,
            int openQuantity) {
    }

    private final DefectRecordRepository defectRepository;
    private final ProductLotRepository lotRepository;
    private final StockLogRepository stockLogRepository;
    private final SellableStockQuery sellableStockQuery;
    private final InventoryService inventoryService;
	private final WmsStockCoordinator wmsStockCoordinator;

    public List<DefectRecord> records() {
        return defectRepository.findAllByOrderByCreatedAtDesc();
    }

    public long unresolvedCount() {
        return defectRepository.countByStatusNot(DefectStatus.RESOLVED);
    }

    public List<DefectRecord> recordsForLot(Long lotId) {
        return defectRepository.findByLotLotIdOrderByCreatedAtDesc(lotId);
    }

    public DefectAnalytics analytics() {
        List<DefectRecord> records = records();
        LocalDateTime staleThreshold = LocalDateTime.now().minusDays(7);
        List<DefectRecord> stale = records.stream()
                .filter(record -> record.getStatus() != DefectStatus.RESOLVED)
                .filter(record -> record.getCreatedAt() != null
                        && record.getCreatedAt().isBefore(staleThreshold))
                .toList();
        return new DefectAnalytics(
                stale,
                stats(records, record -> record.getDefectType().getLabel()),
                stats(records, record -> record.getOccurrenceStage().getLabel()),
                stats(records, record -> record.getLot().getProduct()
                        .getManufacturer().getCompanyName()),
                records.stream().filter(record -> record.getStatus() == DefectStatus.RESOLVED).count(),
                records.stream().filter(record -> record.getStatus() != DefectStatus.RESOLVED)
                        .mapToInt(DefectRecord::getQuantity).sum());
    }

    private List<DefectStat> stats(
            List<DefectRecord> records,
            java.util.function.Function<DefectRecord, String> labelExtractor) {
        Map<String, List<DefectRecord>> grouped = new LinkedHashMap<>();
        records.forEach(record -> grouped.computeIfAbsent(
                labelExtractor.apply(record), ignored -> new ArrayList<>()).add(record));
        return grouped.entrySet().stream()
                .map(entry -> new DefectStat(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream().mapToInt(DefectRecord::getQuantity).sum()))
                .sorted(Comparator.comparingLong(DefectStat::count).reversed())
                .toList();
    }

    @Transactional
    public void register(Long lotId, int quantity, DefectType defectType,
            OccurrenceStage occurrenceStage, String description, String reporter,
            LocalDateTime occurredAt) {
        if (quantity <= 0) throw new IllegalArgumentException("불량 수량은 1개 이상이어야 합니다.");
        requireText(description, "불량 상세 내용을 입력해 주세요.");
        requireText(reporter, "등록자를 입력해 주세요.");

        ProductLot lot = findLot(lotId);
        // 아래 재고 차감이 판매 구역에서만 빼기 때문에 검사도 같은 기준이어야
        // 합니다. LOT 총잔량으로 판단하면 검수·입고 대기 구역 재고까지 포함되어
        // 주문이 예약한 판매 구역 재고를 불량 격리가 가져갈 수 있습니다.
        int sellable = sellableStockQuery.sellableByLotIds(List.of(lotId));
        if (sellable < quantity) {
            throw new IllegalStateException(
                    "주문 예약분을 제외한 가용 재고보다 불량 수량이 많습니다. "
                            + "요청 " + quantity + "개 / 가능 " + sellable + "개");
        }

        lot.changeQuantity(-quantity);
        lot.getProduct().changeStock(-quantity);
		wmsStockCoordinator.adjust(
				lot,
				-quantity,
				null,
				"불량 격리: " + description.trim(),
				reporter.trim());
		inventoryService.synchronizeWarehouseStock(lot.getProduct());
        DefectRecord record = defectRepository.save(new DefectRecord(
                lot, quantity, defectType, occurrenceStage,
                description.trim(), reporter.trim(), occurredAt));
        stockLogRepository.save(new StockLog(
                lot, 1L, ChangeType.DEFECT, -quantity,
                "불량 격리 " + record.getDefectNo() + ": " + description.trim()));
    }

    /*
     * 고객에게 출고되었다가 회수된 상품은 현재고에 포함되지 않으므로
     * 재고 차감 없이 곧바로 격리 불량 건으로 등록합니다.
     */
    @Transactional
    public void registerReturned(Long lotId, int quantity, DefectType defectType,
            String description, String reporter) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("회수 불량 수량은 1개 이상이어야 합니다.");
        }
        requireText(description, "회수 불량 상세 내용을 입력해 주세요.");
        requireText(reporter, "회수 검수 담당자를 입력해 주세요.");

        ProductLot lot = findLot(lotId);
        defectRepository.save(new DefectRecord(
                lot, quantity, defectType, OccurrenceStage.RETURNED,
                description.trim(), reporter.trim(), LocalDateTime.now()));
    }

    @Transactional
    public void startInspection(Long defectId) {
        findRecord(defectId).startInspection();
    }

    @Transactional
    public void resolve(Long defectId, ResolutionType resolutionType,
            String processor, String note) {
        requireText(processor, "처리 담당자를 입력해 주세요.");
        requireText(note, "처리 내용을 입력해 주세요.");

        DefectRecord record = findRecord(defectId);
        record.resolve(resolutionType, processor.trim(), note.trim());

        if (resolutionType == ResolutionType.REWORK
                || resolutionType == ResolutionType.CONCESSION) {
            ProductLot lot = record.getLot();
            lot.changeQuantity(record.getQuantity());
            lot.getProduct().changeStock(record.getQuantity());
			wmsStockCoordinator.adjust(
					lot,
					record.getQuantity(),
					null,
					"불량 처리 복원: " + resolutionType.getLabel(),
					processor.trim());
			inventoryService.synchronizeWarehouseStock(lot.getProduct());
            stockLogRepository.save(new StockLog(
                    lot, 1L, ChangeType.DEFECT_RECOVERY, record.getQuantity(),
                    "불량 처리 " + record.getDefectNo() + ": " + resolutionType.getLabel()));
        }
    }

    private DefectRecord findRecord(Long id) {
        return defectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("불량 내역을 찾을 수 없습니다."));
    }

    private ProductLot findLot(Long id) {
        return lotRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("LOT를 찾을 수 없습니다."));
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }
}
