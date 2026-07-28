package com.feedflow.admin.service;

import com.feedflow.admin.dto.BinDetailDto;
import com.feedflow.admin.dto.InventoryDto;
import com.feedflow.admin.dto.WarehouseBinMapDto;
import com.feedflow.admin.dto.WarehouseMapLevelDto;
import com.feedflow.admin.dto.WarehouseMapRow;
import com.feedflow.admin.dto.WarehouseMapSummaryDto;
import com.feedflow.admin.dto.WarehouseMapZoneDto;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.common.util.Texts;
import com.feedflow.repository.InventoryRepository;
import com.feedflow.repository.WarehouseBinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 창고 2D 도면 맵 조회 서비스.
 *
 * <h3>도면 배치 규칙</h3>
 * 실제 창고를 보는 것처럼 <b>랙을 가로축, 단을 세로축</b> 으로 배치한다.
 * 단은 높은 층이 위로 오도록 역순으로 그린다 (3단 → 2단 → 1단).
 * 좌표는 서버에서 계산해 내려주므로 화면은 CSS Grid 에 값만 꽂아 넣으면 된다.
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
     * 도면 조회
     * ------------------------------------------------------------------ */

    /**
     * 구역 그룹(Zone) 단위로 묶은 도면 데이터.
     * <p>
     * 그룹 안에서 랙/단 좌표를 계산해 CSS Grid 배치 정보까지 채워준다.
     */
    public List<WarehouseMapZoneDto> getZones(String zone, LocalDate today) {
        List<WarehouseMapRow> rows =
                warehouseBinRepository.findWarehouseMapRows(Texts.trimToNull(zone));

        // 조회 순서(zone → binCode)를 유지하기 위해 LinkedHashMap 사용
        Map<String, List<WarehouseMapRow>> grouped = new LinkedHashMap<>();
        for (WarehouseMapRow row : rows) {
            grouped.computeIfAbsent(row.zone(), key -> new ArrayList<>()).add(row);
        }

        List<WarehouseMapZoneDto> result = new ArrayList<>();
        for (Map.Entry<String, List<WarehouseMapRow>> entry : grouped.entrySet()) {
            result.add(toZoneDto(entry.getKey(), entry.getValue(), today));
        }
        return result;
    }

    /**
     * 창고 전체 요약 (사용 중지 구역은 수용량 통계에서 제외).
     * <p>
     * 이미 조회한 도면 데이터를 재사용하므로 DB 를 다시 조회하지 않는다.
     */
    public WarehouseMapSummaryDto getSummary(List<WarehouseMapZoneDto> zones) {
        return WarehouseMapSummaryDto.of(flattenBins(zones));
    }

    /** 구역 그룹별로 나뉜 타일을 하나의 목록으로 펼친다 */
    public List<WarehouseBinMapDto> flattenBins(List<WarehouseMapZoneDto> zones) {
        return zones.stream()
                .flatMap(zone -> zone.getBins().stream())
                .toList();
    }

    /** 필터용 구역 그룹 목록 */
    public List<String> getZoneCodes() {
        return warehouseBinRepository.findDistinctZones();
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

        WarehouseBinMapDto bin = WarehouseBinMapDto.of(row, today);

        List<InventoryDto> inventories =
                inventoryRepository.search(null, binId, null).stream()
                        .map(inventory -> InventoryDto.of(inventory, today))
                        .toList();

        return BinDetailDto.builder()
                .bin(bin)
                .inventories(inventories)
                .build();
    }

    /* ------------------------------------------------------------------
     * 내부 헬퍼
     * ------------------------------------------------------------------ */

    /**
     * 한 구역 그룹을 도면 DTO 로 변환한다.
     * <p>
     * 창고 평면도처럼 <b>단(Level)을 한 줄씩 쌓고</b>, 줄 안에서는 랙 번호 순으로 나열한다.
     * 칸의 너비는 화면에서 수용량에 비례하도록 그려지므로 여기서는 순서만 맞춰주면 된다.
     */
    private WarehouseMapZoneDto toZoneDto(String zone, List<WarehouseMapRow> rows, LocalDate today) {
        // 단별로 묶는다. 높은 단이 위로 오도록 내림차순 정렬.
        Map<Integer, List<WarehouseMapRow>> byLevel = new TreeMap<>(Comparator.reverseOrder());
        for (WarehouseMapRow row : rows) {
            byLevel.computeIfAbsent(levelOf(row.binLevel()), key -> new ArrayList<>()).add(row);
        }

        List<WarehouseMapLevelDto> levels = new ArrayList<>();
        List<WarehouseBinMapDto> allBins = new ArrayList<>();
        int totalCapacity = 0;
        int totalLoaded = 0;

        for (Map.Entry<Integer, List<WarehouseMapRow>> entry : byLevel.entrySet()) {
            List<WarehouseBinMapDto> levelBins = entry.getValue().stream()
                    .sorted(Comparator.comparing(row -> rackOf(row.rack())))
                    .map(row -> WarehouseBinMapDto.of(row, today))
                    .toList();

            levels.add(WarehouseMapLevelDto.builder()
                    .level(entry.getKey())
                    .bins(levelBins)
                    .build());

            allBins.addAll(levelBins);

            for (WarehouseMapRow row : entry.getValue()) {
                if (row.isActive()) {
                    totalCapacity += row.capacity();
                    totalLoaded += row.loaded();
                }
            }
        }

        return WarehouseMapZoneDto.builder()
                .zone(zone)
                .levels(levels)
                .bins(allBins)
                .totalCapacity(totalCapacity)
                .totalLoaded(totalLoaded)
                .usageRate(WarehouseBinMapDto.calculateUsageRate(totalLoaded, totalCapacity))
                .build();
    }

    /** 랙 번호가 없는 구역은 정렬 시 맨 뒤로 보낸다 */
    private String rackOf(String rack) {
        return (rack == null || rack.isBlank()) ? "~" : rack;
    }

    /** 단이 없는 구역은 1단으로 본다 */
    private int levelOf(Integer binLevel) {
        return binLevel == null ? 1 : binLevel;
    }
}
