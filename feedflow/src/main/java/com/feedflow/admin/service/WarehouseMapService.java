package com.feedflow.admin.service;

import com.feedflow.admin.dto.BinDetailDto;
import com.feedflow.admin.dto.InventoryDto;
import com.feedflow.admin.dto.WarehouseBinMapDto;
import com.feedflow.admin.dto.WarehouseFacilityDto;
import com.feedflow.admin.dto.WarehouseFloorPlanDto;
import com.feedflow.admin.dto.WarehouseMapRow;
import com.feedflow.admin.dto.WarehouseMapSummaryDto;
import com.feedflow.admin.dto.WarehouseMapZoneDto;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.repository.InventoryRepository;
import com.feedflow.repository.WarehouseBinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 창고 2D 평면도 조회 서비스.
 *
 * <h3>배치 방식</h3>
 * 구역마다 저장된 <b>좌표(posX, posY)와 크기(posWidth, posHeight)</b> 로 자유 배치한다.
 * 랙/단 격자에 억지로 맞추지 않기 때문에 통로 · 대형 구역 · 비어 있는 공간을
 * 실제 창고 도면처럼 표현할 수 있다.
 * <p>
 * 출입구 · 벽 · 검수실은 재고를 보관하지 않는 건물 구조물이라 DB 가 아니라
 * {@link WarehouseFacilityDto#forCenter} 상수로 관리한다.
 *
 * <p>모두 조회 전용이다. 재고를 변경하지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseMapService {

    private final WarehouseBinRepository warehouseBinRepository;
    private final InventoryRepository inventoryRepository;

    /* ------------------------------------------------------------------
     * 평면도 조회
     * ------------------------------------------------------------------ */

    /**
     * 센터 한 곳의 평면도를 조회한다.
     *
     * @param centerId 조회할 센터 (null 이면 전체 센터의 구역이 한 도면에 섞이므로 권장하지 않음)
     * @param today    D-Day 계산 기준일
     */
    public WarehouseFloorPlanDto getFloorPlan(Long centerId, LocalDate today) {
        List<WarehouseBinMapDto> bins = warehouseBinRepository.findWarehouseMapRows(centerId).stream()
                .map(row -> WarehouseBinMapDto.of(row, today))
                .toList();

        return WarehouseFloorPlanDto.builder()
                .bins(bins)
                .facilities(WarehouseFacilityDto.forCenter(centerId))
                .zones(toZoneSummaries(bins))
                .summary(WarehouseMapSummaryDto.of(bins))
                .build();
    }

    /* ------------------------------------------------------------------
     * 구역 상세 (모달)
     * ------------------------------------------------------------------ */

    /**
     * 특정 구역에 보관 중인 재고 상세.
     * <p>
     * 어떤 품목의 어느 로트가 몇 개 있는지, 유통기한이 임박한 순으로 내려준다.
     *
     * @throws ResourceNotFoundException 구역이 존재하지 않는 경우
     */
    public BinDetailDto getBinDetail(Long binId, LocalDate today) {
        WarehouseMapRow row = warehouseBinRepository.findWarehouseMapRowByBinId(binId)
                .orElseThrow(() -> ResourceNotFoundException.ofWarehouseBin(binId));

        // 구역을 지정했으므로 센터 조건은 불필요하다 (구역 하나는 센터 하나에 속한다)
        List<InventoryDto> inventories = inventoryRepository.search(null, null, binId, null).stream()
                .map(inventory -> InventoryDto.of(inventory, today))
                .toList();

        return BinDetailDto.builder()
                .bin(WarehouseBinMapDto.of(row, today))
                .inventories(inventories)
                .build();
    }

    /* ------------------------------------------------------------------
     * 내부 헬퍼
     * ------------------------------------------------------------------ */

    /**
     * 구역(Zone) 별 요약을 만든다.
     * <p>
     * 도면 위에 구역 이름을 큰 글자로 겹쳐 표시하기 위해 좌표 경계 상자도 함께 계산한다.
     * 조회 순서(posY → posX)를 유지해야 도면 위에서 아래로 읽는 순서와 맞으므로
     * {@link LinkedHashMap} 을 쓴다.
     */
    private List<WarehouseMapZoneDto> toZoneSummaries(List<WarehouseBinMapDto> bins) {
        Map<String, List<WarehouseBinMapDto>> grouped = new LinkedHashMap<>();
        for (WarehouseBinMapDto bin : bins) {
            grouped.computeIfAbsent(bin.getZone(), key -> new ArrayList<>()).add(bin);
        }

        List<WarehouseMapZoneDto> zones = new ArrayList<>();
        for (Map.Entry<String, List<WarehouseBinMapDto>> entry : grouped.entrySet()) {
            zones.add(WarehouseMapZoneDto.of(entry.getKey(), entry.getValue()));
        }
        return zones;
    }
}
