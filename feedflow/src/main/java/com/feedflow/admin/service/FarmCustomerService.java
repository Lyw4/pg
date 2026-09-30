package com.feedflow.admin.service;

import com.feedflow.admin.dto.CenterFarmRow;
import com.feedflow.admin.dto.CenterFarmSummaryDto;
import com.feedflow.admin.dto.FarmCustomerDto;
import com.feedflow.admin.dto.FarmNetworkDto;
import com.feedflow.admin.dto.FarmSearchDto;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.common.util.Texts;
import com.feedflow.domain.AnimalType;
import com.feedflow.domain.Center;
import com.feedflow.domain.CustomerStatus;
import com.feedflow.domain.FarmCustomer;
import com.feedflow.repository.CenterRepository;
import com.feedflow.repository.FarmCustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 농장 고객사 관리 서비스.
 *
 * <h3>집계를 DB 로 내린 이유 (원본 대비 가장 큰 변경)</h3>
 * 팀원 모듈의 원본 서비스는 이런 구조였다.
 * <pre>
 *   customers()                  // 전체 농장 로드
 *   activeCount()                // customers() 를 다시 호출 → 또 전체 로드
 *   totalMonthlyFeedQuantity()   // customers() 를 다시 호출 → 또 전체 로드
 *   warehouseSummaries()         // customers() 를 다시 호출 + 창고마다 stream().filter()
 * </pre>
 * 화면 한 번 열 때 <b>전체 목록을 네 번 읽고</b>, 센터별 합계를
 * {@code 센터 수 × 농장 수} 만큼 반복문으로 돌았다. 농장이 늘어나면 그대로 느려진다.
 * <p>
 * 이 서비스는 <b>목록 1회 + 집계 1회</b>로 끝낸다. 집계는
 * {@code group by} 로 DB 가 하고, 화면이 쓰는 요약값은 그 결과를 한 번 훑어 만든다.
 * 이 프로젝트가 대시보드 통계에서 쓰는 방식과 같다.
 *
 * <h3>Entity 를 화면으로 내보내지 않는다</h3>
 * {@code FarmCustomer.center} 는 지연 로딩이라 템플릿에서 건드리면 렌더링 중에
 * 쿼리가 나간다. 모든 반환값은 DTO 다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FarmCustomerService {

    private final FarmCustomerRepository farmCustomerRepository;
    private final CenterRepository centerRepository;

    /* ==================================================================
     * 조회
     * ================================================================== */

    /**
     * 농장 목록 검색.
     *
     * @param centerId   담당 센터 (null 이면 전국)
     * @param animalType 축종 (null 이면 전체)
     * @param status     거래 상태 (null 이면 전체)
     * @param keyword    농장명 · 대표자 · 주소 · 농장코드 검색어 (공백이면 전체)
     */
    public FarmSearchDto search(Long centerId,
                                AnimalType animalType,
                                CustomerStatus status,
                                String keyword) {

        List<FarmCustomerDto> rows = farmCustomerRepository
                .search(centerId, animalType, status, likeKeyword(keyword))
                .stream()
                .map(FarmCustomerDto::of)
                .toList();

        return FarmSearchDto.of(rows);
    }

    /**
     * 전국 농장 현황 (센터 카드 + 전국 합계).
     * <p>
     * 센터 목록과 농장 집계를 각각 한 번씩 읽어 맞춘다. 집계 쿼리는
     * 담당 농장이 없는 센터를 내려보내지 않으므로({@code group by} 결과)
     * 센터 목록 기준으로 순회하며 빠진 센터를 0 으로 채운다.
     */
    public FarmNetworkDto getNetwork() {
        List<Center> centers = centerRepository.findByActiveTrueOrderByCenterCodeAsc();

        Map<Long, CenterFarmRow> summaryByCenter = farmCustomerRepository.findFarmSummaryByCenter()
                .stream()
                .filter(row -> row.centerId() != null)
                .collect(Collectors.toMap(CenterFarmRow::centerId, Function.identity(),
                        (first, second) -> first));

        // 비중의 분모. 카드를 만들기 전에 전국 합계를 먼저 구해야 계산할 수 있다.
        int nationwideFeed = summaryByCenter.values().stream()
                .mapToInt(CenterFarmRow::activeFeed)
                .sum();

        List<CenterFarmSummaryDto> cards = new ArrayList<>(centers.size());
        for (Center center : centers) {
            CenterFarmRow row = summaryByCenter.get(center.getCenterId());

            int activeFeed = row == null ? 0 : row.activeFeed();

            cards.add(CenterFarmSummaryDto.builder()
                    .centerId(center.getCenterId())
                    .centerCode(center.getCenterCode())
                    .centerName(center.displayName())
                    .region(center.getRegion())
                    .note(center.getNote())
                    .farmCount(row == null ? 0 : row.farms())
                    .activeCount(row == null ? 0 : row.activeFarms())
                    .livestockCount(row == null ? 0 : row.livestock())
                    .activeFeedQuantity(activeFeed)
                    .sharePercent(share(activeFeed, nationwideFeed))
                    .build());
        }

        return FarmNetworkDto.of(cards);
    }

    /**
     * 담당 센터가 취급하지 않는 축종을 가진 농장 (배정 검토용).
     * <p>
     * 판단 기준은 "그 센터에 그 축종 사료 재고가 하나도 없는가" 다.
     * 운영 방향({@code Center.note})은 문장이라 기계가 읽을 수 없어 재고로 판단한다.
     */
    public List<FarmCustomerDto> getFarmsWithUnsupportedAnimalType() {
        return farmCustomerRepository.findWithUnsupportedAnimalType().stream()
                .map(FarmCustomerDto::of)
                .toList();
    }

    /* ==================================================================
     * 변경
     * ================================================================== */

    /**
     * 거래 상태 변경 (거래 중 ↔ 거래 보류).
     *
     * @param farmCustomerId 대상 농장
     * @param newStatus      변경할 상태
     * @return 변경된 농장명 (결과 메시지용)
     * @throws ResourceNotFoundException 농장이 없는 경우
     * @throws IllegalArgumentException  상태가 null 인 경우
     * @throws IllegalStateException     이미 같은 상태인 경우
     */
    @Transactional
    public String changeStatus(Long farmCustomerId, CustomerStatus newStatus) {
        FarmCustomer farm = farmCustomerRepository.findWithCenterById(farmCustomerId)
                .orElseThrow(() -> ResourceNotFoundException.ofFarmCustomer(farmCustomerId));

        farm.changeStatus(newStatus);
        return farm.getFarmName();
    }

    /* ==================================================================
     * 내부 헬퍼
     * ================================================================== */

    /**
     * 검색어를 JPQL {@code like} 파라미터로 만든다.
     * <p>
     * 대문자로 맞춰 대소문자를 구분하지 않게 하고 양쪽에 {@code %} 를 붙인다.
     * 공백이면 null 을 반환해 조건 자체가 무력화되게 한다
     * ({@code :keyword is null or ...} 형태이므로).
     */
    private String likeKeyword(String keyword) {
        if (Texts.isBlank(keyword)) {
            return null;
        }
        return "%" + keyword.trim().toUpperCase() + "%";
    }

    /**
     * 전국 대비 비중 (%).
     * <p>
     * 분모가 0 이면 0 을 반환한다. 농장이 하나도 없는 초기 상태에서
     * 0 으로 나누기가 발생하지 않게 한다.
     */
    private int share(int centerValue, int nationwideValue) {
        if (nationwideValue <= 0) {
            return 0;
        }
        return (int) Math.round(centerValue * 100.0 / nationwideValue);
    }
}
