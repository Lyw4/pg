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

        Map<Long, CenterStockRow> stock = index(
                inventoryRepository.findStockByCenter(null), CenterStockRow::centerId);
        /*
            적재율의 분자는 '보관 구역' 재고여야 한다. 분모가 보관 구역 수용량이기
            때문이다. 전체 재고를 분자로 쓰면 입고 대기 구역의 물건까지 보관 공간을
            차지한 것으로 계산되어 적재율이 부풀려지고 100% 를 넘길 수도 있다.
         */
        Map<Long, CenterStockRow> storageStock = index(
                inventoryRepository.findStorageStockByCenter(), CenterStockRow::centerId);
        Map<Long, CenterCapacityRow> capacity = index(
                warehouseBinRepository.findStorageCapacityByCenter(), CenterCapacityRow::centerId);
        Map<Long, CenterAlertRow> alert = index(
                inventoryRepository.findExpiringByCenter(
                        today, today.plusDays(StockPolicy.EXPIRING_SOON_DAYS)),
                CenterAlertRow::centerId);

        Map<Long, Map<MovementType, Integer>> activity = groupActivity(today);
        Map<Long, Map<String, Integer>> animalMix = groupAnimalMix();

        int nationwideQuantity = stock.values().stream()
                .mapToInt(CenterStockRow::totalQuantity).sum();

        List<CenterOverviewDto> rows = new ArrayList<>(centers.size());
        for (Center c : centers) {
            Long id = c.getCenterId();
            CenterStockRow s = stock.get(id);
            CenterCapacityRow cap = capacity.get(id);
            CenterAlertRow a = alert.get(id);

            CenterStockRow ss = storageStock.get(id);

            int qty = s == null ? 0 : s.totalQuantity();

            rows.add(CenterOverviewDto.builder()
                    .centerId(id)
                    .centerCode(c.getCenterCode())
                    .centerName(c.displayName())
                    .region(c.getRegion())
                    .note(c.getNote())
                    .quantity(qty)
                    .storageQuantity(ss == null ? 0 : ss.totalQuantity())
                    .sharePercent(share(qty, nationwideQuantity))
                    .capacity(cap == null ? 0 : cap.totalCapacity())
                    .rowCount(s == null ? 0 : s.rows())
                    .expiringCount(a == null ? 0 : a.expiring())
                    .expiredCount(a == null ? 0 : a.expired())
                    .activity(activity.getOrDefault(id, Map.of()))
                    .animalMix(animalMix.getOrDefault(id, Map.of()))
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

        Map<Long, CenterStockRow> stock = index(
                inventoryRepository.findStockByCenter(null), CenterStockRow::centerId);
        // 적재율 분자는 보관 구역 재고. 대시보드 센터 카드와 같은 기준을 써야
        // 같은 센터가 지도 팝업과 카드에서 다른 적재율로 보이지 않는다.
        Map<Long, CenterStockRow> storageStock = index(
                inventoryRepository.findStorageStockByCenter(), CenterStockRow::centerId);
        Map<Long, CenterCapacityRow> capacity = index(
                warehouseBinRepository.findStorageCapacityByCenter(), CenterCapacityRow::centerId);

        List<CenterMapPinDto> pins = new ArrayList<>();
        int missing = 0;

        for (Center c : centers) {
            if (!c.hasLocation()) {
                missing++;
                continue;
            }
            CenterStockRow s = stock.get(c.getCenterId());
            CenterStockRow ss = storageStock.get(c.getCenterId());
            CenterCapacityRow cap = capacity.get(c.getCenterId());

            int qty = s == null ? 0 : s.totalQuantity();
            int storageQty = ss == null ? 0 : ss.totalQuantity();
            int total = cap == null ? 0 : cap.totalCapacity();

            pins.add(new CenterMapPinDto(
                    c.getCenterId(), c.getCenterCode(), c.displayName(),
                    c.getRegion(), c.getNote(),
                    c.getLatitude(), c.getLongitude(),
                    qty, storageQty,
                    total <= 0 ? 0 : (int) Math.round(storageQty * 100.0 / total)));
        }

        return new CenterMapPinDto.Response(pins, missing);
    }

    /* ------------------------------------------------------------------
     * 내부 헬퍼
     * ------------------------------------------------------------------ */

    private <T> Map<Long, T> index(List<T> rows, Function<T, Long> key) {
        return rows.stream().collect(Collectors.toMap(key, r -> r, (a, b) -> a));
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
