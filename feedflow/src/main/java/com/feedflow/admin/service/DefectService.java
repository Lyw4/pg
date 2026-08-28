package com.feedflow.admin.service;

import com.feedflow.admin.dto.DefectForm;
import com.feedflow.admin.dto.DefectRecordDto;
import com.feedflow.admin.dto.DefectResolveForm;
import com.feedflow.admin.dto.DefectSearchDto;
import com.feedflow.admin.dto.DefectStatRow;
import com.feedflow.admin.dto.LotCandidateDto;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.common.util.Texts;
import com.feedflow.domain.DefectRecord;
import com.feedflow.domain.DefectStage;
import com.feedflow.domain.DefectStatus;
import com.feedflow.domain.DefectType;
import com.feedflow.domain.ProductLot;
import com.feedflow.domain.WarehouseBin;
import com.feedflow.repository.DefectRecordRepository;
import com.feedflow.repository.ProductLotRepository;
import com.feedflow.repository.WarehouseBinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 불량 기록 등록 · 검사 · 처리.
 *
 * <h3>이 서비스가 재고를 바꾸지 않는다</h3>
 * 불량을 등록해도, 폐기로 처리해도 재고 수량은 그대로다. 재고를 줄이는 일은
 * {@link InventoryService#dispose} 하나만 한다.
 * <p>
 * 두 곳에서 재고를 줄이면 언젠가 한쪽만 고치게 된다. 그때 재고는 줄었는데 이력이
 * 없거나, 이력은 있는데 재고가 그대로인 상태가 생기고, 어느 쪽이 맞는지 알 수 없다.
 * 대신 처리 결과에 <b>다음에 할 일</b>({@code followUp})을 붙여 담당자를 폐기 화면으로
 * 보낸다. 사람이 한 번 더 확인하는 대가로 재고 숫자의 출처를 한 곳으로 유지한다.
 *
 * <h3>격리가 출고를 막지 않는다</h3>
 * 출고를 막는 것은 구역 용도다. 검수 구역 · 격리 구역의 재고는 애초에 출고 대상에서
 * 빠진다. 불량 상태로 한 번 더 막으면 규칙이 두 곳에 생기고, 구역만 옮겼는데
 * 여전히 막히거나 그 반대가 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefectService {

    /** 방치로 보는 기준 일수 — 격리 재고는 자리를 차지하면서 출고도 되지 않는다 */
    private static final int STALE_DAYS = 7;

    private static final DateTimeFormatter DEFECT_NO_MONTH = DateTimeFormatter.ofPattern("yyMM");

    private final DefectRecordRepository defectRecordRepository;
    private final ProductLotRepository productLotRepository;
    private final WarehouseBinRepository warehouseBinRepository;

    /* ------------------------------------------------------------------
     * 조회
     * ------------------------------------------------------------------ */

    /**
     * 불량 목록 검색.
     * <p>
     * 목록은 필터를 따르지만 유형 · 단계 · 제조사 집계는 <b>전체</b>를 센다.
     * 상태를 하나 고른 순간 집계까지 그 상태만 남으면 비교 자체가 불가능해진다.
     */
    public DefectSearchDto search(DefectStatus status,
                                  DefectType defectType,
                                  DefectStage stage,
                                  Long centerId) {

        List<DefectRecordDto> rows = defectRecordRepository
                .search(status, defectType, stage, centerId)
                .stream()
                .map(DefectRecordDto::of)
                .toList();

        return DefectSearchDto.of(
                rows,
                toTypeLabels(defectRecordRepository.findStatsByType()),
                toStageLabels(defectRecordRepository.findStatsByStage()),
                defectRecordRepository.findStatsByManufacturer());
    }

    /** 미처리 건수 (대시보드) */
    public long getOpenCount() {
        return defectRecordRepository.countOpen();
    }

    /**
     * 오래 방치된 미처리 건.
     * <p>
     * 격리해 둔 재고는 출고되지 않으면서 자리를 차지한다. 처리를 미루면
     * 창고 공간만 잠식하므로 목록 위에 경고로 띄운다.
     */
    public List<DefectRecordDto> getStaleDefects() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(STALE_DAYS);
        return defectRecordRepository.findStale(threshold)
                .stream()
                .map(DefectRecordDto::of)
                .toList();
    }

    /**
     * 등록 화면의 로트 선택 목록.
     * <p>
     * <b>재고가 남은 로트만 거르지 않는다.</b> 센터 간 이관 중이라 어느 구역에도
     * 올라가 있지 않은 재고에서도 파손이 나고, 이미 폐기한 로트에 뒤늦게 기록을
     * 남겨야 하는 경우도 있다. 재고 기준으로 목록을 줄이면 그런 건을 아예
     * 등록할 수 없다.
     */
    public List<LotCandidateDto> getLotOptions() {
        LocalDate today = LocalDate.now();
        return productLotRepository.findAllWithProduct()
                .stream()
                .map(lot -> LotCandidateDto.of(lot, today))
                .toList();
    }

    /* ------------------------------------------------------------------
     * 등록 · 처리
     * ------------------------------------------------------------------ */

    /**
     * 불량 발견 등록.
     * <p>
     * 관리번호는 자동 발급한다. 담당자가 번호를 직접 입력하게 하면 중복이 나거나
     * 규칙이 어긋난 번호가 섞여 나중에 검색이 안 된다.
     *
     * @param form           등록 폼
     * @param reportedByName 발견자 이름 (스냅샷)
     */
    @Transactional
    public DefectRecordDto register(DefectForm form, String reportedByName) {
        ProductLot lot = productLotRepository.findWithProductById(form.getLotId())
                .orElseThrow(() -> ResourceNotFoundException.ofProductLot(form.getLotId()));

        WarehouseBin bin = null;
        if (form.getBinId() != null) {
            bin = warehouseBinRepository.findWithCenterById(form.getBinId())
                    .orElseThrow(() -> ResourceNotFoundException.ofWarehouseBin(form.getBinId()));
        }

        DefectRecord record = DefectRecord.report(
                nextDefectNo(),
                lot,
                bin,
                form.quantityValue(),
                form.getDefectType(),
                form.getStage(),
                Texts.trimToNull(form.getMemo()),
                reportedByName);

        return DefectRecordDto.of(defectRecordRepository.save(record));
    }

    /**
     * 검사 착수 (격리 → 검사 중).
     * <p>
     * 상태를 바꾸는 것만으로도 의미가 있다. 격리 상태로 며칠째 남아 있는 건과
     * 담당자가 들여다보고 있는 건은 다르게 다뤄야 한다.
     */
    @Transactional
    public DefectRecordDto startInspection(Long defectId, String memo) {
        DefectRecord record = findRecord(defectId);
        record.startInspection(Texts.trimToNull(memo));
        return DefectRecordDto.of(record);
    }

    /**
     * 처리 완료.
     * <p>
     * 처리 방법을 고르지 않았으면 검사 착수로만 처리한다. 같은 화면에서 두 가지를
     * 하기 때문이다.
     * <p>
     * 반품 · 폐기로 처리해도 <b>재고는 줄지 않는다.</b> 결과 DTO 의
     * {@code followUp} 이 폐기 화면으로 안내한다.
     *
     * @param form           처리 폼
     * @param resolvedByName 처리자 이름 (스냅샷)
     */
    @Transactional
    public DefectRecordDto resolve(DefectResolveForm form, String resolvedByName) {
        DefectRecord record = findRecord(form.getDefectId());

        if (form.isResolveRequest()) {
            record.resolve(form.getResolution(),
                    Texts.trimToNull(form.getResolutionMemo()),
                    resolvedByName);
        } else {
            record.startInspection(Texts.trimToNull(form.getResolutionMemo()));
        }

        return DefectRecordDto.of(record);
    }

    /* ------------------------------------------------------------------
     * 내부
     * ------------------------------------------------------------------ */

    private DefectRecord findRecord(Long defectId) {
        if (defectId == null) {
            throw new ResourceNotFoundException("불량 건을 찾을 수 없습니다.");
        }
        return defectRecordRepository.findWithDetailById(defectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "불량 기록을 찾을 수 없습니다. (id=" + defectId + ")"));
    }

    /**
     * 관리번호 발급 — {@code DF-yyMM-NNN}.
     * <p>
     * 월별로 순번을 다시 시작한다. 연속 번호로 쭉 늘리면 "이번 달에 몇 건이었나" 를
     * 번호만 보고 알 수 없고, 자리수가 계속 늘어난다.
     */
    private String nextDefectNo() {
        String prefix = "DF-" + LocalDate.now().format(DEFECT_NO_MONTH) + "-";
        int next = defectRecordRepository.findMaxDefectNo(prefix)
                .map(last -> parseSequence(last, prefix) + 1)
                .orElse(1);
        return prefix + String.format("%03d", next);
    }

    /**
     * 관리번호 꼬리의 순번을 읽는다.
     * <p>
     * 형식이 어긋난 값이 섞여 있어도 등록을 막지 않는다. 번호 하나 때문에
     * 불량 등록이 실패하면 담당자는 기록을 아예 남기지 않는 쪽을 택한다.
     */
    private int parseSequence(String defectNo, String prefix) {
        try {
            return Integer.parseInt(defectNo.substring(prefix.length()));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return 0;
        }
    }

    /** 유형 집계의 enum 이름을 화면 라벨로 바꾼다 */
    private List<DefectStatRow> toTypeLabels(List<DefectStatRow> stats) {
        List<DefectStatRow> labeled = new ArrayList<>();
        for (DefectStatRow stat : stats) {
            labeled.add(new DefectStatRow(
                    describeType(stat.label()), stat.defectCount(), stat.quantity()));
        }
        return labeled;
    }

    /** 단계 집계의 enum 이름을 화면 라벨로 바꾼다 */
    private List<DefectStatRow> toStageLabels(List<DefectStatRow> stats) {
        List<DefectStatRow> labeled = new ArrayList<>();
        for (DefectStatRow stat : stats) {
            labeled.add(new DefectStatRow(
                    describeStage(stat.label()), stat.defectCount(), stat.quantity()));
        }
        return labeled;
    }

    private String describeType(String name) {
        try {
            return DefectType.valueOf(name).getDescription();
        } catch (IllegalArgumentException | NullPointerException e) {
            return Texts.defaultIfBlank(name, "기타");
        }
    }

    private String describeStage(String name) {
        try {
            return DefectStage.valueOf(name).getDescription();
        } catch (IllegalArgumentException | NullPointerException e) {
            return Texts.defaultIfBlank(name, "기타");
        }
    }
}
