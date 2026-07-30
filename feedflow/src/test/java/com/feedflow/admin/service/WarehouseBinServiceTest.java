package com.feedflow.admin.service;

import com.feedflow.admin.dto.WarehouseBinDto;
import com.feedflow.admin.dto.WarehouseBinForm;
import com.feedflow.common.exception.DuplicateCodeException;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.domain.Warehouse;
import com.feedflow.domain.WarehouseBin;
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

    @Mock
    private WarehouseBinRepository warehouseBinRepository;

    @InjectMocks
    private WarehouseBinService warehouseBinService;

    @Test
    @DisplayName("중복되지 않은 구역 코드로 등록하면 저장되고 생성된 ID를 반환한다")
    void create_success() {
        // given
        WarehouseBinForm form = binForm("A-04-01", "a");
        given(warehouseBinRepository.existsByBinCode("A-04-01")).willReturn(false);
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
    @DisplayName("다른 구역이 사용 중인 코드로 변경하면 DuplicateCodeException 이 발생한다")
    void update_duplicateCode_throwsException() {
        // given
        WarehouseBin target = bin(1L, "A-01-01");
        given(warehouseBinRepository.findById(1L)).willReturn(Optional.of(target));
        given(warehouseBinRepository.existsByBinCodeAndBinIdNot("B-01-01", 1L)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> warehouseBinService.update(1L, binForm("B-01-01", "B")))
                .isInstanceOf(DuplicateCodeException.class);

        assertThat(target.getBinCode()).isEqualTo("A-01-01");
    }

    @Test
    @DisplayName("존재하지 않는 구역을 수정하면 ResourceNotFoundException 이 발생한다")
    void update_notFound_throwsException() {
        given(warehouseBinRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> warehouseBinService.update(999L, binForm("Z-01-01", "Z")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("존재하지 않는 창고 구역");
    }

    @Test
    @DisplayName("사용 중지 처리하면 active 가 false 로 변경된다")
    void changeActive_deactivate() {
        // given
        WarehouseBin target = bin(1L, "A-01-01");
        given(warehouseBinRepository.findById(1L)).willReturn(Optional.of(target));

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
         * 실제로 있었던 버그: 구역 코드만으로 정렬해 창고가 뒤섞였다.
         * 제2창고의 COLD-01 이 알파벳 순서상 제1창고의 C-02 와 D-01 사이에 끼어들어
         * 선택 목록이 "제1창고 C → 제2창고 COLD → 제1창고 D" 순으로 나왔다.
         */
        @Test
        @DisplayName("Repository 가 정렬한 순서(창고 → 구역코드)를 변환 과정에서 흐트러뜨리지 않는다")
        void preservesRepositoryOrder() {
            given(warehouseBinRepository.findByActiveTrueOrderByWarehouseAscBinCodeAsc())
                    .willReturn(List.of(
                            bin(1L, "C-02", Warehouse.WH1),
                            bin(2L, "D-01", Warehouse.WH1),
                            bin(3L, "COLD-01", Warehouse.WH2),
                            bin(4L, "N-01", Warehouse.WH2)));

            List<WarehouseBinDto> bins = warehouseBinService.getActiveBins();

            assertThat(bins)
                    .extracting(WarehouseBinDto::getBinCode)
                    .as("제1창고가 모두 먼저 오고 그다음 제2창고여야 한다")
                    .containsExactly("C-02", "D-01", "COLD-01", "N-01");
        }

        @Test
        @DisplayName("창고별로 묶고 창고 순서를 유지한다")
        void groupsByWarehouseKeepingOrder() {
            given(warehouseBinRepository.findByActiveTrueOrderByWarehouseAscBinCodeAsc())
                    .willReturn(List.of(
                            bin(1L, "A-01", Warehouse.WH1),
                            bin(2L, "B-01", Warehouse.WH1),
                            bin(3L, "COLD-01", Warehouse.WH2)));

            Map<Warehouse, List<WarehouseBinDto>> grouped =
                    warehouseBinService.getActiveBinsByWarehouse();

            // HashMap 으로 모으면 키 순서가 보장되지 않아 화면에서 제2창고가 먼저 나올 수 있다
            assertThat(grouped.keySet())
                    .as("optgroup 순서가 뒤바뀌지 않도록 삽입 순서를 유지해야 한다")
                    .containsExactly(Warehouse.WH1, Warehouse.WH2);

            assertThat(grouped.get(Warehouse.WH1))
                    .extracting(WarehouseBinDto::getBinCode)
                    .containsExactly("A-01", "B-01");
            assertThat(grouped.get(Warehouse.WH2))
                    .extracting(WarehouseBinDto::getBinCode)
                    .containsExactly("COLD-01");
        }

        @Test
        @DisplayName("사용 중인 구역이 없으면 빈 목록을 돌려준다")
        void emptyWhenNoActiveBins() {
            given(warehouseBinRepository.findByActiveTrueOrderByWarehouseAscBinCodeAsc())
                    .willReturn(List.of());

            assertThat(warehouseBinService.getActiveBins()).isEmpty();
            assertThat(warehouseBinService.getActiveBinsByWarehouse()).isEmpty();
        }
    }

    /* ------------------------------------------------------------------
     * 픽스처
     * ------------------------------------------------------------------ */

    private WarehouseBinForm binForm(String binCode, String zone) {
        WarehouseBinForm form = new WarehouseBinForm();
        form.setBinCode(binCode);
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
                .zone("A")
                .rack("01")
                .binLevel(1)
                .maxCapacity(500)
                .active(true)
                .build();
    }

    /** 창고를 지정하는 픽스처 (선택 목록 정렬 · 그룹핑 검증용) */
    private WarehouseBin bin(Long binId, String binCode, Warehouse warehouse) {
        return WarehouseBin.builder()
                .binId(binId)
                .binCode(binCode)
                .warehouse(warehouse)
                .zone(binCode.split("-")[0])
                .rack("01")
                .binLevel(1)
                .maxCapacity(500)
                .active(true)
                .build();
    }
}
