package com.feedflow.admin.service;

import com.feedflow.admin.dto.BinDetailDto;
import com.feedflow.admin.dto.InventoryDto;
import com.feedflow.admin.dto.WarehouseBinMapDto;
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
import java.util.TreeSet;

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

        // 도면 좌표는 상세에서 의미가 없으므로 1,1 로 둔다
        WarehouseBinMapDto bin = WarehouseBinMapDto.of(row, today, 1, 1);

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
     * 랙 번호와 단을 각각 정렬해 축 라벨을 만들고, 그 인덱스를 CSS Grid 좌표로 쓴다.
     * 특정 좌표에 구역이 없으면 타일이 없으므로 도면에 빈칸으로 남는다.
     */
    private WarehouseMapZoneDto toZoneDto(String zone, List<WarehouseMapRow> rows, LocalDate today) {
        List<String> rackLabels = new ArrayList<>(new TreeSet<>(
                rows.stream().map(row -> rackLabel(row.rack())).toList()));

        // 단은 높은 층이 위로 오도록 내림차순
        List<Integer> levelLabels = rows.stream()
                .map(row -> levelLabel(row.binLevel()))
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        List<WarehouseBinMapDto> bins = new ArrayList<>();
        int totalCapacity = 0;
        int totalLoaded = 0;

        for (WarehouseMapRow row : rows) {
            int column = rackLabels.indexOf(rackLabel(row.rack())) + 1;
            int rowIndex = levelLabels.indexOf(levelLabel(row.binLevel())) + 1;

            bins.add(WarehouseBinMapDto.of(row, today, column, rowIndex));

            if (row.isActive()) {
                totalCapacity += row.capacity();
                totalLoaded += row.loaded();
            }
        }

        return WarehouseMapZoneDto.builder()
                .zone(zone)
                .rackLabels(rackLabels)
                .levelLabels(levelLabels)
                .bins(bins)
                .totalCapacity(totalCapacity)
                .totalLoaded(totalLoaded)
                .usageRate(WarehouseBinMapDto.calculateUsageRate(totalLoaded, totalCapacity))
                .build();
    }

    /** 랙 번호가 없는 구역은 한 칸으로 몰아 배치한다 */
    private String rackLabel(String rack) {
        return (rack == null || rack.isBlank()) ? "-" : rack;
    }

    /** 단이 없는 구역은 1단으로 본다 */
    private int levelLabel(Integer binLevel) {
        return binLevel == null ? 1 : binLevel;
    }
}
