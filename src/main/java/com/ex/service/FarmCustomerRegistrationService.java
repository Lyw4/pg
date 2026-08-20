package com.ex.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.dto.SignupRequest;
import com.ex.entity.FarmCustomer;
import com.ex.entity.Member;
import com.ex.entity.Warehouse;
import com.ex.repository.FarmCustomerRepository;
import com.ex.repository.WarehouseRepository;
import com.ex.repository.ProductRepository;
import com.ex.repository.WarehouseAllocationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FarmCustomerRegistrationService {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private record Coordinate(double latitude, double longitude) {
    }

    private record LocatedFarm(Coordinate coordinate, String source) {
    }

    private record WarehouseDistance(Warehouse warehouse, double distanceKm) {
    }

    private final FarmCustomerRepository farmCustomerRepository;
    private final WarehouseRepository warehouseRepository;
    private final FarmFeedModelService farmFeedModelService;
    private final ProductRepository productRepository;
    private final WarehouseAllocationRepository allocationRepository;
    private final WarehouseCapacityPlanningService capacityPlanningService;

    @Transactional
    public FarmCustomer register(Member member, SignupRequest request) {
        Optional<FarmCustomer> existing = farmCustomerRepository
                .findByMemberId(member.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        LocatedFarm locatedFarm = locateFarm(request);
        WarehouseDistance assignment = warehouseRepository
                .findAllByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .filter(Warehouse::hasCoordinates)
                .map(warehouse -> new WarehouseDistance(
                        warehouse,
                        distanceKm(
                                locatedFarm.coordinate().latitude(),
                                locatedFarm.coordinate().longitude(),
                                warehouse.getLatitude(),
                                warehouse.getLongitude())))
                .min(Comparator
                        .comparingDouble(WarehouseDistance::distanceKm)
                        .thenComparingInt(item ->
                                item.warehouse().getDisplayOrder()))
                .orElseThrow(() -> new IllegalStateException(
                        "자동 배정할 운영 창고가 없습니다."));

        SignupRequest.FarmProfileRequest profile = request.farmProfile();
        var modeledProfile = farmFeedModelService.model(profile);
        String address = joinAddress(
                request.farmAddress().baseAddress(),
                request.farmAddress().detailAddress());
        int deliveryDay = request.regularDeliveryDay() == null
                ? 0
                : request.regularDeliveryDay();

        FarmCustomer customer = FarmCustomer.registeredMember(
                member,
                "F-M%08d".formatted(member.getId()),
                request.farmName().trim(),
                request.name().trim(),
                request.phone().trim(),
                valueOrEmpty(request.farmAddress().postalCode()),
                address,
                locatedFarm.coordinate().latitude(),
                locatedFarm.coordinate().longitude(),
                modeledProfile.animalType(),
                modeledProfile.livestockCount(),
                modeledProfile.monthlyFeedQuantity(),
                modeledProfile.preferredFeed(),
                deliveryDay,
                assignment.warehouse(),
                assignment.distanceKm(),
                "회원가입 자동 등록 · " + locatedFarm.source()
                        + " · " + modeledProfile.modelBasis()
                        + (modeledProfile.monthlyQuantityEstimated()
                                ? " · 월 사용량 모델 예측"
                                : " · 월 사용량 직접 입력"));
        FarmCustomer saved = farmCustomerRepository.save(customer);
        increaseWarehousePlanForNewFarm(saved);
        return saved;
    }

    private void increaseWarehousePlanForNewFarm(FarmCustomer farm) {
        var product = productRepository.findByName(farm.getPreferredFeed())
                .filter(com.ex.entity.Product::isActive)
                .or(() -> allocationRepository
                        .findByWarehouseWarehouseId(
                                farm.getAssignedWarehouse().getWarehouseId())
                        .stream()
                        .map(com.ex.entity.WarehouseAllocation::getProduct)
                        .filter(com.ex.entity.Product::isActive)
                        .filter(candidate -> normalizeAnimalType(
                                candidate.getAnimalType()).equals(
                                        normalizeAnimalType(farm.getAnimalType())))
                        .findFirst())
                .orElse(null);
        // 상품 마스터가 없는 최소 테스트/초기 구축 상태에서는 가입 자체를 막지 않는다.
        if (product == null) return;
        // 계획 행이 아직 없는 창고·상품 조합이면 만들어 씁니다. 예외를 던지면
        // 수요 계획 보정이라는 부가 작업 때문에 회원가입 전체가 실패합니다.
        var allocation = allocationRepository
                .findByWarehouseWarehouseIdAndProductProductId(
                        farm.getAssignedWarehouse().getWarehouseId(),
                        product.getProductId())
                .orElseGet(() -> allocationRepository.save(
                        new com.ex.entity.WarehouseAllocation(
                                farm.getAssignedWarehouse(), product, 0, 0)));
        int monthlyPlan = Math.addExact(
                allocation.getMonthlyPlannedQuantity(),
                farm.getMonthlyFeedQuantity());
        int calculatedTarget = (int) Math.min(
                Integer.MAX_VALUE,
                ((long) monthlyPlan * 22 + 29) / 30);
        allocation.changePlan(
                monthlyPlan,
                Math.max(allocation.getTargetStockQuantity(), calculatedTarget));
        increaseSupplementPlanForNewFarm(farm);
        capacityPlanningService.resizeForWarehouse(
                farm.getAssignedWarehouse().getWarehouseId());
    }

    private void increaseSupplementPlanForNewFarm(FarmCustomer farm) {
        int supplementDemand = DemandPlanService.supplementDemand(
                farm.getMonthlyFeedQuantity());
        var supplements = allocationRepository
                .findByWarehouseWarehouseId(
                        farm.getAssignedWarehouse().getWarehouseId())
                .stream()
                .filter(allocation -> allocation.getProduct().isActive())
                .filter(allocation -> "영양제".equals(
                        allocation.getProduct().getAnimalType()))
                .toList();
        if (supplements.isEmpty()) return;
        int base = supplementDemand / supplements.size();
        int remainder = supplementDemand % supplements.size();
        for (int index = 0; index < supplements.size(); index++) {
            var supplement = supplements.get(index);
            int added = base + (index < remainder ? 1 : 0);
            int monthly = Math.addExact(
                    supplement.getMonthlyPlannedQuantity(), added);
            int target = (int) Math.min(Integer.MAX_VALUE,
                    ((long) monthly * 22 + 29) / 30);
            supplement.changePlan(monthly,
                    Math.max(supplement.getTargetStockQuantity(), target));
        }
    }

    private String normalizeAnimalType(String value) {
        String animal = value == null ? "" : value.trim();
        if (animal.contains("소") || animal.contains("한우")) return "소";
        if (animal.contains("돼지") || animal.contains("양돈")) return "돼지";
        if (animal.contains("조류") || animal.contains("닭")
                || animal.contains("오리") || animal.contains("육계")
                || animal.contains("산란")) return "조류(닭/오리)";
        return animal;
    }

    @Transactional(readOnly = true)
    public Optional<FarmCustomer> findByMemberId(Long memberId) {
        return farmCustomerRepository.findByMemberId(memberId);
    }

    @Transactional
    public FarmCustomer synchronizeMember(Member member) {
        FarmCustomer customer = farmCustomerRepository.findByMemberId(member.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "농장 고객사 정보를 찾을 수 없습니다."));
        var farmAddress = member.getAddresses().stream()
                .filter(address -> address.getAddressType()
                        == com.ex.entity.AddressType.FARM)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "농장 주소를 찾을 수 없습니다."));
        String updatedAddress = joinAddress(
                farmAddress.getBaseAddress(), farmAddress.getDetailAddress());
        boolean sameAddress = normalizeAddress(updatedAddress).equals(
                normalizeAddress(customer.getAddress()));
        boolean keepPreciseLocation = sameAddress
                && validCoordinates(
                        customer.getLatitude(), customer.getLongitude())
                && customer.getAssignedWarehouse() != null
                && customer.getAssignedWarehouse().isActive();
        LocatedFarm located = keepPreciseLocation
                ? new LocatedFarm(
                        new Coordinate(customer.getLatitude(), customer.getLongitude()),
                        "기존 농장 좌표 유지")
                : new LocatedFarm(
                        regionalCoordinate(farmAddress.getBaseAddress()),
                        "회원정보 수정 주소 권역 기준");
        WarehouseDistance assignment = keepPreciseLocation
                ? new WarehouseDistance(
                        customer.getAssignedWarehouse(),
                        customer.getDistanceKm())
                : nearestWarehouse(located.coordinate());
        customer.updateDetails(
                member.getFarmName(), member.getName(), member.getPhone(),
                valueOrEmpty(farmAddress.getPostalCode()),
                updatedAddress,
                located.coordinate().latitude(), located.coordinate().longitude(),
                customer.getAnimalType(), customer.getLivestockCount(),
                customer.getMonthlyFeedQuantity(), customer.getPreferredFeed(),
                member.getRegularDeliveryDay() == null
                        ? 0 : member.getRegularDeliveryDay(),
                assignment.warehouse(), assignment.distanceKm(),
                customer.getStatus(), customer.getNotes());
        return customer;
    }

    private LocatedFarm locateFarm(SignupRequest request) {
        SignupRequest.FarmProfileRequest profile = request.farmProfile();
        if (profile != null
                && validCoordinates(profile.latitude(), profile.longitude())) {
            return new LocatedFarm(
                    new Coordinate(profile.latitude(), profile.longitude()),
                    "농장 주소 좌표 기준");
        }
        return new LocatedFarm(
                regionalCoordinate(request.farmAddress().baseAddress()),
                "농장 주소 권역 기준");
    }

    private WarehouseDistance nearestWarehouse(Coordinate coordinate) {
        return warehouseRepository.findAllByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .filter(Warehouse::hasCoordinates)
                .map(warehouse -> new WarehouseDistance(
                        warehouse,
                        distanceKm(coordinate.latitude(), coordinate.longitude(),
                                warehouse.getLatitude(), warehouse.getLongitude())))
                .min(Comparator.comparingDouble(WarehouseDistance::distanceKm)
                        .thenComparingInt(item ->
                                item.warehouse().getDisplayOrder()))
                .orElseThrow(() -> new IllegalStateException(
                        "자동 배정할 운영 창고가 없습니다."));
    }

    private Coordinate regionalCoordinate(String address) {
        String normalized = address == null
                ? ""
                : address.replace(" ", "").toLowerCase(Locale.KOREAN);
        if (containsAny(normalized, "서울")) return coordinate(37.5665, 126.9780);
        if (containsAny(normalized, "인천")) return coordinate(37.4563, 126.7052);
        if (containsAny(normalized, "경기")) return coordinate(37.4138, 127.5183);
        if (containsAny(normalized, "충남", "충청남도")) return coordinate(36.5184, 126.8000);
        if (containsAny(normalized, "대전")) return coordinate(36.3504, 127.3845);
        if (containsAny(normalized, "세종")) return coordinate(36.4800, 127.2890);
        if (containsAny(normalized, "충북", "충청북도")) return coordinate(36.6357, 127.4917);
        if (containsAny(normalized, "전북", "전라북도")) return coordinate(35.7175, 127.1530);
        if (containsAny(normalized, "광주")) return coordinate(35.1595, 126.8526);
        if (containsAny(normalized, "전남", "전라남도")) return coordinate(34.8679, 126.9910);
        if (containsAny(normalized, "경북", "경상북도")) return coordinate(36.4919, 128.8889);
        if (containsAny(normalized, "대구")) return coordinate(35.8714, 128.6014);
        if (containsAny(normalized, "경남", "경상남도")) return coordinate(35.4606, 128.2132);
        if (containsAny(normalized, "부산")) return coordinate(35.1796, 129.0756);
        if (containsAny(normalized, "울산")) return coordinate(35.5384, 129.3114);
        if (containsAny(normalized, "강원")) return coordinate(37.8228, 128.1555);
        if (containsAny(normalized, "제주")) return coordinate(33.4996, 126.5312);
        return coordinate(36.3504, 127.3845);
    }

    private boolean validCoordinates(Double latitude, Double longitude) {
        return latitude != null
                && longitude != null
                && latitude >= -90
                && latitude <= 90
                && longitude >= -180
                && longitude <= 180;
    }

    private double distanceKm(
            double fromLatitude,
            double fromLongitude,
            double toLatitude,
            double toLongitude) {
        double latitudeDistance = Math.toRadians(toLatitude - fromLatitude);
        double longitudeDistance = Math.toRadians(toLongitude - fromLongitude);
        double value = Math.sin(latitudeDistance / 2)
                * Math.sin(latitudeDistance / 2)
                + Math.cos(Math.toRadians(fromLatitude))
                * Math.cos(Math.toRadians(toLatitude))
                * Math.sin(longitudeDistance / 2)
                * Math.sin(longitudeDistance / 2);
        double bounded = Math.min(1, Math.max(0, value));
        return EARTH_RADIUS_KM
                * 2
                * Math.atan2(Math.sqrt(bounded), Math.sqrt(1 - bounded));
    }

    private boolean containsAny(String text, String... values) {
        return List.of(values).stream().anyMatch(text::contains);
    }

    private Coordinate coordinate(double latitude, double longitude) {
        return new Coordinate(latitude, longitude);
    }

    private String joinAddress(String baseAddress, String detailAddress) {
        String joined = (valueOrEmpty(baseAddress) + " "
                + valueOrEmpty(detailAddress)).trim();
        return joined.length() <= 180 ? joined : joined.substring(0, 180);
    }

    private String normalizeAddress(String value) {
        return valueOrEmpty(value).replaceAll("\\s+", "").toLowerCase(Locale.KOREAN);
    }

    private int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String valueOrDefault(String value, String defaultValue) {
        String normalized = valueOrEmpty(value);
        return normalized.isEmpty() ? defaultValue : normalized;
    }
}
