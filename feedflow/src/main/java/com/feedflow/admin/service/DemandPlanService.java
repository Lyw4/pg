package com.feedflow.admin.service;

import com.feedflow.admin.dto.AnimalCoverageDto;
import com.feedflow.admin.dto.CenterAnimalQuantityRow;
import com.feedflow.admin.dto.CenterCoverageDto;
import com.feedflow.admin.dto.DemandPlanDto;
import com.feedflow.domain.AnimalType;
import com.feedflow.domain.Center;
import com.feedflow.repository.CenterRepository;
import com.feedflow.repository.FarmCustomerRepository;
import com.feedflow.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 수요 계획 서비스 — 담당 농장의 월 예상 사료량과 센터의 출고 가능 재고를 대조한다.
 *
 * <h3>이 기능이 성립하는 지점</h3>
 * 농장의 축종과 품목의 축종이 <b>같은 {@link AnimalType} enum</b> 이라
 * {@code (센터, 축종)} 키로 두 집계를 맞물릴 수 있다. 팀원 모듈에서 옮겨올 때
 * 자유 문자열({@code "조류(닭/오리)"})을 enum 으로 바꾼 결정이 여기서 값을 만든다.
 * 그대로 뒀다면 문자열 매핑 테이블을 하나 끼워야 했고, 매핑이 빠진 축종은
 * 조용히 0 으로 계산되었을 것이다.
 *
 * <h3>쿼리는 3회로 끝난다</h3>
 * <pre>
 *   센터 목록 1회 + 공급 집계 1회 + 수요 집계 1회 (+ 배송 일정 1회)
 * </pre>
 * 센터마다 재고를 조회하면 센터 수만큼 쿼리가 나간다. 집계는 {@code group by} 로
 * DB 가 하고, 결합은 메모리에서 한 번에 한다.
 *
 * <h3>합집합으로 순회하는 이유</h3>
 * 수요와 공급은 <b>어느 한쪽에만 있는 조합</b>이 생긴다.
 * <ul>
 *     <li>수요만 있음 — 담당 농장은 있는데 그 축종 재고가 하나도 없다. <b>가장 위험한
 *         상태이므로 반드시 보여야 한다.</b> 공급 기준으로만 순회하면 이 조합이
 *         목록에서 아예 빠져 부족을 놓친다.</li>
 *     <li>공급만 있음 — 재고는 있는데 담당 농장이 없다. 배정이 잘못되었거나 재고가
 *         엉뚱한 센터에 있다는 신호다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DemandPlanService {

    private final CenterRepository centerRepository;
    private final InventoryRepository inventoryRepository;
    private final FarmCustomerRepository farmCustomerRepository;

    /**
     * 수요 계획 조회.
     *
     * @param today 기준일 (이 날짜 이전에 만료된 로트는 공급에서 제외)
     */
    public DemandPlanDto getDemandPlan(LocalDate today) {
        List<Center> centers = centerRepository.findByActiveTrueOrderByCenterCodeAsc();

        /*
            이름에 supply / demand 를 분명히 박아 둔다. 두 Map 은 구조가 같아서
            (센터 × 축종 → 수량) 바꿔 써도 컴파일이 되고 화면도 그려진다.
            숫자만 조용히 틀린다. 이 프로젝트에서 s / ss 로 이름을 줄였다가
            가장 오래 걸린 버그를 만든 적이 있다.
         */
        Map<CoverageKey, Integer> supplyByKey = index(
                inventoryRepository.findAllocatableStockByCenterAndAnimalType(today));
        Map<CoverageKey, Integer> demandByKey = index(
                farmCustomerRepository.findDemandByCenterAndAnimalType());

        List<CenterCoverageDto> cards = new ArrayList<>(centers.size());
        for (Center center : centers) {
            cards.add(buildCenter(center, supplyByKey, demandByKey));
        }

        return DemandPlanDto.of(cards, farmCustomerRepository.findDeliverySchedule());
    }

    /* ==================================================================
     * 내부
     * ================================================================== */

    private CenterCoverageDto buildCenter(Center center,
                                          Map<CoverageKey, Integer> supplyByKey,
                                          Map<CoverageKey, Integer> demandByKey) {

        Long centerId = center.getCenterId();

        // 이 센터에서 수요나 공급이 있는 축종만 모은다.
        // 둘 다 0 인 축종을 넣으면 모든 센터가 축종 3줄을 갖게 되어, 실제로
        // 다루는 축종이 무엇인지 화면에서 알 수 없다.
        Set<AnimalType> animals = new LinkedHashSet<>();
        for (AnimalType animalType : AnimalType.values()) {
            CoverageKey key = new CoverageKey(centerId, animalType);
            if (demandByKey.containsKey(key) || supplyByKey.containsKey(key)) {
                animals.add(animalType);
            }
        }

        List<AnimalCoverageDto> rows = new ArrayList<>(animals.size());
        for (AnimalType animalType : animals) {
            CoverageKey key = new CoverageKey(centerId, animalType);
            rows.add(AnimalCoverageDto.of(
                    animalType,
                    demandByKey.getOrDefault(key, 0),
                    supplyByKey.getOrDefault(key, 0)));
        }

        return CenterCoverageDto.of(
                centerId,
                center.getCenterCode(),
                center.displayName(),
                center.getNote(),
                rows);
    }

    /**
     * 집계 결과를 {@code (센터, 축종)} 키로 인덱싱한다.
     * <p>
     * 같은 키가 두 번 나오면 앞의 값을 쓴다. {@code group by} 결과라 중복이 나올 수
     * 없지만, 쿼리를 고치다 {@code group by} 를 빠뜨리면 예외 대신 조용히 값이
     * 하나만 반영된다. 그 경우 합계가 틀어지므로 <b>두 값을 더한다.</b>
     */
    private Map<CoverageKey, Integer> index(List<CenterAnimalQuantityRow> rows) {
        Map<CoverageKey, Integer> result = new LinkedHashMap<>();
        for (CenterAnimalQuantityRow row : rows) {
            if (row.centerId() == null || row.animalType() == null) {
                continue;
            }
            result.merge(new CoverageKey(row.centerId(), row.animalType()), row.amount(), Integer::sum);
        }
        return result;
    }

    /**
     * 센터 × 축종 복합 키.
     * <p>
     * record 라 {@code equals} · {@code hashCode} 가 자동으로 만들어진다.
     * 문자열을 이어 붙여 키로 쓰면({@code centerId + "-" + animalType})
     * 센터 1 의 축종과 센터 11 의 축종이 우연히 같은 문자열이 되는 실수를
     * 컴파일러가 막아 주지 못한다.
     */
    private record CoverageKey(Long centerId, AnimalType animalType) {
    }
}
