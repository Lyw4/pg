package com.ex.service;

import com.ex.dto.AdminProductRequest;
import com.ex.dto.ProductResponse;
import com.ex.entity.Manufacturer;
import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.repository.ManufacturerRepository;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.WarehouseAllocationRepository;
import com.ex.repository.WarehouseRepository;
import com.ex.entity.WarehouseAllocation;
import com.ex.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminProductService {

    private final ProductRepository productRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final ProductLotRepository productLotRepository;
	private final WmsStockCoordinator wmsStockCoordinator;
	private final WarehouseRepository warehouseRepository;
	private final WarehouseAllocationRepository allocationRepository;
	private final InventoryService inventoryService;
	private final SellableStockQuery sellableStockQuery;

    @Transactional
    public ProductResponse create(AdminProductRequest request) {
        Manufacturer manufacturer = findOrCreateManufacturer(request.manufacturerName());
        Product product = new Product(
                manufacturer,
                request.name(),
                categoryLabel(request),
                request.weightKg(),
                BigDecimal.valueOf(request.price()),
                0,
                shelfLifeMonths(request),
                request.description());
        applyProduct(product, manufacturer, request);
        productRepository.save(product);
		ensureWarehouseAllocations(product);

        ProductLot lot = createLot(product, request);
        productLotRepository.save(lot);
        product.addLot(lot);
        product.changeStock(request.lotQuantity());
		wmsStockCoordinator.inbound(
				lot,
				request.lotQuantity(),
				null,
				"관리자 상품 등록 초기 LOT",
				"관리자");
		inventoryService.synchronizeWarehouseStock(product);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse update(Long productId, AdminProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        Manufacturer manufacturer =
                findOrCreateManufacturer(request.manufacturerName());
        applyProduct(product, manufacturer, request);

        // 요청에 담긴 LOT 번호로 대상을 특정합니다. 예전에는 만료 여부와
        // 무관하게 "가장 먼저 조회된 LOT"을 잡았기 때문에, LOT이 여러 개인
        // 상품을 수정하면 화면에 보이지 않던 LOT의 번호·날짜·수량이 덮어써지고
        // 총재고가 중복 증가했습니다.
        String requestedLotNo = request.lotNumber() == null
                ? ""
                : request.lotNumber().trim();
        ProductLot lot = productLotRepository
                .findByProductProductIdOrderByExpirationDateAsc(productId)
                .stream()
                .filter(candidate -> candidate.getLotNo() != null
                        && candidate.getLotNo().equalsIgnoreCase(requestedLotNo))
                .findFirst()
                .orElse(null);
        if (lot == null) {
            lot = createLot(product, request);
            productLotRepository.save(lot);
            product.addLot(lot);
            product.changeStock(request.lotQuantity());
			wmsStockCoordinator.inbound(
					lot,
					request.lotQuantity(),
					null,
					"관리자 상품 수정 신규 LOT",
					"관리자");
        } else {
            int difference =
                    request.lotQuantity() - lot.getLotQuantity();
            lot.updateDetails(
                    request.lotNumber(),
                    request.manufacturedDate(),
                    request.expirationDate(),
                    request.lotQuantity());
            product.changeStock(difference);
			wmsStockCoordinator.adjust(
					lot,
					difference,
					null,
					"관리자 상품 LOT 수량 수정",
					"관리자");
		}
		ensureWarehouseAllocations(product);
		inventoryService.synchronizeWarehouseStock(product);
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        product.deactivate();
    }

    /**
     * 관리자 화면용 상품 목록입니다. 판매 중지 상품까지 포함해, 중지한 상품을
     * 다시 판매로 되돌릴 수 있게 합니다. 재고 수치는 고객 화면과 같은 기준
     * (판매 가능 수량)으로 채웁니다.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> findAllForAdmin() {
        List<Product> products = productRepository.findAllByOrderByNameAsc();
        Map<Long, Integer> sellableStocks = sellableStockQuery
                .sellableByProductIds(products.stream()
                        .map(Product::getProductId)
                        .toList());
        return products.stream()
                .map(product -> ProductResponse.from(product)
                        .withSellableStock(sellableStocks.getOrDefault(
                                product.getProductId(), 0)))
                .toList();
    }

	private void ensureWarehouseAllocations(Product product) {
		warehouseRepository.findAllByActiveTrueOrderByDisplayOrderAsc()
				.forEach(warehouse -> allocationRepository
						.findByWarehouseWarehouseIdAndProductProductId(
								warehouse.getWarehouseId(), product.getProductId())
						.orElseGet(() -> allocationRepository.save(
								new WarehouseAllocation(warehouse, product, 0, 0))));
	}

    private Manufacturer findOrCreateManufacturer(String name) {
        return manufacturerRepository.findByCompanyName(name)
                .orElseGet(() -> manufacturerRepository.save(
                        new Manufacturer(name, "고객몰 등록", "")));
    }

    private void applyProduct(
            Product product,
            Manufacturer manufacturer,
            AdminProductRequest request) {
        product.updateForStorefront(
                manufacturer,
                request.name(),
                categoryLabel(request),
                request.feedStage(),
                request.description(),
                request.weightKg(),
                BigDecimal.valueOf(request.price()),
                request.originalPrice(),
                request.proteinPercent(),
                request.fatPercent(),
                request.fiberPercent(),
                request.calciumPercent(),
                request.imageUrl(),
                request.badge(),
                automaticDisplayTone(request),
                automaticDisplayShape(request));
        product.configureShelfLife(shelfLifeMonths(request));
        product.activate();
    }

    private ProductLot createLot(Product product, AdminProductRequest request) {
        return new ProductLot(
                product,
                request.lotNumber(),
                request.manufacturedDate(),
                request.expirationDate(),
                request.lotQuantity());
    }

    private int shelfLifeMonths(AdminProductRequest request) {
        long months = ChronoUnit.MONTHS.between(
                request.manufacturedDate(),
                request.expirationDate());
        return (int) Math.max(1, months);
    }

    private String categoryLabel(AdminProductRequest request) {
        return switch (request.animalType()) {
            case CATTLE, DAIRY_CATTLE -> "소";
            case PIG -> "돼지";
            case CHICKEN, DUCK -> "조류(닭/오리)";
            case PET -> "반려동물";
            case SUPPLEMENT -> "영양제";
        };
    }

    private String automaticDisplayTone(AdminProductRequest request) {
        return switch (request.animalType()) {
            case CATTLE -> "amber";
            case DAIRY_CATTLE -> "blue";
            case PIG -> "rose";
            case CHICKEN -> "gold";
            case DUCK -> "teal";
            case PET -> "violet";
            case SUPPLEMENT -> "green";
        };
    }

    private String automaticDisplayShape(AdminProductRequest request) {
        return switch (request.animalType()) {
            case CHICKEN -> "crumble";
            case PET -> "kibble";
            case SUPPLEMENT -> "powder";
            default -> "pellet";
        };
    }
}
