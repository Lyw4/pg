package com.feedflow.admin.service;

import com.feedflow.admin.dto.CenterDto;
import com.feedflow.admin.dto.WarehouseBinDto;
import com.feedflow.admin.dto.WarehouseBinForm;
import com.feedflow.common.exception.DuplicateCodeException;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.common.util.Texts;
import com.feedflow.domain.Center;
import com.feedflow.domain.WarehouseBin;
import com.feedflow.repository.CenterRepository;
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
 * <p>
 * 구역은 반드시 하나의 물류센터에 속한다. 센터는 {@code Warehouse} enum 이었다가
 * 전국 확장을 위해 {@link Center} 엔티티로 승격되었다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseBinService {

    private final WarehouseBinRepository warehouseBinRepository;
    private final CenterRepository centerRepository;

    /* ------------------------------------------------------------------
     * 조회
     * ------------------------------------------------------------------ */

    /**
     * 구역 목록 검색.
     *
     * @param centerId 센터 (null 이면 전체)
     */
    public List<WarehouseBinDto> getBins(Long centerId, String zone, Boolean active) {
        return warehouseBinRepository.search(centerId, Texts.trimToNull(zone), active).stream()
                .map(WarehouseBinDto::from)
                .toList();
    }

    public WarehouseBinForm getBinForm(Long binId) {
        return WarehouseBinForm.from(findBin(binId));
    }

    public List<String> getZones() {
        return warehouseBinRepository.findDistinctZones();
    }

    /** 화면 선택 상자 · 도면 탭 구성용 센터 목록 (운영 중인 센터만) */
    public List<CenterDto> getActiveCenters() {
        return centerRepository.findByActiveTrueOrderByCenterCodeAsc().stream()
                .map(CenterDto::from)
                .toList();
    }

    /**
     * 입고 · 이동 화면 등의 구역 선택 목록 (사용 중인 구역만).
     * <p>
     * <b>센터 순 → 구역 코드 순</b>으로 정렬한다. 구역 코드만으로 정렬하면
     * 제2창고의 {@code COLD-01} 이 제1창고의 {@code C-02} 와 {@code D-01} 사이에 끼어
     * 센터가 뒤섞인 목록이 된다.
     */
    public List<WarehouseBinDto> getActiveBins() {
        return warehouseBinRepository.findActiveBinsForSelection().stream()
                .map(WarehouseBinDto::from)
                .toList();
    }

    /**
     * 사용 중인 구역을 <b>센터별로 묶은</b> 선택 목록.
     * <p>
     * 화면에서 {@code <optgroup>} 으로 렌더링해 센터 경계를 눈으로 구분할 수 있게 한다.
     * 정렬 순서를 유지해야 하므로 {@link LinkedHashMap} 으로 모은다.
     * (일반 {@code HashMap} 은 키 순서를 보장하지 않아 센터가 뒤바뀔 수 있다)
     * <p>
     * 키를 센터명(문자열)으로 쓰는 이유는 {@code optgroup} 라벨이 곧 센터명이고,
     * 엔티티나 DTO 를 키로 두면 {@code equals}/{@code hashCode} 동작에 묶이기 때문이다.
     */
    public Map<String, List<WarehouseBinDto>> getActiveBinsByCenter() {
        return getActiveBins().stream()
                .collect(Collectors.groupingBy(
                        WarehouseBinDto::getCenterName,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    public long countActiveBins() {
        return warehouseBinRepository.countByActive(true);
    }

    /**
     * 선택한 구역이 선택한 센터 밖에 있는지 판정한다.
     * <p>
     * 검색 화면은 센터와 구역을 <b>독립된 select 두 개</b>로 받는다. 구역 목록은 전국을
     * 보여주므로 "제1창고 + 제2창고의 COLD-01" 처럼 서로 모순되는 조합을 고를 수 있고,
     * 그러면 조건을 만족하는 재고가 없어 결과가 조용히 0건이 된다.
     * <p>
     * 0건 화면만 보면 <b>재고가 없는 것인지 조건이 잘못된 것인지 구분할 수 없다.</b>
     * 화면에서 원인을 안내할 수 있도록 이 판정을 제공한다.
     * <p>
     * 구역 select 을 센터에 따라 좁히는 방식(JS)을 쓰지 않은 이유는, 그러면 URL 로 직접
     * 들어온 요청이나 북마크한 조건에서는 여전히 모순이 발생하는데 안내가 없기 때문이다.
     * 서버가 판정하면 어느 경로로 들어와도 같은 안내가 나간다.
     *
     * @return 센터와 구역을 모두 선택했고 그 구역이 다른 센터에 속할 때만 true
     */
    public boolean isBinOutsideCenter(Long centerId, Long binId) {
        if (centerId == null || binId == null) {
            return false;
        }
        return warehouseBinRepository.findWithCenterById(binId)
                .map(bin -> !centerId.equals(bin.centerId()))
                .orElse(false);
    }

    /* ------------------------------------------------------------------
     * 등록 / 수정
     * ------------------------------------------------------------------ */

    /**
     * 창고 구역 등록.
     *
     * @throws DuplicateCodeException    구역 코드가 이미 존재하는 경우
     * @throws ResourceNotFoundException 지정한 센터가 없는 경우
     */
    @Transactional
    public Long create(WarehouseBinForm form) {
        String binCode = Texts.code(form.getBinCode());

        if (warehouseBinRepository.existsByBinCode(binCode)) {
            throw DuplicateCodeException.ofBinCode(binCode);
        }

        WarehouseBin bin = WarehouseBin.builder()
                .binCode(binCode)
                .center(findCenter(form.getCenterId()))
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
     * @throws ResourceNotFoundException 구역 또는 센터가 존재하지 않는 경우
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
                findCenter(form.getCenterId()),
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

    /**
     * 구역 조회.
     * <p>
     * 센터를 함께 읽는다. 수정 폼과 목록이 센터명을 표시하므로 지연 로딩으로 두면
     * 호출마다 쿼리가 한 번 더 나간다.
     */
    private WarehouseBin findBin(Long binId) {
        return warehouseBinRepository.findWithCenterById(binId)
                .orElseThrow(() -> ResourceNotFoundException.ofWarehouseBin(binId));
    }

    private Center findCenter(Long centerId) {
        return centerRepository.findById(centerId)
                .orElseThrow(() -> ResourceNotFoundException.ofCenter(centerId));
    }
}
