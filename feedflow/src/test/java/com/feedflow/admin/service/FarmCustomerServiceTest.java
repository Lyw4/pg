package com.feedflow.admin.service;

import com.feedflow.admin.dto.CenterFarmRow;
import com.feedflow.admin.dto.FarmNetworkDto;
import com.feedflow.admin.dto.FarmSearchDto;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.domain.AnimalType;
import com.feedflow.domain.Center;
import com.feedflow.domain.CustomerStatus;
import com.feedflow.domain.FarmCustomer;
import com.feedflow.repository.CenterRepository;
import com.feedflow.repository.FarmCustomerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 농장 고객사 서비스 단위 테스트.
 *
 * <h3>여기서 검증하는 것</h3>
 * <ul>
 *     <li>집계 행이 없는 센터를 0 으로 채워 <b>카드가 빠지지 않는지</b></li>
 *     <li>전국 비중(sharePercent)의 분모가 0 일 때 나눗셈이 터지지 않는지</li>
 *     <li>검색어가 공백이면 조건이 무력화되도록 <b>null</b> 이 넘어가는지</li>
 *     <li>거래 상태 변경의 예외 경로</li>
 * </ul>
 * 집계 자체의 정확성(거래 중만 합산하는지 등)은 JPQL 안에 있으므로
 * {@code FarmCustomerRepositoryTest} 가 실제 H2 로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("농장 고객사 서비스 테스트")
class FarmCustomerServiceTest {

    @Mock
    private FarmCustomerRepository farmCustomerRepository;

    @Mock
    private CenterRepository centerRepository;

    @InjectMocks
    private FarmCustomerService farmCustomerService;

    /* ==================================================================
     * 목록 검색
     * ================================================================== */

    @Test
    @DisplayName("목록 집계는 거래 중 농장만 월 사료량에 더한다")
    void searchSumsFeedOnlyForTradingFarms() {
        Center center = center(1L, "C1-YS", "충남 예산 센터");

        given(farmCustomerRepository.search(isNull(), isNull(), isNull(), isNull()))
                .willReturn(List.of(
                        farm(1L, center, "거래중 농장", AnimalType.CATTLE, 180, 720, CustomerStatus.ACTIVE),
                        farm(2L, center, "보류 농장", AnimalType.POULTRY, 60000, 2380, CustomerStatus.PAUSED)));

        FarmSearchDto result = farmCustomerService.search(null, null, null, null);

        assertThat(result.getRowCount()).isEqualTo(2);
        assertThat(result.getActiveCount()).isEqualTo(1);
        assertThat(result.getPausedCount()).isEqualTo(1);
        assertThat(result.getActiveFeedQuantity())
                .as("720 만 더한다 (전체라면 3100)")
                .isEqualTo(720);
        assertThat(result.getLivestockCount())
                .as("사육 규모는 보류를 포함한다")
                .isEqualTo(60180);
        assertThat(result.isHasPaused()).isTrue();
    }

    @Test
    @DisplayName("검색어가 공백이면 조건을 걸지 않도록 null 을 넘긴다")
    void blankKeywordBecomesNull() {
        given(farmCustomerRepository.search(any(), any(), any(), any()))
                .willReturn(List.of());

        farmCustomerService.search(null, null, null, "   ");

        verify(farmCustomerRepository).search(isNull(), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("검색어는 대문자로 바꾸고 양쪽을 % 로 감싸 넘긴다")
    void keywordIsUpperCasedAndWrapped() {
        given(farmCustomerRepository.search(any(), any(), any(), any()))
                .willReturn(List.of());

        farmCustomerService.search(null, null, null, " gj-farm ");

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(farmCustomerRepository).search(isNull(), isNull(), isNull(), keyword.capture());

        assertThat(keyword.getValue()).isEqualTo("%GJ-FARM%");
    }

    /* ==================================================================
     * 전국 현황
     * ================================================================== */

    @Test
    @DisplayName("담당 농장이 없는 센터도 0 으로 채워 카드가 빠지지 않는다")
    void centerWithoutFarmStillGetsCard() {
        Center yesan = center(1L, "C1-YS", "충남 예산 센터");
        Center empty = center(9L, "C9-ZZ", "미배정 센터");

        given(centerRepository.findByActiveTrueOrderByCenterCodeAsc())
                .willReturn(List.of(yesan, empty));
        given(farmCustomerRepository.findFarmSummaryByCenter())
                .willReturn(List.of(new CenterFarmRow(1L, "충남 예산 센터", 3L, 2L, 62580L, 2570L)));

        FarmNetworkDto network = farmCustomerService.getNetwork();

        assertThat(network.getCenters())
                .as("집계에 없던 센터도 카드로 나와야 한다")
                .hasSize(2);
        assertThat(network.getCenters().get(1).isEmptyCenter()).isTrue();
        assertThat(network.getCenters().get(1).getActiveFeedQuantity()).isZero();
        assertThat(network.getEmptyCenterCount()).isEqualTo(1);

        assertThat(network.getTotalFarmCount()).isEqualTo(3);
        assertThat(network.getTotalActiveCount()).isEqualTo(2);
        assertThat(network.getTotalActiveFeedQuantity()).isEqualTo(2570);
        assertThat(network.getTotalPausedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("전국 비중은 월 사료량 기준으로 계산한다")
    void sharePercentIsBasedOnActiveFeedQuantity() {
        given(centerRepository.findByActiveTrueOrderByCenterCodeAsc())
                .willReturn(List.of(center(1L, "C1-YS", "예산"), center(2L, "C2-GJ", "김제")));
        given(farmCustomerRepository.findFarmSummaryByCenter())
                .willReturn(List.of(
                        new CenterFarmRow(1L, "예산", 2L, 2L, 100L, 3000L),
                        new CenterFarmRow(2L, "김제", 1L, 1L, 200L, 1000L)));

        FarmNetworkDto network = farmCustomerService.getNetwork();

        assertThat(network.getCenters().get(0).getSharePercent()).isEqualTo(75);
        assertThat(network.getCenters().get(1).getSharePercent()).isEqualTo(25);
    }

    @Test
    @DisplayName("농장이 하나도 없으면 비중 계산에서 0 으로 나누지 않는다")
    void sharePercentIsZeroWhenNoFarmExists() {
        given(centerRepository.findByActiveTrueOrderByCenterCodeAsc())
                .willReturn(List.of(center(1L, "C1-YS", "예산")));
        given(farmCustomerRepository.findFarmSummaryByCenter())
                .willReturn(List.of());

        FarmNetworkDto network = farmCustomerService.getNetwork();

        assertThat(network.getCenters().get(0).getSharePercent()).isZero();
        assertThat(network.getAverageFeedPerActiveFarm())
                .as("거래 중 농장이 0 이어도 평균 계산이 터지지 않는다")
                .isZero();
    }

    /* ==================================================================
     * 거래 상태 변경
     * ================================================================== */

    @Test
    @DisplayName("거래 상태를 변경하고 농장명을 돌려준다")
    void changeStatusReturnsFarmName() {
        FarmCustomer target = farm(1L, center(1L, "C1-YS", "예산"),
                "홍성 광천 산란계농장", AnimalType.POULTRY, 60000, 2380, CustomerStatus.ACTIVE);

        given(farmCustomerRepository.findWithCenterById(1L)).willReturn(Optional.of(target));

        String changed = farmCustomerService.changeStatus(1L, CustomerStatus.PAUSED);

        assertThat(changed).isEqualTo("홍성 광천 산란계농장");
        assertThat(target.getStatus()).isEqualTo(CustomerStatus.PAUSED);
        assertThat(target.isTrading()).isFalse();
    }

    @Test
    @DisplayName("없는 농장이면 ResourceNotFoundException")
    void changeStatusFailsWhenFarmNotFound() {
        given(farmCustomerRepository.findWithCenterById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> farmCustomerService.changeStatus(999L, CustomerStatus.PAUSED))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("이미 같은 상태면 IllegalStateException")
    void changeStatusFailsWhenAlreadySameStatus() {
        FarmCustomer target = farm(1L, center(1L, "C1-YS", "예산"),
                "예산 고덕 한우농장", AnimalType.CATTLE, 180, 720, CustomerStatus.ACTIVE);

        given(farmCustomerRepository.findWithCenterById(1L)).willReturn(Optional.of(target));

        assertThatThrownBy(() -> farmCustomerService.changeStatus(1L, CustomerStatus.ACTIVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("거래 중");
    }

    @Test
    @DisplayName("상태를 지정하지 않으면 IllegalArgumentException")
    void changeStatusFailsWhenStatusIsNull() {
        FarmCustomer target = farm(1L, center(1L, "C1-YS", "예산"),
                "예산 고덕 한우농장", AnimalType.CATTLE, 180, 720, CustomerStatus.ACTIVE);

        given(farmCustomerRepository.findWithCenterById(1L)).willReturn(Optional.of(target));

        assertThatThrownBy(() -> farmCustomerService.changeStatus(1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /* ==================================================================
     * 픽스처
     * ================================================================== */

    private Center center(Long centerId, String code, String name) {
        return Center.builder()
                .centerId(centerId)
                .centerCode(code)
                .name(name)
                .region("테스트 권역")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private FarmCustomer farm(Long id,
                              Center center,
                              String farmName,
                              AnimalType animalType,
                              int livestockCount,
                              int monthlyFeedQuantity,
                              CustomerStatus status) {
        return FarmCustomer.builder()
                .farmCustomerId(id)
                .farmCode("F-TEST-%02d".formatted(id))
                .farmName(farmName)
                .representativeName("대표자")
                .phone("010-0000-0000")
                .postalCode("32400")
                .address("테스트 주소")
                .latitude(36.7)
                .longitude(126.7)
                .animalType(animalType)
                .livestockCount(livestockCount)
                .monthlyFeedQuantity(monthlyFeedQuantity)
                .preferredFeed("테스트 사료")
                .recurringDeliveryDay(1)
                .center(center)
                .distanceKm(6.8)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
