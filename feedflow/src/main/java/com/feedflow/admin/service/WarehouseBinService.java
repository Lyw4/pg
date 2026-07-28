package com.feedflow.admin.service;

import com.feedflow.common.util.Texts;
import com.feedflow.admin.dto.WarehouseBinDto;
import com.feedflow.admin.dto.WarehouseBinForm;
import com.feedflow.common.exception.DuplicateCodeException;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.domain.WarehouseBin;
import com.feedflow.repository.WarehouseBinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 창고 구역(기준 정보) 관리 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseBinService {

    private final WarehouseBinRepository warehouseBinRepository;

    /* ------------------------------------------------------------------
     * 조회
     * ------------------------------------------------------------------ */

    public List<WarehouseBinDto> getBins(String zone, Boolean active) {
        return warehouseBinRepository.search(Texts.trimToNull(zone), active).stream()
                .map(WarehouseBinDto::from)
                .toList();
    }

    public WarehouseBinForm getBinForm(Long binId) {
        return WarehouseBinForm.from(findBin(binId));
    }

    public List<String> getZones() {
        return warehouseBinRepository.findDistinctZones();
    }

    /** 입고 화면 등의 구역 선택 목록 (사용 중인 구역만) */
    public List<WarehouseBinDto> getActiveBins() {
        return warehouseBinRepository.findByActiveTrueOrderByBinCodeAsc().stream()
                .map(WarehouseBinDto::from)
                .toList();
    }

    public long countActiveBins() {
        return warehouseBinRepository.countByActive(true);
    }

    /* ------------------------------------------------------------------
     * 등록 / 수정
     * ------------------------------------------------------------------ */

    /**
     * 창고 구역 등록.
     *
     * @throws DuplicateCodeException 구역 코드가 이미 존재하는 경우
     */
    @Transactional
    public Long create(WarehouseBinForm form) {
        String binCode = Texts.code(form.getBinCode());

        if (warehouseBinRepository.existsByBinCode(binCode)) {
            throw DuplicateCodeException.ofBinCode(binCode);
        }

        WarehouseBin bin = WarehouseBin.builder()
                .binCode(binCode)
                .zone(Texts.code(form.getZone()))
                .rack(Texts.trim(form.getRack()))
                .binLevel(form.getBinLevel())
                .maxCapacity(form.getMaxCapacity())
                .memo(Texts.trim(form.getMemo()))
                .active(form.isActive())
                .build();

        return warehouseBinRepository.save(bin).getBinId();
    }

    /**
     * 창고 구역 수정.
     *
     * @throws ResourceNotFoundException 구역이 존재하지 않는 경우
     * @throws DuplicateCodeException    변경한 구역 코드가 다른 구역에서 이미 사용 중인 경우
     */
    @Transactional
    public void update(Long binId, WarehouseBinForm form) {
        WarehouseBin bin = findBin(binId);
        String binCode = Texts.code(form.getBinCode());

        if (warehouseBinRepository.existsByBinCodeAndBinIdNot(binCode, binId)) {
            throw DuplicateCodeException.ofBinCode(binCode);
        }

        bin.updateMasterData(
                binCode,
                Texts.code(form.getZone()),
                Texts.trim(form.getRack()),
                form.getBinLevel(),
                form.getMaxCapacity(),
                Texts.trim(form.getMemo()));
        bin.changeActive(form.isActive());
    }

    @Transactional
    public String changeActive(Long binId, boolean active) {
        WarehouseBin bin = findBin(binId);
        bin.changeActive(active);
        return bin.getBinCode();
    }

    /* ------------------------------------------------------------------
     * 내부 헬퍼
     * ------------------------------------------------------------------ */

    private WarehouseBin findBin(Long binId) {
        return warehouseBinRepository.findById(binId)
                .orElseThrow(() -> ResourceNotFoundException.ofWarehouseBin(binId));
    }
}
