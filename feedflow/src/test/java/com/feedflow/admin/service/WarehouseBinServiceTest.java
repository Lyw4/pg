package com.feedflow.admin.service;

import com.feedflow.admin.dto.WarehouseBinDto;
import com.feedflow.admin.dto.WarehouseBinForm;
import com.feedflow.common.exception.DuplicateCodeException;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.domain.Center;
import com.feedflow.domain.WarehouseBin;
import com.feedflow.repository.CenterRepository;
import com.feedflow.repository.WarehouseBinRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 창고 구역(기준 정보) 서비스 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseBinService 단위 테스트")
class WarehouseBinServiceTest {

    private static final Center CENTER_1 = Center.builder()
            .centerId(1L).centerCode("WH1").name("제1창고").region("수도권")
            .note("상온 · 배합사료").active(true).build();

    private static final Center CENTER_2 = Center.builder()
            .centerId(2L).centerCode("WH2").name("제2창고").region("수도권")
            .note("저온 · 영양제").active(true).build();

    @Mock
    private WarehouseBinRepository warehouseBinRepository;

    @Mock
    private CenterRepository centerRepository;

    @InjectMocks
    private WarehouseBinService warehouseBinService;

    @Test
    @DisplayName("중복되지 않은 구역 코드로 등록하면 저장되고 생성된 ID를 반환한다")
    void create_success() {
        // given
        WarehouseBinForm form = binForm("A-04-01", "a");
        given(warehouseBinRepository.existsByBinCode("A-04-01")).willReturn(false);
        given(centerRepository.findById(1L)).willReturn(Optional.of(CENTER_1));
        given(warehouseBinRepository.save(any(WarehouseBin.class))).willReturn(bin(10L, "A-04-01"));

        // when
        Long binId = warehouseBinService.create(form);

        // then
        assertThat(binId).isEqualTo(10L);

        ArgumentCaptor<WarehouseBin> captor = ArgumentCaptor.forClass(WarehouseBin.class);
        verify(warehouseBinRepository).save(captor.capture());

        WarehouseBin saved = captor.getValue();
        assertThat(saved.getBinCode()).isEqualTo("A-04-01");
        assertThat(saved.getZone())
                .as("구역(Zone)도 대문자로 정규화되어야 한다")
                .isEqualTo("A");
        assertThat(saved.getCenter())
                .as("폼에서 선택한 센터가 연결되어야 한다")
                .isSameAs(CENTER_1);
        assertThat(saved.getBinLevel()).isEqualTo(1);
        assertThat(saved.getMaxCapacity()).isEqualTo(400);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    @DisplayName("이미 존재하는 구역 코드로 등록하면 DuplicateCodeException 이 발생하고 저장되지 않는다")
    void create_duplicateCode_throwsException() {
        // given
        WarehouseBinForm form = binForm("A-01-01", "A");
        given(warehouseBinRepository.existsByBinCode("A-01-01")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> warehouseBinService.create(form))
                .isInstanceOf(DuplicateCodeException.class)
                .hasMessageContaining("이미 등록된 구역 코드입니다")
                .hasMessageContaining("A-01-01");

        verify(warehouseBinRepository, never()).save(any(WarehouseBin.class));
    }

    @Test
    @DisplayName("존재하지 않는 센터를 지정해 등록하면 ResourceNotFoundException 이 발생하고 저장되지 않는다")
    void create_centerNotFound_throwsException() {
        // given
        WarehouseBinForm form = binForm("A-04-01", "A");
        form.setCenterId(999L);
        given(warehouseBinRepository.existsByBinCode("A-04-01")).willReturn(false);
        given(centerRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> warehouseBinService.create(form))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("존재하지 않는 물류센터");

        verify(warehouseBinRepository, never()).save(any(WarehouseBin.class));
    }

    @Test
    @DisplayName("다른 구역이 사용 중인 코드로 변경하면 DuplicateCodeException 이 발생한다")
    void update_duplicateCode_throwsException() {
        // given
        WarehouseBin target = bin(1L, "A-01-01");
        given(warehouseBinRepository.findWithCenterById(1L)).willReturn(Optional.of(target));
        given(warehouseBinRepository.existsByBinCodeAndBinIdNot("B-01-01", 1L)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> warehouseBinService.update(1L, binForm("B-01-01", "B")))
                .isInstanceOf(DuplicateCodeException.class);

        assertThat(target.getBinCode()).isEqualTo("A-01-01");
    }

    @Test
    @DisplayName("존재하지 않는 구역을 수정하면 ResourceNotFoundException 이 발생한다")
    void update_notFound_throwsException() {
        given(warehouseBinRepository.findWithCenterById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> warehouseBinService.update(999L, binForm("Z-01-01", "Z")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("존재하지 않는 창고 구역");
    }

    @Test
    @DisplayName("사용 중지 처리하면 active 가 false 로 변경된다")
    void changeActive_deactivate() {
        // given
        WarehouseBin target = bin(1L, "A-01-01");
        given(warehouseBinRepository.findWithCenterById(1L)).willReturn(Optional.of(target));

        // when
        String binCode = warehouseBinService.changeActive(1L, false);

        // then
        assertThat(target.isActive()).isFalse();
        assertThat(binCode).isEqualTo("A-01-01");
        verify(warehouseBinRepository, never()).delete(any(WarehouseBin.class));
    }

    /* ------------------------------------------------------------------
     * 구역 선택 목록 (입고 · 이동 화면의 select)
     * ------------------------------------------------------------------ */

    @Nested
    @DisplayName("구역 선택 목록")
    class ActiveBinList {

        /**
         * 실제로 있었던 버그: 구역 코드만으로 정렬해 센터가 뒤섞였다.
         * 제2창고의 COLD-01 이 알파벳 순서상 제1창고의 C-02 와 D-01 사이에 끼어들어
         * 선택 목록이 "제1창고 C → 제2창고 COLD → 제1창고 D" 순으로 나왔다.
         */
        @Test
        @DisplayName("Repository 가 정렬한 순서(센터 → 구역코드)를 변환 과정에서 흐트러뜨리지 않는다")
        void preservesRepositoryOrder() {
            given(warehouseBinRepository.findActiveBinsForSelection())
                    .willReturn(List.of(
                            bin(1L, "C-02", CENTER_1),
                            bin(2L, "D-01", CENTER_1),
                            bin(3L, "COLD-01", CENTER_2),
                            bin(4L, "N-01", CENTER_2)));

            List<WarehouseBinDto> bins = warehouseBinService.getActiveBins();

            assertThat(bins)
                    .extracting(WarehouseBinDto::getBinCode)
                    .as("제1창고가 모두 먼저 오고 그다음 제2창고여야 한다")
                    .containsExactly("C-02", "D-01", "COLD-01", "N-01");
        }

        @Test
        @DisplayName("센터별로 묶고 센터 순서를 유지한다")
        void groupsByWarehouseKeepingOrder() {
            given(warehouseBinRepository.findActiveBinsForSelection())
                    .willReturn(List.of(
                            bin(1L, "A-01", CENTER_1),
                            bin(2L, "B-01", CENTER_1),
                            bin(3L, "COLD-01", CENTER_2)));

            Map<String, List<WarehouseBinDto>> grouped =
                    warehouseBinService.getActiveBinsByCenter();

            // HashMap 으로 모으면 키 순서가 보장되지 않아 화면에서 제2창고가 먼저 나올 수 있다
            assertThat(grouped.keySet())
                    .as("optgroup 순서가 뒤바뀌지 않도록 삽입 순서를 유지해야 한다")
                    .containsExactly("제1창고", "제2창고");

            assertThat(grouped.get("제1창고"))
                    .extracting(WarehouseBinDto::getBinCode)
                    .containsExactly("A-01", "B-01");
            assertThat(grouped.get("제2창고"))
                    .extracting(WarehouseBinDto::getBinCode)
                    .containsExactly("COLD-01");
        }

        @Test
        @DisplayName("사용 중인 구역이 없으면 빈 목록을 돌려준다")
        void emptyWhenNoActiveBins() {
            given(warehouseBinRepository.findActiveBinsForSelection())
                    .willReturn(List.of());

            assertThat(warehouseBinService.getActiveBins()).isEmpty();
            assertThat(warehouseBinService.getActiveBinsByCenter()).isEmpty();
        }
    }

    /* ------------------------------------------------------------------
     * 센터 · 구역 조건 모순 판정 (재고 현황 검색)
     * ------------------------------------------------------------------ */

    @Nested
    @DisplayName("센터와 구역 조건의 모순 판정")
    class BinCenterConsistency {

        /**
         * 검색 화면은 센터와 구역을 독립된 select 두 개로 받는다.
         * 모순된 조합을 고르면 결과가 조용히 0건이 되어, 재고가 없는 것인지
         * 조건이 잘못된 것인지 구분할 수 없다.
         */
        @Test
        @DisplayName("선택한 구역이 다른 센터에 속하면 모순으로 판정한다")
        void detectsMismatch() {
            given(warehouseBinRepository.findWithCenterById(3L))
                    .willReturn(Optional.of(bin(3L, "COLD-01", CENTER_2)));

            assertThat(warehouseBinService.isBinOutsideCenter(CENTER_1.getCenterId(), 3L))
                    .as("제1창고를 골랐는데 제2창고의 구역을 골랐다")
                    .isTrue();
        }

        @Test
        @DisplayName("선택한 구역이 선택한 센터에 속하면 모순이 아니다")
        void sameCenter_isConsistent() {
            given(warehouseBinRepository.findWithCenterById(1L))
                    .willReturn(Optional.of(bin(1L, "A-01", CENTER_1)));

            assertThat(warehouseBinService.isBinOutsideCenter(CENTER_1.getCenterId(), 1L)).isFalse();
        }

        @Test
        @DisplayName("센터나 구역 중 하나라도 선택하지 않으면 모순이 아니며 조회하지 않는다")
        void missingCondition_noQuery() {
            assertThat(warehouseBinService.isBinOutsideCenter(null, 1L)).isFalse();
            assertThat(warehouseBinService.isBinOutsideCenter(CENTER_1.getCenterId(), null)).isFalse();
            assertThat(warehouseBinService.isBinOutsideCenter(null, null)).isFalse();

            verify(warehouseBinRepository, never()).findWithCenterById(any());
        }

        @Test
        @DisplayName("존재하지 않는 구역이면 모순으로 보지 않는다 (검색 결과가 비는 것으로 충분하다)")
        void unknownBin_notMismatch() {
            given(warehouseBinRepository.findWithCenterById(999L)).willReturn(Optional.empty());

            assertThat(warehouseBinService.isBinOutsideCenter(CENTER_1.getCenterId(), 999L)).isFalse();
        }
    }

    /* ------------------------------------------------------------------
     * 픽스처
     * ------------------------------------------------------------------ */

    private WarehouseBinForm binForm(String binCode, String zone) {
        WarehouseBinForm form = new WarehouseBinForm();
        form.setBinCode(binCode);
        form.setCenterId(CENTER_1.getCenterId());
        form.setZone(zone);
        form.setRack("04");
        form.setBinLevel(1);
        form.setMaxCapacity(400);
        form.setActive(true);
        return form;
    }

    private WarehouseBin bin(Long binId, String binCode) {
        return WarehouseBin.builder()
                .binId(binId)
                .binCode(binCode)
                .center(CENTER_1)
                .zone("A")
                .rack("01")
                .binLevel(1)
                .maxCapacity(500)
                .active(true)
                .build();
    }

    /** 센터를 지정하는 픽스처 (선택 목록 정렬 · 그룹핑 검증용) */
    private WarehouseBin bin(Long binId, String binCode, Center center) {
        return WarehouseBin.builder()
                .binId(binId)
                .binCode(binCode)
                .center(center)
                .zone(binCode.split("-")[0])
                .rack("01")
                .binLevel(1)
                .maxCapacity(500)
                .active(true)
                .build();
    }
}
