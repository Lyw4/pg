package com.ex;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import com.ex.entity.Product;
import com.ex.entity.CustomerOrder;
import com.ex.entity.Delivery;
import com.ex.entity.Delivery.ReturnStatus;
import com.ex.entity.FarmCustomer.CustomerStatus;
import com.ex.entity.Shipment;
import com.ex.repository.CustomerOrderRepository;
import com.ex.repository.DeliveryRepository;
import com.ex.repository.ProductRepository;
import com.ex.repository.ShipmentRepository;
import com.ex.service.DistributionService;
import com.ex.service.FarmCustomerSeeder;
import com.ex.service.FarmCustomerService;
import com.ex.service.InventoryService;
import com.ex.service.RecurringDeliveryService;
import com.ex.service.ShipmentService;
import com.ex.service.SellableStockQuery;
import com.ex.service.WarehouseManagementService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "admin", roles = "ADMIN")
class FinalProjectApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private InventoryService inventoryService;

	@Autowired
	private DistributionService distributionService;

	@Autowired
	private ShipmentService shipmentService;

	@Autowired
	private CustomerOrderRepository orderRepository;

	@Autowired
	private ShipmentRepository shipmentRepository;

	@Autowired
	private DeliveryRepository deliveryRepository;

	@Autowired
	private WarehouseManagementService warehouseManagementService;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private RecurringDeliveryService recurringDeliveryService;

	@Autowired
	private FarmCustomerService farmCustomerService;

	@Autowired
	private FarmCustomerSeeder farmCustomerSeeder;

	@Autowired
	private SellableStockQuery sellableStockQuery;

	@Test
	void contextLoads() {
	}

	@Test
	void inventoryRendersIntegratedDefectTab() throws Exception {
		mockMvc.perform(get("/inventory").queryParam("view", "defects"))
				.andExpect(status().isOk())
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString("data-summary-panel=\"defects\"")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString("불량 등록 및 격리")));
	}

	@Test
	void warehousePlanSeedsFiveSitesAndAllFeedAllocations() {
		assertEquals(5, warehouseManagementService.warehouses().size());
		assertEquals(
				productRepository
						.findAllByActiveTrueOrderByProductIdAsc()
						.size() * 5,
				warehouseManagementService.allocations().size());
		assertTrue(
				warehouseManagementService
						.totalMonthlyPlannedQuantity() >= 109741);
		assertTrue(
				warehouseManagementService
						.totalTargetStockQuantity() >= 80545);
		assertTrue(
				warehouseManagementService.totalCurrentStockQuantity()
						>= warehouseManagementService
								.totalTargetStockQuantity());
		assertEquals(
				0,
				warehouseManagementService.lowStockAllocationCount());
	}

	@Test
	void farmCustomersAreSeededAcrossAllFiveWarehouses() {
		assertEquals(20, farmCustomerService.customers().size());
		assertEquals(18, farmCustomerService.activeCount());
		assertEquals(
				32740,
				farmCustomerService.totalMonthlyFeedQuantity());

		var summaries = farmCustomerService.warehouseSummaries();
		assertEquals(5, summaries.size());
		assertEquals(4, summaries.get("W01").customerCount());
		assertEquals(7050, summaries.get("W01").monthlyFeedQuantity());
		assertEquals(4, summaries.get("W05").customerCount());
		assertEquals(5470, summaries.get("W05").monthlyFeedQuantity());
	}

	@Test
	void inventoryRendersWarehouseCenteredStockInsteadOfLegacyStock()
			throws Exception {
		mockMvc.perform(get("/inventory").queryParam("view", "stock"))
				.andExpect(status().isOk())
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"5개 거점 전체 재고")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"id=\"warehouseStockTable\"")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"id=\"warehouseStockModal\"")))
				.andExpect(content().string(
						org.hamcrest.Matchers.not(
								org.hamcrest.Matchers.containsString(
										"카테고리별 상품 재고"))));
	}

	@Test
	@Transactional
	void warehouseCurrentStockCanBeAdjustedAndMarkedLow() {
		var allocation = warehouseManagementService.allocations()
				.stream()
				.findFirst()
				.orElseThrow();
		int target = allocation.getTargetStockQuantity();

		warehouseManagementService.adjustCurrentStock(
				allocation.getAllocationId(),
				Math.max(0, target - 1));

		assertEquals(
				Math.max(0, target - 1),
				allocation.getCurrentStockQuantity());
		assertEquals(target > 0, allocation.isLowStock());
	}

	@Test
	void recurringDeliveriesAreSeededForEveryWarehouseAndFeed() {
		assertEquals(300, recurringDeliveryService.deliveries().size());
		assertEquals(300, recurringDeliveryService.activeCount());

		var summaries = recurringDeliveryService.warehouseSummaries();
		assertEquals(5, summaries.size());
		assertEquals(60, summaries.get("W01").activeScheduleCount());
		assertEquals("1일 · 15일", summaries.get("W01").deliveryDays());
		assertEquals(14746, summaries.get("W01").monthlyQuantity());
		assertEquals(28066, summaries.get("W05").monthlyQuantity());
	}

	@Test
	void recurringDeliveryRendersWarehouseFiltersAndScheduleTable()
			throws Exception {
		mockMvc.perform(get("/inventory").queryParam("view", "recurring"))
				.andExpect(status().isOk())
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"5개 창고 월간 정기 입고")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"id=\"recurringWarehouseTable\"")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"정기 입고 창고 선택")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"나주 문평 창고")));
	}

	@Test
	@Transactional
	void recurringReceiptIncreasesSelectedWarehouseStock() {
		var delivery = recurringDeliveryService.deliveries()
				.stream()
				.filter(item -> "W01".equals(
						item.getWarehouse().getCode()))
				.findFirst()
				.orElseThrow();
		var allocation = warehouseManagementService.allocations()
				.stream()
				.filter(item -> item.getWarehouse().getWarehouseId()
						.equals(delivery.getWarehouse().getWarehouseId()))
				.filter(item -> item.getProduct().getProductId()
						.equals(delivery.getProduct().getProductId()))
				.findFirst()
				.orElseThrow();
		int warehouseStockBefore =
				allocation.getCurrentStockQuantity();
		int productStockBefore =
				delivery.getProduct().getTotalStock();

		recurringDeliveryService.receive(
				delivery.getRecurringDeliveryId(),
				LocalDate.now());

		assertEquals(
				warehouseStockBefore + delivery.getQuantity(),
				allocation.getCurrentStockQuantity());
		assertEquals(
				productStockBefore + delivery.getQuantity(),
				delivery.getProduct().getTotalStock());
	}

	@Test
	void inventoryRendersWarehouseManagementTab() throws Exception {
		mockMvc.perform(get("/inventory").queryParam("view", "warehouses"))
				.andExpect(status().isOk())
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"data-summary-panel=\"warehouses\"")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"5개 거점 창고 관리")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"예산 고덕 창고")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"나주 문평 창고")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"한우 송아지 스타터")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"육용오리 그로워")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"id=\"warehousePlanModal\"")));
	}

	@Test
	void distributionRendersShipmentWorkflow() throws Exception {
		mockMvc.perform(get("/distribution"))
				.andExpect(status().isOk())
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString("출고 작업")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"data-delivery-view=\"cancelled\"")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString("취소 배송")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"테스트 주문 생성")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"id=\"demoAddressSearch\"")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"가까운 창고 자동 배정")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"자동 배정 창고")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"postcode.v2.js")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"data-delivery-view=\"cancelled_orders\"")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"id=\"orderCancelModal\"")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"action=\"/distribution/orders/0/cancel\"")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"name=\"_csrf\"")));
	}

	@Test
	void distributionRendersFarmCustomerManagementTab()
			throws Exception {
		mockMvc.perform(get("/distribution").queryParam("view", "farms"))
				.andExpect(status().isOk())
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"data-delivery-panel=\"farms\"")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"거점별 농장 고객사 관리")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"id=\"farmCustomerTable\"")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"예산 고덕 한우농장")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"나주 문평 오리농장")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"data-farm-status=\"ACTIVE\"")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"거래 보류로 변경")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"거래 재개")));
	}

	@Test
	@Transactional
	void farmCustomerStatusCanBeChangedImmediatelyAndPersistsAfterSeed()
			throws Exception {
		var farm = farmCustomerService.customers().stream()
				.filter(item -> "F-W03-04".equals(item.getFarmCode()))
				.findFirst()
				.orElseThrow();
		assertEquals(CustomerStatus.PAUSED, farm.getStatus());

		MvcResult page = mockMvc.perform(
				get("/distribution").queryParam("view", "farms"))
				.andExpect(status().isOk())
				.andReturn();
		CsrfToken csrfToken = (CsrfToken) page.getRequest()
				.getAttribute(CsrfToken.class.getName());
		MockHttpSession session =
				(MockHttpSession) page.getRequest().getSession(false);

		mockMvc.perform(post(
				"/distribution/farm-customers/{farmCustomerId}/status",
				farm.getFarmCustomerId())
				.session(session)
				.param(csrfToken.getParameterName(), csrfToken.getToken())
				.param("status", "ACTIVE"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/distribution?view=farms"));

		assertEquals(CustomerStatus.ACTIVE, farm.getStatus());
		farmCustomerSeeder.seed();
		assertEquals(CustomerStatus.ACTIVE, farm.getStatus());
	}

	@Test
	@Transactional
	void farmCustomerOrderKeepsCustomerLinkAndNearestWarehouse() {
		var farm = farmCustomerService.customers().stream()
				.filter(item -> "F-W05-01".equals(item.getFarmCode()))
				.findFirst()
				.orElseThrow();
		var reservedBefore = inventoryService.reservedStockByLot();
		var lot = inventoryService.lots().stream()
				.filter(item -> farm.getPreferredFeed().equals(
						item.getProduct().getName()))
				.filter(item -> item.getLotQuantity()
						- reservedBefore.getOrDefault(
								item.getLotId(), 0) >= 1)
				.findFirst()
				.orElseThrow();

		Long orderId = distributionService.createDemoOrder(
				farm.getFarmCustomerId(),
				lot.getLotId(), 1, BigDecimal.ZERO,
				farm.getRepresentativeName(), farm.getPhone(),
				farm.getPostalCode(), farm.getAddress(),
				null, null, farm.getLatitude(), farm.getLongitude(),
				"농장 고객사 연결 테스트");

		var order = orderRepository.findById(orderId).orElseThrow();
		assertNotNull(order.getFarmCustomer());
		assertEquals(
				farm.getFarmCustomerId(),
				order.getFarmCustomer().getFarmCustomerId());
		assertEquals(
				"F-W05-01",
				order.getFarmCustomer().getFarmCode());
		assertNotNull(order.getFulfillmentWarehouse());
		assertTrue(sellableStockQuery.sellableByWarehouseAndProductIds(
				List.of(lot.getProduct().getProductId())).getOrDefault(
						sellableStockQuery.stockKey(
								order.getFulfillmentWarehouse().getWarehouseId(),
								lot.getProduct().getProductId()), 0) >= 1);
	}

	@Test
	@Transactional
	void demoOrderReservesSelectedLotStock() {
		var reservedBefore = inventoryService.reservedStockByLot();
		var lot = inventoryService.lots().stream()
				.filter(item -> "한우 송아지 스타터".equals(
						item.getProduct().getName()))
				.filter(item -> item.getLotQuantity()
						- reservedBefore.getOrDefault(item.getLotId(), 0) >= 2)
				.findFirst()
				.orElseThrow();
		int originalReserved =
				reservedBefore.getOrDefault(lot.getLotId(), 0);

		Long orderId = distributionService.createDemoOrder(
				lot.getLotId(), 2, BigDecimal.valueOf(1000),
				"발표 수령인", "010-0000-0000",
				"06236", "서울특별시 강남구 테헤란로 123",
				"서울특별시 강남구 역삼동 123",
				"4층 발표장", 37.500123, 127.035123,
				"도착 전 연락");

		var order = orderRepository.findById(orderId).orElseThrow();
		assertEquals(
				CustomerOrder.OrderStatus.PAID,
				order.getStatus());
		assertEquals("발표 수령인", order.getRecipientName());
		assertEquals("06236", order.getPostalCode());
		assertEquals(
				"[06236] 서울특별시 강남구 테헤란로 123 4층 발표장",
				order.getShippingAddress());
		assertEquals(37.500123, order.getLatitude());
		assertNotNull(order.getFulfillmentWarehouse());
		assertEquals(
				"W04",
				order.getFulfillmentWarehouse().getCode());
		assertNotNull(order.getFulfillmentDistanceKm());
		assertEquals(
				"배송지 좌표 기준 자동 배정",
				order.getFulfillmentAssignmentBasis());
		assertEquals(
				originalReserved + 2,
				inventoryService.reservedStockByLot()
						.get(lot.getLotId()));
	}

	@Test
	@Transactional
	void addressRegionFallbackAssignsJeonbukWarehouse() {
		var reservedBefore = inventoryService.reservedStockByLot();
		var lot = inventoryService.lots().stream()
				.filter(item -> "한우 송아지 스타터".equals(
						item.getProduct().getName()))
				.filter(item -> item.getLotQuantity()
						- reservedBefore.getOrDefault(
								item.getLotId(), 0) >= 1)
				.findFirst()
				.orElseThrow();

		Long orderId = distributionService.createDemoOrder(
				lot.getLotId(), 1, BigDecimal.ZERO,
				"전북 농장", "010-2000-3000",
				"54321", "전북특별자치도 김제시 농장로 10",
				null, "축사 앞", null, null, null);

		var order = orderRepository.findById(orderId).orElseThrow();
		assertNotNull(order.getFulfillmentWarehouse());
		assertEquals(
				"주소 권역 기준 자동 배정",
				order.getFulfillmentAssignmentBasis());
	}

	@Test
	@Transactional
	void shipmentDeductsAndCancellationRestoresAssignedWarehouseStock() {
		var reservedBefore = inventoryService.reservedStockByLot();
		var lot = inventoryService.lots().stream()
				.filter(item -> "한우 송아지 스타터".equals(
						item.getProduct().getName()))
				.filter(item -> item.getLotQuantity()
						- reservedBefore.getOrDefault(
								item.getLotId(), 0) >= 1)
				.findFirst()
				.orElseThrow();

		Long orderId = distributionService.createDemoOrder(
				lot.getLotId(), 1, BigDecimal.ZERO,
				"나주 농장", "010-4000-5000",
				"58291", "전라남도 나주시 문평면 농장길 10",
				null, null, 35.0459, 126.8447, null);
		var order = orderRepository.findById(orderId).orElseThrow();
		assertNotNull(order.getFulfillmentWarehouse());

		var allocation = warehouseManagementService.allocations()
				.stream()
				.filter(item -> item.getWarehouse().getWarehouseId()
						.equals(order.getFulfillmentWarehouse()
								.getWarehouseId()))
				.filter(item -> item.getProduct().getProductId()
						.equals(lot.getProduct().getProductId()))
				.findFirst()
				.orElseThrow();
		int originalWarehouseStock =
				allocation.getCurrentStockQuantity();

		shipmentService.create(
				orderId, "자동 배정 테스트", "나주 창고 출고");
		var shipment = shipmentRepository.findByOrderOrderId(orderId)
				.orElseThrow();
		shipmentService.startPicking(
				shipment.getShipmentId(), "피킹 담당자");
		shipmentService.inspect(
				shipment.getShipmentId(), "검수 담당자");
		shipmentService.complete(
				shipment.getShipmentId(), "출고 담당자");

		assertEquals(
				originalWarehouseStock - 1,
				allocation.getCurrentStockQuantity());

		shipmentService.cancelCompleted(
				shipment.getShipmentId(), "자동 배정 출고 취소 테스트");
		assertEquals(
				originalWarehouseStock,
				allocation.getCurrentStockQuantity());
	}

	@Test
	@Transactional
	void deliveryDetailRendersLotTrackingAndStatusHistory() throws Exception {
		var order = orderRepository.save(new CustomerOrder(
				999L, BigDecimal.valueOf(50000),
				BigDecimal.ZERO, "서울시 테스트 배송지"));
		shipmentRepository.save(new Shipment(
				order, "테스트 담당자", "배송 상세 렌더링 테스트"));
		var delivery = deliveryRepository.save(new Delivery(
				order, "테스트 운송사", "TEST-1234"));

		distributionService.updateDelivery(
				delivery.getDeliveryId(),
				Delivery.DeliveryStatus.PICKED_UP,
				"테스트 택배 인계");

		mockMvc.perform(get(
				"/distribution/deliveries/{deliveryId}",
				delivery.getDeliveryId()))
				.andExpect(status().isOk())
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString("DELIVERY TRACE")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString("출고 제품과 LOT")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString("테스트 택배 인계")));
	}

	@Test
	@Transactional
	void deliveryCanBeCancelledAndReactivated() throws Exception {
		var order = orderRepository.save(new CustomerOrder(
				998L, BigDecimal.valueOf(42000),
				BigDecimal.ZERO, "부산시 테스트 배송지"));
		var delivery = deliveryRepository.save(new Delivery(
				order, "기존 운송사", "OLD-1000"));

		distributionService.cancelDelivery(
				delivery.getDeliveryId(), "수령 일정 변경", "테스트 관리자");
		assertEquals(
				Delivery.DeliveryStatus.CANCELLED,
				delivery.getStatus());
		mockMvc.perform(get("/distribution").queryParam("view", "cancelled"))
				.andExpect(status().isOk())
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString(
								"data-status=\"CANCELLED\"")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString("재배송 등록")));

		distributionService.reactivateDelivery(
				delivery.getDeliveryId(), "재배송 운송사", "NEW-2000");
		assertEquals(
				Delivery.DeliveryStatus.PICKED_UP,
				delivery.getStatus());
		assertEquals("NEW-2000", delivery.getTrackingNumber());
		assertEquals(
				2,
				distributionService.deliveryHistories(
						delivery.getDeliveryId()).size());
	}

	@Test
	@Transactional
	void deliveredGoodsCanBeReturnedAndRestockedAfterInspection() {
		var reservedBefore = inventoryService.reservedStockByLot();
		var lot = inventoryService.lots().stream()
				.filter(item -> item.getLotQuantity()
						- reservedBefore.getOrDefault(item.getLotId(), 0) >= 1)
				.findFirst()
				.orElseThrow();
		int originalLotQuantity = lot.getLotQuantity();
		int originalProductQuantity = lot.getProduct().getTotalStock();

		Long orderId = distributionService.createDemoOrder(
				lot.getLotId(), 1, BigDecimal.ZERO,
				"회수 테스트", "010-5555-6666",
				"06236", "서울특별시 강남구 테헤란로 123",
				null, "회수 테스트 주소", null, null, null);
		shipmentService.create(orderId, "출고 담당자", "회수 흐름 테스트");
		var shipment = shipmentRepository.findByOrderOrderId(orderId)
				.orElseThrow();
		shipmentService.startPicking(shipment.getShipmentId(), "피킹 담당자");
		shipmentService.inspect(shipment.getShipmentId(), "검수 담당자");
		shipmentService.complete(shipment.getShipmentId(), "출고 담당자");
		assertEquals(originalLotQuantity - 1, lot.getLotQuantity());

		distributionService.registerDelivery(
				orderId, "테스트 운송사", "RETURN-1000");
		var delivery = deliveryRepository.findByOrderOrderId(orderId)
				.orElseThrow();
		distributionService.updateDelivery(
				delivery.getDeliveryId(),
				Delivery.DeliveryStatus.IN_TRANSIT, "배송 중");
		distributionService.updateDelivery(
				delivery.getDeliveryId(),
				Delivery.DeliveryStatus.DELIVERED, "고객 인도");

		distributionService.requestReturn(
				delivery.getDeliveryId(), "고객 반품 요청", "회수 담당자");
		assertEquals(ReturnStatus.REQUESTED, delivery.getReturnStatus());
		assertEquals(originalLotQuantity - 1, lot.getLotQuantity());

		distributionService.startReturn(delivery.getDeliveryId());
		distributionService.receiveReturn(delivery.getDeliveryId());
		distributionService.inspectReturn(
				delivery.getDeliveryId(), true, null,
				"포장 및 내용물 정상", "회수 검수자");

		assertEquals(ReturnStatus.COMPLETED, delivery.getReturnStatus());
		assertEquals(originalLotQuantity, lot.getLotQuantity());
		assertEquals(originalProductQuantity, lot.getProduct().getTotalStock());
	}

	@Test
	@Transactional
	void orderCancellationRestoresShippedLotStock() {
		var reservedBefore = inventoryService.reservedStockByLot();
		var lot = inventoryService.lots().stream()
				.filter(item -> item.getLotQuantity()
						- reservedBefore.getOrDefault(item.getLotId(), 0) >= 1)
				.findFirst()
				.orElseThrow();
		int originalLotQuantity = lot.getLotQuantity();
		int originalProductQuantity = lot.getProduct().getTotalStock();
		int originalReserved =
				reservedBefore.getOrDefault(lot.getLotId(), 0);

		Long orderId = distributionService.createDemoOrder(
				lot.getLotId(), 1, BigDecimal.ZERO,
				"취소 테스트", "010-1111-2222",
				"06236", "서울특별시 강남구 테헤란로 123",
				null, "취소 테스트 주소", null, null,
				"취소 테스트");
		assertEquals(
				originalReserved + 1,
				inventoryService.reservedStockByLot()
						.get(lot.getLotId()));

		shipmentService.create(orderId, "출고 담당자", "주문 취소 테스트");
		var shipment = shipmentRepository.findByOrderOrderId(orderId)
				.orElseThrow();
		shipmentService.startPicking(
				shipment.getShipmentId(), "피킹 담당자");
		shipmentService.inspect(
				shipment.getShipmentId(), "검수 담당자");
		shipmentService.complete(
				shipment.getShipmentId(), "출고 담당자");
		assertEquals(originalLotQuantity - 1, lot.getLotQuantity());
		assertEquals(
				originalProductQuantity - 1,
				lot.getProduct().getTotalStock());

		distributionService.registerDelivery(
				orderId, "테스트 운송사", "CANCEL-1000");
		var delivery = deliveryRepository.findByOrderOrderId(orderId)
				.orElseThrow();

		distributionService.cancelOrder(
				orderId, "고객 주문 취소", "취소 담당자");

		var order = orderRepository.findById(orderId).orElseThrow();
		assertEquals(CustomerOrder.OrderStatus.CANCELLED, order.getStatus());
		assertEquals("고객 주문 취소", order.getCancellationReason());
		assertEquals("취소 담당자", order.getCancellationManager());
		assertEquals(
				Shipment.ShipmentStatus.CANCELLED,
				shipment.getStatus());
		assertEquals(
				Delivery.DeliveryStatus.CANCELLED,
				delivery.getStatus());
		assertEquals(originalLotQuantity, lot.getLotQuantity());
		assertEquals(
				originalProductQuantity,
				lot.getProduct().getTotalStock());
		assertEquals(
				originalReserved,
				inventoryService.reservedStockByLot()
						.getOrDefault(lot.getLotId(), 0));
	}

	@Test
	@Transactional
	void orderCancellationPostAcceptsRenderedCsrfToken() throws Exception {
		var reservedBefore = inventoryService.reservedStockByLot();
		var lot = inventoryService.lots().stream()
				.filter(item -> item.getLotQuantity()
						- reservedBefore.getOrDefault(item.getLotId(), 0) >= 1)
				.findFirst()
				.orElseThrow();
		Long orderId = distributionService.createDemoOrder(
				lot.getLotId(), 1, BigDecimal.ZERO,
				"CSRF 테스트", "010-3333-4444",
				"06236", "서울특별시 강남구 테헤란로 123",
				null, "테스트 주소", null, null, null);

		MvcResult page = mockMvc.perform(get("/distribution"))
				.andExpect(status().isOk())
				.andReturn();
		CsrfToken csrfToken = (CsrfToken) page.getRequest()
				.getAttribute(CsrfToken.class.getName());
		MockHttpSession session =
				(MockHttpSession) page.getRequest().getSession(false);

		mockMvc.perform(post(
				"/distribution/orders/{orderId}/cancel", orderId)
				.session(session)
				.param(csrfToken.getParameterName(), csrfToken.getToken())
				.param("manager", "보안 테스트 담당자")
				.param("reason", "CSRF 정상 처리 테스트"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl(
						"/distribution?view=cancelled_orders"));

		assertEquals(
				CustomerOrder.OrderStatus.CANCELLED,
				orderRepository.findById(orderId).orElseThrow().getStatus());
	}

	@Test
	void lotDetailRendersTraceabilityAndBarcodeLabel() throws Exception {
		var lot = inventoryService.lots().stream()
				.filter(item -> item.getLotQuantity() > 0)
				.findFirst()
				.orElseThrow();

		mockMvc.perform(get("/inventory/lots/{lotId}", lot.getLotId()))
				.andExpect(status().isOk())
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString("LOT TRACEABILITY")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString("재고 실사")))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString("data:image/svg+xml;base64,")));
	}

	@Test
	@Transactional
	void lotLocationAndInventoryAuditAreRecorded() {
		var lot = inventoryService.lots().stream()
				.filter(item -> item.getLotQuantity() > 0)
				.findFirst()
				.orElseThrow();
		int adjustedQuantity = lot.getLotQuantity() + 1;

		inventoryService.updateWarehouseLocation(lot.getLotId(), "T창고-01");
		inventoryService.auditLot(
				lot.getLotId(), adjustedQuantity, "자동 테스트 실사");

		assertEquals("T창고-01", lot.getWarehouseLocation());
		assertEquals(adjustedQuantity, lot.getLotQuantity());
		assertEquals(
				"재고 실사",
				inventoryService.lotLogs(lot.getLotId()).get(0)
						.getChangeType().getLabel());
	}

	@Test
	@Transactional
	void fifoReleaseWorksWithReservationCalculation() {
		Product product = inventoryService.products().stream()
				.filter(item -> item.getTotalStock() > 0)
				.findFirst()
				.orElseThrow();
		int originalStock = product.getTotalStock();

		inventoryService.releaseFifo(product.getProductId(), 1, "출고 동작 테스트");
		var outbound = inventoryService.outboundLogs().stream()
				.filter(log -> "출고 동작 테스트".equals(log.getReason()))
				.findFirst()
				.orElseThrow();
		inventoryService.cancelDirectOutbound(outbound.getLogId(), "테스트 출고 취소");

		assertEquals(originalStock, product.getTotalStock());
	}
}
