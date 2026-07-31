package com.feedflow.admin.service;

import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.domain.BinPurpose;
import com.feedflow.domain.Center;
import com.feedflow.domain.WarehouseBin;
import com.feedflow.repository.InventoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 구역 적재 한도 검증기 단위 테스트.
 * <p>
 * 입고 · 구역 간 이동 · 출고 취소 복구가 <b>같은 판정</b>을 쓰도록 추출한 컴포넌트라,
 * 경계값과 null 보정을 여기서 한 번만 검증하고 각 서비스 테스트는 업무 흐름에 집중한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BinCapacityChecker 단위 테스트")
class BinCapacityCheckerTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private BinCapacityChecker binCapacityChecker;

    @Nested
    @DisplayName("적재 한도 판정")
    class CheckCanAccept {

        @Test
        @DisplayName("한도 안이면 통과하고 이동 전 적재량을 돌려준다")
        void withinCapacity_returnsCurrentLoad() {
            WarehouseBin bin = bin(1L, "A-01", 600);
            given(inventoryRepository.sumQuantityByBinId(1L)).willReturn(100L);

            int currentLoad = binCapacityChecker.checkCanAccept(bin, 50, "입고");

            assertThat(currentLoad)
                    .as("호출한 쪽이 '처리 후 남은 여유' 를 같은 값으로 계산하도록 돌려준다")
                    .isEqualTo(100);
        }

        @Test
        @DisplayName("한도와 정확히 같아지는 수량은 허용한다 (경계값)")
        void exactlyAtCapacity_isAllowed() {
            WarehouseBin bin = bin(1L, "A-01", 600);
            given(inventoryRepository.sumQuantityByBinId(1L)).willReturn(580L);

            // 580 + 20 = 600 = 한도
            int currentLoad = binCapacityChecker.checkCanAccept(bin, 20, "이동");

            assertThat(currentLoad).isEqualTo(580);
        }

        @Test
        @DisplayName("한도를 1 이라도 넘으면 거부한다 (경계값)")
        void oneOverCapacity_throwsException() {
            WarehouseBin bin = bin(1L, "A-01", 600);
            given(inventoryRepository.sumQuantityByBinId(1L)).willReturn(580L);

            // 580 + 21 = 601 > 600
            assertThatThrownBy(() -> binCapacityChecker.checkCanAccept(bin, 21, "이동"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("적재 한도를 초과합니다")
                    .hasMessageContaining("A-01");
        }

        @Test
        @DisplayName("작업 이름이 예외 문구에 들어간다 (입고 · 이동 · 복구 구분)")
        void actionLabelAppearsInMessage() {
            WarehouseBin bin = bin(1L, "A-01", 100);
            given(inventoryRepository.sumQuantityByBinId(1L)).willReturn(90L);

            assertThatThrownBy(() -> binCapacityChecker.checkCanAccept(bin, 20, "복구"))
                    .hasMessageContaining("복구 20")
                    .hasMessageContaining("한도 100");
        }

        @Test
        @DisplayName("재고가 없는 구역은 sum() 이 null 이라 0 으로 보정한다")
        void nullSum_treatedAsZero() {
            WarehouseBin bin = bin(1L, "A-01", 600);
            given(inventoryRepository.sumQuantityByBinId(1L)).willReturn(null);

            int currentLoad = binCapacityChecker.checkCanAccept(bin, 600, "입고");

            assertThat(currentLoad)
                    .as("null 을 0 으로 보지 않으면 NPE 가 나거나 빈 구역에 입고를 못 한다")
                    .isZero();
        }

        @Test
        @DisplayName("최대 수용량이 없는 구역은 한도 0 으로 보아 어떤 적재도 거부한다")
        void nullCapacity_rejectsAnything() {
            // WarehouseBin.capacityLimit() 이 null 을 0 으로 보정하는 규칙을 그대로 따른다
            WarehouseBin bin = WarehouseBin.builder()
                    .binId(1L)
                    .binCode("A-01")
                    .center(center())
                    .zone("A")
                    .binPurpose(BinPurpose.STORAGE)
                    .maxCapacity(null)
                    .active(true)
                    .build();
            given(inventoryRepository.sumQuantityByBinId(1L)).willReturn(0L);

            assertThatThrownBy(() -> binCapacityChecker.checkCanAccept(bin, 1, "입고"))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    @DisplayName("현재 적재량 조회")
    class CurrentLoad {

        @Test
        @DisplayName("구역 전체 적재량을 돌려준다 (특정 로트 수량이 아니다)")
        void returnsWholeBinLoad() {
            WarehouseBin bin = bin(1L, "A-01", 600);
            given(inventoryRepository.sumQuantityByBinId(1L)).willReturn(250L);

            assertThat(binCapacityChecker.currentLoadOf(bin)).isEqualTo(250);
        }

        @Test
        @DisplayName("빈 구역은 0 을 돌려준다")
        void emptyBinReturnsZero() {
            WarehouseBin bin = bin(1L, "A-01", 600);
            given(inventoryRepository.sumQuantityByBinId(1L)).willReturn(null);

            assertThat(binCapacityChecker.currentLoadOf(bin)).isZero();
        }
    }

    private WarehouseBin bin(Long binId, String binCode, int maxCapacity) {
        return WarehouseBin.builder()
                .binId(binId)
                .binCode(binCode)
                .center(center())
                .zone(binCode.substring(0, 1))
                .binPurpose(BinPurpose.STORAGE)
                .rack("01")
                .binLevel(1)
                .maxCapacity(maxCapacity)
                .posX(1)
                .posY(1)
                .posWidth(2)
                .posHeight(2)
                .active(true)
                .build();
    }

    /**
     * 테스트용 센터 픽스처.
     * <p>
     * {@code Warehouse} enum 이 {@link Center} 엔티티로 승격되어 구역마다 센터가 필요하다.
     * DB 에 저장하지 않는 단위 테스트이므로 빌더로 만든 객체를 그대로 쓴다.
     */
    private Center center() {
        return Center.builder()
                .centerId(1L)
                .centerCode("WH1")
                .name("제1창고")
                .region("수도권")
                .note("상온 · 배합사료")
                .active(true)
                .build();
    }

}
