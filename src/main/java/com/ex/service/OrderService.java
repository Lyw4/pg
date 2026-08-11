package com.ex.service;

import com.ex.dto.CreateOrderRequest;
import com.ex.dto.OrderResponse;
import com.ex.dto.OrderDetailResponse;
import com.ex.config.PaymentProperties;
import com.ex.entity.CustomerOrder;
import com.ex.entity.Member;
import com.ex.entity.OrderItem;
import com.ex.entity.OrderLotAllocation;
import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.entity.PaymentStatus;
import com.ex.repository.CustomerOrderRepository;
import com.ex.repository.DeliveryRepository;
import com.ex.repository.DeliveryStatusHistoryRepository;
import com.ex.repository.MemberRepository;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.ProductRepository;
import com.ex.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final BigDecimal FREE_DELIVERY_THRESHOLD =
            BigDecimal.valueOf(150_000);
    private static final BigDecimal DELIVERY_FEE =
            BigDecimal.valueOf(5_000);
    private static final BigDecimal REGULAR_DELIVERY_DISCOUNT_RATE =
            new BigDecimal("0.03");

    private final ProductRepository productRepository;
    private final ProductLotRepository productLotRepository;
    private final CustomerOrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryStatusHistoryRepository deliveryHistoryRepository;
    private final MemberRepository memberRepository;
    private final PaymentProperties paymentProperties;
    private final WarehouseFulfillmentService warehouseFulfillmentService;
	private final WmsStockCoordinator wmsStockCoordinator;
    private final ExpirySaleService expirySaleService;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        return createOrder(request, null);
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, Long memberId) {
        List<ResolvedLine> lines = resolveLines(request);
        BigDecimal productAmount = lines.stream()
                .map(ResolvedLine::lineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deliveryFee = productAmount.compareTo(
                FREE_DELIVERY_THRESHOLD) >= 0
                ? BigDecimal.ZERO
                : DELIVERY_FEE;
        BigDecimal discountAmount = request.regularDelivery()
                ? productAmount.multiply(
                        REGULAR_DELIVERY_DISCOUNT_RATE)
                        .setScale(0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        CustomerOrder order = CustomerOrder.storefront(
                createOrderNumber(),
                request.customerName(),
                request.phone(),
                request.address(),
                request.detailAddress(),
                request.unloadingLocation(),
                request.deliveryRequest(),
                request.paymentMethod(),
                request.regularDelivery(),
                productAmount,
                deliveryFee,
                discountAmount);

        if (memberId != null) {
            Member member = memberRepository.findById(memberId)
                    .filter(Member::isActive)
                    .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
            order.assignMember(member);
            if (paymentProperties.isPortOneEnabled()) {
                order.prepareExternalPayment();
            }
        }

        lines.forEach(line -> order.addItem(
                commitInventory(order, line)));
        warehouseFulfillmentService.assignNearest(
                order, order.getItems());
        order.markInventoryCommitted();

        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse cancelOrder(
            String orderNumber,
            String phone) {
        CustomerOrder order = findByOrderNumber(orderNumber);
        if (StringUtils.hasText(order.getProviderTransactionId())
                && (order.getPaymentStatus() == PaymentStatus.DONE
                || order.getPaymentStatus() == PaymentStatus.WAITING_FOR_DEPOSIT)) {
            throw new IllegalStateException(
                    "전자결제가 시작된 회원 주문은 로그인 후 마이페이지에서 취소해 주세요.");
        }
        order.cancelByCustomer(phone);
        restoreCommittedInventory(order);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse findOrder(
            String orderNumber,
            String phone) {
        CustomerOrder order = findByOrderNumber(orderNumber);
        if (order.getPhone() == null
                || !order.getPhone().equals(phone)) {
            throw new IllegalArgumentException(
                    "주문자의 전화번호가 일치하지 않습니다.");
        }
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findMemberOrders(Long memberId) {
        if (memberId == null) throw new IllegalArgumentException("로그인이 필요합니다.");
        return orderRepository
                .findByMember_IdAndCreatedAtAfterOrderByCreatedAtDesc(
                        memberId, LocalDateTime.now().minusMonths(6))
                .stream()
                .map(OrderResponse::from)
                .toList();
    }

    /**
     * 회원 본인의 주문만 상세 조회합니다. 주문번호를 알고 있어도 다른 회원의
     * 주문·주소·배송 정보를 조회할 수 없도록 소유권을 함께 검증합니다.
     */
    @Transactional(readOnly = true)
    public OrderDetailResponse findMemberOrderDetail(
            String orderNumber,
            Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        CustomerOrder order = findByOrderNumber(orderNumber);
        if (order.getMember() == null
                || !memberId.equals(order.getMember().getId())) {
            throw new IllegalArgumentException("본인 주문만 조회할 수 있습니다.");
        }
        var shipment = shipmentRepository.findByOrderOrderId(order.getOrderId())
                .orElse(null);
        var delivery = deliveryRepository.findByOrderOrderId(order.getOrderId())
                .orElse(null);
        var histories = delivery == null
                ? List.<com.ex.entity.DeliveryStatusHistory>of()
                : deliveryHistoryRepository
                        .findByDeliveryDeliveryIdOrderByChangedAtDesc(
                                delivery.getDeliveryId());
        return OrderDetailResponse.from(order, shipment, delivery, histories);
    }

    @Transactional
    public OrderResponse cancelMemberOrder(String orderNumber, Long memberId) {
        if (memberId == null) throw new IllegalArgumentException("로그인이 필요합니다.");
        CustomerOrder order = findByOrderNumber(orderNumber);
        if (order.getMember() == null || !memberId.equals(order.getMember().getId())) {
            throw new IllegalArgumentException("본인 주문만 취소할 수 있습니다.");
        }
        order.cancel("회원 마이페이지 요청", order.getCustomerName());
        if (order.getPaymentStatus() != null) order.cancelPayment();
        restoreCommittedInventory(order);
        return toResponse(order);
    }

    private List<ResolvedLine> resolveLines(
            CreateOrderRequest request) {
        List<ResolvedLine> lines = new ArrayList<>();
        for (CreateOrderRequest.OrderLineRequest line :
                request.items()) {
            Product product = productRepository.findById(
                            line.productId())
                    .filter(Product::isActive)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "주문할 수 없는 상품입니다: "
                                            + line.productId()));
            var saleOffer = expirySaleService.offerFor(product);
            if (saleOffer.isPresent()
                    && line.quantity() > saleOffer.get().saleStock()) {
                throw new IllegalArgumentException(
                        product.getName() + " 유통기한 특가 재고는 "
                                + saleOffer.get().saleStock()
                                + "포까지 구매할 수 있습니다.");
            }
            BigDecimal unitPrice = saleOffer
                    .map(offer -> BigDecimal.valueOf(offer.salePrice()))
                    .orElse(product.getPrice());
            lines.add(new ResolvedLine(
                    product,
                    line.quantity(),
                    unitPrice,
                    unitPrice.multiply(
                            BigDecimal.valueOf(line.quantity())),
                    saleOffer.map(ExpirySaleService.SaleOffer::lotIds)
                            .orElse(List.of())));
        }
        return lines;
    }

    private OrderItem commitInventory(
            CustomerOrder order,
            ResolvedLine line) {
        List<ProductLot> lots = productLotRepository
                .findByProductProductIdAndLotQuantityGreaterThanOrderByExpirationDateAsc(
                        line.product().getProductId(), 0)
                .stream()
                .filter(lot -> !lot.getExpirationDate().isBefore(
                        LocalDate.now().plusDays(
                                ExpirySaleService.MINIMUM_SELLABLE_DAYS)))
                .filter(lot -> line.saleLotIds().isEmpty()
                        || line.saleLotIds().contains(lot.getLotId()))
                .toList();
        int available = lots.stream()
                .mapToInt(ProductLot::getLotQuantity)
                .sum();
        if (available < line.quantity()) {
            throw new IllegalArgumentException(
                    line.product().getName()
                            + " 상품의 재고가 부족합니다.");
        }

        OrderItem item = new OrderItem(
                order,
                line.product(),
                line.quantity(),
                line.unitPrice());
        int remaining = line.quantity();
        for (ProductLot lot : lots) {
            if (remaining == 0) {
                break;
            }
            int deduction = Math.min(
                    lot.getLotQuantity(), remaining);
            lot.decrease(deduction);
            line.product().changeStock(-deduction);
			wmsStockCoordinator.outbound(
					lot,
					deduction,
					null,
					"판매 홈페이지 주문 재고 확정",
					"온라인 주문",
					null);
            item.addLotAllocation(
                    new OrderLotAllocation(lot, deduction));
            remaining -= deduction;
        }
        return item;
    }

    private void restoreCommittedInventory(CustomerOrder order) {
        if (!order.isInventoryCommitted()) {
            return;
        }
        order.getItems().stream()
                .flatMap(item ->
                        item.getLotAllocations().stream())
                .forEach(allocation -> {
                    ProductLot lot = allocation.getProductLot();
                    lot.increase(allocation.getQuantity());
                    lot.getProduct().changeStock(
                            allocation.getQuantity());
					wmsStockCoordinator.restore(
							lot,
							allocation.getQuantity(),
							order.getFulfillmentWarehouse(),
							"판매 홈페이지 주문 취소",
							"온라인 주문",
							order.getOrderId());
                });
        order.releaseInventoryCommit();
    }

    private CustomerOrder findByOrderNumber(
            String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "주문을 찾을 수 없습니다."));
    }

    private String createOrderNumber() {
        String date = LocalDate.now().format(
                DateTimeFormatter.BASIC_ISO_DATE);
        String random = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase();
        return "FF-" + date + "-" + random;
    }

    private OrderResponse toResponse(CustomerOrder order) {
        return OrderResponse.from(order);
    }

    void releasePaymentReservation(CustomerOrder order, String reason) {
        if (order.getStatus() == CustomerOrder.OrderStatus.CANCELLED) {
            return;
        }
        order.cancel(reason, "결제 시스템");
        restoreCommittedInventory(order);
    }

    private record ResolvedLine(
            Product product,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineAmount,
            List<Long> saleLotIds) {
    }
}
