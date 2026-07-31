package com.feedflow.admin.service;

import com.feedflow.admin.dto.CenterActivityRow;
import com.feedflow.admin.dto.CenterAlertRow;
import com.feedflow.admin.dto.CenterAnimalMixRow;
import com.feedflow.admin.dto.CenterCapacityRow;
import com.feedflow.admin.dto.CenterMapPinDto;
import com.feedflow.admin.dto.CenterNetworkDto;
import com.feedflow.admin.dto.CenterOverviewDto;
import com.feedflow.admin.dto.CenterStockChartDto;
import com.feedflow.admin.dto.CenterStockRow;
import com.feedflow.common.StockPolicy;
import com.feedflow.domain.Center;
import com.feedflow.domain.MovementType;
import com.feedflow.repository.CenterRepository;
import com.feedflow.repository.InventoryRepository;
import com.feedflow.repository.StockMovementRepository;
import com.feedflow.repository.WarehouseBinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 전국 물류망 대시보드 서비스 (Epic Phase 4a).
 *
 * <h3>왜 별도 서비스인가</h3>
 * {@code DashboardService} 는 <b>전국 합계 기준</b>의 요약을 담당한다
 * (안전재고 알림 · 유통기한 알림 · 오늘의 할 일 · 매출). 그 관점을 유지해야
 * "전국에 재고가 얼마나 있는지" 를 볼 곳이 남는다.
 * <p>
 * 이 서비스는 축이 다르다 — <b>센터별로 쪼개서 비교</b>한다. 두 관점을 한 서비스에
 * 섞으면 메서드마다 "이건 전국인가 센터별인가" 를 확인해야 한다.
 *
 * <h3>조립 방식</h3>
 * 센터 목록 · 재고 · 수용량 · 경보 · 실적 · 축종 구성을 <b>각각 한 번씩</b> 집계해
 * 센터 단위로 짝지어 내려준다. 센터마다 반복 조회하면 센터 수만큼 쿼리가 늘어난다.
 * (쿼리 6회로 고정 — 센터가 50곳이 되어도 변하지 않는다)
 *
 * <p>모두 조회 전용이다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CenterDashboardService {

    private final CenterRepository centerRepository;
    private final InventoryRepository inventoryRepository;
    private final WarehouseBinRepository warehouseBinRepository;
    private final StockMovementRepository stockMovementRepository;

    /**
     * 실적 집계 기간 (일).
     * <p>
     * 매출 차트의 기본 일수와 같은 설정을 쓴다. 두 지표를 나란히 보는 화면에서
     * 기간이 다르면 "입고는 늘었는데 매출은 줄었다" 같은 잘못된 비교를 하게 된다.
     */
    @Value("${feedflow.dashboard.chart-default-days:7}")
    private int activityDays;

    /**
     * 전국 물류망 현황.
     *
     * @param today 기준일 (유통기한 임박 판정과 실적 기간의 끝)
     */
    public CenterNetworkDto getNetworkOverview(LocalDate today) {
        List<Center> centers = centerRepository.findByActiveTrueOrderByCenterCodeAsc();

        /*
            재고를 두 가지로 나눠 읽는다. 이름에 '전체(total)' 와 '보관(storage)' 을
            분명히 박아 둔 이유 —  둘을 혼동해 적재율의 분자로 전체 재고를 쓰는 버그가
            실제로 있었다. 짧은 이름(s / ss)으로는 어느 쪽인지 읽는 사람이 알 수 없다.

              전체 재고   : 대기 구역 · 운송 중 포함  → 분포 · 비중 · 재고 총량 표시용
              보관 구역만 : STORAGE 구역             → 적재율의 분자 (분모와 기준 일치)
         */
        Map<Long, CenterStockRow> totalStockByCenter = index(
                inventoryRepository.findStockByCenter(null), CenterStockRow::centerId);
        Map<Long, CenterStockRow> storageStockByCenter = index(
                inventoryRepository.findStorageStockByCenter(), CenterStockRow::centerId);
        Map<Long, CenterCapacityRow> storageCapacityByCenter = index(
                warehouseBinRepository.findStorageCapacityByCenter(), CenterCapacityRow::centerId);
        Map<Long, CenterAlertRow> expiryAlertByCenter = index(
                inventoryRepository.findExpiringByCenter(
                        today, today.plusDays(StockPolicy.EXPIRING_SOON_DAYS)),
                CenterAlertRow::centerId);

        Map<Long, Map<MovementType, Integer>> activityByCenter = groupActivity(today);
        Map<Long, Map<String, Integer>> animalMixByCenter = groupAnimalMix();

        int nationwideQuantity = totalStockByCenter.values().stream()
                .mapToInt(CenterStockRow::totalQuantity).sum();

        List<CenterOverviewDto> rows = new ArrayList<>(centers.size());
        for (Center center : centers) {
            Long centerId = center.getCenterId();

            CenterStockRow totalStock = totalStockByCenter.get(centerId);
            CenterStockRow storageStock = storageStockByCenter.get(centerId);
            CenterCapacityRow storageCapacity = storageCapacityByCenter.get(centerId);
            CenterAlertRow expiryAlert = expiryAlertByCenter.get(centerId);

            // 집계 쿼리는 group by 결과라 해당 센터 행이 아예 없을 수 있다 (재고 0 인 센터)
            int totalQuantity = totalStock == null ? 0 : totalStock.totalQuantity();
            int storageQuantity = storageStock == null ? 0 : storageStock.totalQuantity();

            rows.add(CenterOverviewDto.builder()
                    .centerId(centerId)
                    .centerCode(center.getCenterCode())
                    .centerName(center.displayName())
                    .region(center.getRegion())
                    .note(center.getNote())
                    .quantity(totalQuantity)
                    .storageQuantity(storageQuantity)
                    .sharePercent(share(totalQuantity, nationwideQuantity))
                    .capacity(storageCapacity == null ? 0 : storageCapacity.totalCapacity())
                    .rowCount(totalStock == null ? 0 : totalStock.rows())
                    .expiringCount(expiryAlert == null ? 0 : expiryAlert.expiring())
                    .expiredCount(expiryAlert == null ? 0 : expiryAlert.expired())
                    .activity(activityByCenter.getOrDefault(centerId, Map.of()))
                    .animalMix(animalMixByCenter.getOrDefault(centerId, Map.of()))
                    .build());
        }

        return CenterNetworkDto.of(rows, activityDays, StockPolicy.EXPIRING_SOON_DAYS);
    }

    /**
     * 센터별 재고 분포 차트 데이터 (도넛).
     * <p>
     * 화면 렌더링 시 넘기지 않고 {@code fetch()} 로 따로 가져간다.
     * 매출 차트와 같은 방식이다 — 차트가 실패해도 화면 본문은 그려진다.
     */
    public CenterStockChartDto getStockChart() {
        List<CenterStockRow> rows = inventoryRepository.findStockByCenter(null);
        return CenterStockChartDto.of(rows);
    }

    /**
     * 전국 지도에 찍을 센터 핀.
     * <p>
     * 좌표가 없는 센터는 핀을 찍을 수 없어 제외하되, <b>몇 곳이 빠졌는지 함께 알려준다.</b>
     * 조용히 빼면 핀 수가 센터 수와 달라도 아무도 눈치채지 못한다.
     * <p>
     * 재고와 적재율을 함께 담는다. 핀을 눌렀을 때 "이 센터에 얼마나 있는지" 를
     * 바로 보여주려면 좌표만으로는 부족하고, 지도와 재고를 따로 요청하면
     * 두 응답을 화면에서 다시 짝지어야 한다.
     */
    public CenterMapPinDto.Response getMapPins() {
        List<Center> centers = centerRepository.findByActiveTrueOrderByCenterCodeAsc();

        // 센터 카드(getNetworkOverview)와 같은 세 쿼리를 같은 기준으로 읽는다.
        // 기준이 갈리면 같은 센터가 지도 팝업과 카드에서 다른 적재율로 보인다.
        Map<Long, CenterStockRow> totalStockByCenter = index(
                inventoryRepository.findStockByCenter(null), CenterStockRow::centerId);
        Map<Long, CenterStockRow> storageStockByCenter = index(
                inventoryRepository.findStorageStockByCenter(), CenterStockRow::centerId);
        Map<Long, CenterCapacityRow> storageCapacityByCenter = index(
                warehouseBinRepository.findStorageCapacityByCenter(), CenterCapacityRow::centerId);

        List<CenterMapPinDto> pins = new ArrayList<>();
        int centersWithoutLocation = 0;

        for (Center center : centers) {
            if (!center.hasLocation()) {
                centersWithoutLocation++;
                continue;
            }
            Long centerId = center.getCenterId();

            CenterStockRow totalStock = totalStockByCenter.get(centerId);
            CenterStockRow storageStock = storageStockByCenter.get(centerId);
            CenterCapacityRow storageCapacity = storageCapacityByCenter.get(centerId);

            int totalQuantity = totalStock == null ? 0 : totalStock.totalQuantity();
            int storageQuantity = storageStock == null ? 0 : storageStock.totalQuantity();
            int storageCapacityTotal = storageCapacity == null ? 0 : storageCapacity.totalCapacity();

            pins.add(new CenterMapPinDto(
                    centerId, center.getCenterCode(), center.displayName(),
                    center.getRegion(), center.getNote(),
                    center.getLatitude(), center.getLongitude(),
                    totalQuantity, storageQuantity,
                    usageRate(storageQuantity, storageCapacityTotal)));
        }

        return new CenterMapPinDto.Response(pins, centersWithoutLocation);
    }

    /* ------------------------------------------------------------------
     * 내부 헬퍼
     * ------------------------------------------------------------------ */

    private <T> Map<Long, T> index(List<T> rows, Function<T, Long> key) {
        return rows.stream().collect(Collectors.toMap(key, r -> r, (a, b) -> a));
    }

    /**
     * 보관 구역 적재율 (%).
     * <p>
     * <b>분자와 분모가 같은 구역 집합을 세야 한다.</b> 이 계산을 호출부마다 인라인으로
     * 적어 두면 한쪽이 전체 재고를 넘기는 실수를 막을 수 없다 — 실제로 그런 버그가
     * 있었다. 대기 구역 · 운송 중 재고는 보관 공간을 차지하지 않으므로 분자에서
     * 빠져야 하고, 그 차이는 화면에서 {@code waitingQuantity} 로 따로 보여준다.
     *
     * @param storageQuantity 보관(STORAGE) 구역에 있는 수량 — 분자
     * @param storageCapacity 활성 보관 구역의 수용량 합계 — 분모
     * @return 0 ~ (경우에 따라 100 초과 가능). 수용량이 0 이면 나눌 수 없으므로 0
     */
    private int usageRate(int storageQuantity, int storageCapacity) {
        if (storageCapacity <= 0) {
            return 0;
        }
        return (int) Math.round(storageQuantity * 100.0 / storageCapacity);
    }

    /**
     * 센터별 유형별 실적을 {@code Map} 두 겹으로 모은다.
     * <p>
     * 유형을 쿼리에서 컬럼으로 펼치지 않은 이유 — 유형이 추가될 때마다
     * {@code case when} 을 늘려야 하고, P3a 에서 이미 두 개가 늘었다.
     */
    private Map<Long, Map<MovementType, Integer>> groupActivity(LocalDate today) {
        LocalDateTime start = today.minusDays(activityDays - 1L).atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);

        Map<Long, Map<MovementType, Integer>> result = new LinkedHashMap<>();
        for (CenterActivityRow row : stockMovementRepository.findActivityByCenter(start, end)) {
            result.computeIfAbsent(row.centerId(), k -> new EnumMap<>(MovementType.class))
                    .merge(row.type(), row.totalQuantity(), Integer::sum);
        }
        return result;
    }

    /**
     * 센터별 축종 구성.
     * <p>
     * 키를 축종 <b>한글 라벨</b>로 둔다. 화면이 enum 이름을 다시 변환하지 않게 하고,
     * 순서를 유지해 축종이 뒤바뀌지 않도록 {@link LinkedHashMap} 을 쓴다.
     */
    private Map<Long, Map<String, Integer>> groupAnimalMix() {
        Map<Long, Map<String, Integer>> result = new LinkedHashMap<>();
        for (CenterAnimalMixRow row : inventoryRepository.findAnimalMixByCenter()) {
            result.computeIfAbsent(row.centerId(), k -> new LinkedHashMap<>())
                    .merge(row.animalType().getDescription(), row.totalQuantity(), Integer::sum);
        }
        return result;
    }

    /** 전국 합계가 0 이면 나눗셈이 불가능하므로 0% 로 본다 */
    private int share(int quantity, int total) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.round(quantity * 100.0 / total);
    }
}
