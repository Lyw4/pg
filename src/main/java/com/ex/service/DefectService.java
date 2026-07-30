package com.ex.service;

import java.time.LocalDateTime;
import java.util.List;

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

    private final DefectRecordRepository defectRepository;
    private final ProductLotRepository lotRepository;
    private final StockLogRepository stockLogRepository;
    private final InventoryService inventoryService;

    public List<DefectRecord> records() {
        return defectRepository.findAllByOrderByCreatedAtDesc();
    }

    public long unresolvedCount() {
        return defectRepository.countByStatusNot(DefectStatus.RESOLVED);
    }

    public List<DefectRecord> recordsForLot(Long lotId) {
        return defectRepository.findByLotLotIdOrderByCreatedAtDesc(lotId);
    }

    @Transactional
    public void register(Long lotId, int quantity, DefectType defectType,
            OccurrenceStage occurrenceStage, String description, String reporter,
            LocalDateTime occurredAt) {
        if (quantity <= 0) throw new IllegalArgumentException("불량 수량은 1개 이상이어야 합니다.");
        requireText(description, "불량 상세 내용을 입력해 주세요.");
        requireText(reporter, "등록자를 입력해 주세요.");

        ProductLot lot = findLot(lotId);
        if (inventoryService.availableLotStock(lot) < quantity) {
            throw new IllegalStateException("해당 LOT의 가용 재고보다 불량 수량이 많습니다.");
        }

        lot.changeQuantity(-quantity);
        lot.getProduct().changeStock(-quantity);
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
