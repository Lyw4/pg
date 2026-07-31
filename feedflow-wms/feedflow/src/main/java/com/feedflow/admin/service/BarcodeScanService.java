package com.feedflow.admin.service;

import com.feedflow.admin.dto.BarcodeLabelDto;
import com.feedflow.admin.dto.InboundForm;
import com.feedflow.admin.dto.InboundResultDto;
import com.feedflow.admin.dto.OutboundForm;
import com.feedflow.admin.dto.OutboundResultDto;
import com.feedflow.admin.dto.ScanInboundRequest;
import com.feedflow.admin.dto.ScanOutboundRequest;
import com.feedflow.admin.dto.ScanResultDto;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.common.util.Texts;
import com.feedflow.domain.Inventory;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
import com.feedflow.repository.InventoryRepository;
import com.feedflow.repository.ProductLotRepository;
import com.feedflow.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 바코드 / QR 코드 스캔 조회 서비스.
 * <p>
 * 현장 작업자가 스캔한 문자열을 아래 순서로 해석한다.
 * <ol>
 *     <li><b>로트번호</b>로 조회 (가장 구체적인 정보이므로 우선)</li>
 *     <li>없으면 <b>품목코드</b>로 조회</li>
 *     <li>둘 다 없으면 {@link ResourceNotFoundException} → API 는 404 응답</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BarcodeScanService {

    private static final int MAX_CODE_LENGTH = 100;

    private final ProductRepository productRepository;
    private final ProductLotRepository productLotRepository;
    private final InventoryRepository inventoryRepository;

    /** 스캔 후 즉시 입출고를 위해 검증된 기존 로직을 그대로 재사용한다 */
    private final InventoryService inventoryService;
    private final OutboundService outboundService;

    /**
     * 스캔된 코드를 조회한다.
     *
     * @param rawCode 스캔 원본 문자열 (공백/대소문자 무관)
     * @throws BusinessRuleException     코드가 비어있거나 너무 긴 경우
     * @throws ResourceNotFoundException 등록되지 않은 코드인 경우
     */
    public ScanResultDto scan(String rawCode) {
        String code = normalize(rawCode);
        LocalDate today = LocalDate.now();

        // 1) 로트번호 우선 조회
        Optional<ProductLot> lot = findLotByNo(code);
        if (lot.isPresent()) {
            List<Inventory> inventories = inventoryRepository.findByLotIdWithBin(lot.get().getLotId());
            return ScanResultDto.ofLot(lot.get(), inventories, today);
        }

        // 2) 품목코드 조회
        Product product = findProductByCode(code);
        // 스캔은 전국 재고를 보여준다. 창고 담당자가 어느 센터에 있든 "이 품목이 어디에
        // 얼마나 있는지" 를 알아야 하므로 센터로 좁히지 않는다.
        List<Inventory> inventories = inventoryRepository.search(null, product.getProductId(), null, null);
        return ScanResultDto.ofProduct(product, inventories, today);
    }

    /* ==================================================================
     * 스캔 후 즉시 입출고 (현장 작업 흐름)
     * ================================================================== */

    /**
     * 스캔한 코드로 바로 입고 처리.
     * <ul>
     *     <li>로트번호를 스캔한 경우 : 해당 로트에 수량을 합산 (제조일자는 기존 로트 값 사용)</li>
     *     <li>품목코드를 스캔한 경우 : 로트번호를 자동 부여해 새 로트를 생성</li>
     * </ul>
     */
    @Transactional
    public InboundResultDto receiveByCode(ScanInboundRequest request, Long userId, String userName) {
        String code = normalize(request.getCode());

        InboundForm form = new InboundForm();
        form.setBinId(request.getBinId());
        form.setQuantity(request.getQuantity());
        form.setMemo(Texts.defaultIfBlank(request.getMemo(), "바코드 스캔 입고"));

        Optional<ProductLot> existingLot = findLotByNo(code);
        if (existingLot.isPresent()) {
            ProductLot lot = existingLot.get();
            form.setProductId(lot.getProduct().getProductId());
            form.setLotNo(lot.getLotNo());
            form.setManufacturedDate(lot.getManufacturedDate());
        } else {
            Product product = findProductByCode(code);
            form.setProductId(product.getProductId());
            form.setLotNo(null);   // 자동 부여
            form.setManufacturedDate(
                    request.getManufacturedDate() == null ? LocalDate.now() : request.getManufacturedDate());
        }

        return inventoryService.receive(form, userId, userName);
    }

    /**
     * 스캔한 코드로 바로 출고 처리.
     * <p>
     * 출고 로트는 지정하지 않고 <b>유통기한이 임박한 로트부터 자동 차감(FEFO)</b>한다.
     * 따라서 특정 로트를 스캔했더라도 더 먼저 만료되는 다른 로트가 차감될 수 있으며,
     * 실제 차감된 로트는 응답의 lines 로 확인할 수 있다.
     */
    @Transactional
    public OutboundResultDto dispatchByCode(ScanOutboundRequest request, Long userId, String userName) {
        String code = normalize(request.getCode());
        Product product = resolveProduct(code);

        OutboundForm form = new OutboundForm();
        form.setProductId(product.getProductId());
        form.setQuantity(request.getQuantity());
        form.setMemo(Texts.defaultIfBlank(request.getMemo(), "바코드 스캔 출고"));

        return outboundService.dispatch(form, userId, userName);
    }

    /* ==================================================================
     * 라벨 (테스트/현장 부착용)
     * ================================================================== */

    /** 전체 로트 라벨 (QR 코드로 렌더링) */
    public List<BarcodeLabelDto> getLotLabels() {
        LocalDate today = LocalDate.now();
        return productLotRepository.findAllWithProduct().stream()
                .map(lot -> BarcodeLabelDto.ofLot(lot, today))
                .toList();
    }

    /** 사용 중인 품목 라벨 */
    public List<BarcodeLabelDto> getProductLabels() {
        return productRepository.findByActiveTrueOrderByProductCodeAsc().stream()
                .map(BarcodeLabelDto::ofProduct)
                .toList();
    }

    /* ==================================================================
     * 내부 헬퍼
     * ================================================================== */

    /**
     * 로트번호로 로트를 찾는다.
     * 로트번호는 품목 단위로 유일하므로 서로 다른 품목에 같은 번호가 있을 수 있어
     * 유통기한이 가장 임박한 로트를 우선한다.
     */
    private Optional<ProductLot> findLotByNo(String code) {
        return productLotRepository.findAllByLotNo(code).stream().findFirst();
    }

    /** 코드(로트번호 또는 품목코드)로 품목을 찾는다 */
    private Product resolveProduct(String code) {
        return findLotByNo(code)
                .map(ProductLot::getProduct)
                .orElseGet(() -> findProductByCode(code));
    }

    private Product findProductByCode(String code) {
        return productRepository.findByProductCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "등록되지 않은 바코드입니다. 로트번호 또는 품목코드를 확인하세요. (스캔값: " + code + ")"));
    }

    /** 스캔 값 정규화 : 앞뒤 공백 제거 + 대문자 변환 */
    private String normalize(String rawCode) {
        if (Texts.isBlank(rawCode)) {
            throw new BusinessRuleException("스캔된 코드가 비어 있습니다.");
        }
        String code = Texts.code(rawCode);
        if (code.length() > MAX_CODE_LENGTH) {
            throw new BusinessRuleException("스캔된 코드가 너무 깁니다. (최대 " + MAX_CODE_LENGTH + "자)");
        }
        return code;
    }
}
