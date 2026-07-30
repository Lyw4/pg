package com.feedflow.admin.service;

import com.feedflow.common.util.Texts;
import com.feedflow.admin.dto.WarehouseBinDto;
import com.feedflow.admin.dto.WarehouseBinForm;
import com.feedflow.common.exception.DuplicateCodeException;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.domain.Warehouse;
import com.feedflow.domain.WarehouseBin;
import com.feedflow.repository.WarehouseBinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public List<WarehouseBinDto> getBins(Warehouse warehouse, String zone, Boolean active) {
        return warehouseBinRepository.search(warehouse, Texts.trimToNull(zone), active).stream()
                .map(WarehouseBinDto::from)
                .toList();
    }

    public WarehouseBinForm getBinForm(Long binId) {
        return WarehouseBinForm.from(findBin(binId));
    }

    public List<String> getZones() {
        return warehouseBinRepository.findDistinctZones();
    }

    /**
     * 입고 · 이동 화면 등의 구역 선택 목록 (사용 중인 구역만).
     * <p>
     * <b>창고 순 → 구역 코드 순</b>으로 정렬한다. 구역 코드만으로 정렬하면
     * 제2창고의 {@code COLD-01} 이 제1창고의 {@code C-02} 와 {@code D-01} 사이에 끼어
     * 창고가 뒤섞인 목록이 된다.
     */
    public List<WarehouseBinDto> getActiveBins() {
        return warehouseBinRepository.findByActiveTrueOrderByWarehouseAscBinCodeAsc().stream()
                .map(WarehouseBinDto::from)
                .toList();
    }

    /**
     * 사용 중인 구역을 <b>창고별로 묶은</b> 선택 목록.
     * <p>
     * 화면에서 {@code <optgroup>} 으로 렌더링해 창고 경계를 눈으로 구분할 수 있게 한다.
     * 정렬 순서를 유지해야 하므로 {@link LinkedHashMap} 으로 모은다.
     * (일반 {@code HashMap} 은 키 순서를 보장하지 않아 창고가 뒤바뀔 수 있다)
     */
    public Map<Warehouse, List<WarehouseBinDto>> getActiveBinsByWarehouse() {
        return getActiveBins().stream()
                .collect(Collectors.groupingBy(
                        WarehouseBinDto::getWarehouse,
                        LinkedHashMap::new,
                        Collectors.toList()));
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
                .warehouse(form.getWarehouse())
                .zone(Texts.code(form.getZone()))
                .binPurpose(form.getBinPurpose())
                .rack(Texts.trim(form.getRack()))
                .binLevel(form.getBinLevel())
                .maxCapacity(form.getMaxCapacity())
                .posX(form.getPosX())
                .posY(form.getPosY())
                .posWidth(form.getPosWidth())
                .posHeight(form.getPosHeight())
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
                form.getWarehouse(),
                Texts.code(form.getZone()),
                form.getBinPurpose(),
                Texts.trim(form.getRack()),
                form.getBinLevel(),
                form.getMaxCapacity(),
                Texts.trim(form.getMemo()));
        bin.updateLayout(form.getPosX(), form.getPosY(), form.getPosWidth(), form.getPosHeight());
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
