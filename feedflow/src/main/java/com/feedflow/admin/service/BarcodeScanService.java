package com.feedflow.admin.service;

import com.feedflow.admin.dto.ScanResultDto;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.ResourceNotFoundException;
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
        List<ProductLot> lots = productLotRepository.findAllByLotNo(code);
        if (!lots.isEmpty()) {
            ProductLot lot = lots.get(0);
            List<Inventory> inventories = inventoryRepository.findByLotIdWithBin(lot.getLotId());
            return ScanResultDto.ofLot(lot, inventories, today);
        }

        // 2) 품목코드 조회
        Product product = productRepository.findByProductCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "등록되지 않은 바코드입니다. 로트번호 또는 품목코드를 확인하세요. (스캔값: " + code + ")"));

        List<Inventory> inventories = inventoryRepository.search(product.getProductId(), null, null);
        return ScanResultDto.ofProduct(product, inventories, today);
    }

    /** 스캔 값 정규화 : 앞뒤 공백 제거 + 대문자 변환 */
    private String normalize(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new BusinessRuleException("스캔된 코드가 비어 있습니다.");
        }
        String code = rawCode.trim().toUpperCase();
        if (code.length() > MAX_CODE_LENGTH) {
            throw new BusinessRuleException("스캔된 코드가 너무 깁니다. (최대 " + MAX_CODE_LENGTH + "자)");
        }
        return code;
    }
}
