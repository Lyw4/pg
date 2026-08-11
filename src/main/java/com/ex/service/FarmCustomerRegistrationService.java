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
        String address = joinAddress(
                request.farmAddress().baseAddress(),
                request.farmAddress().detailAddress());
        int deliveryDay = request.regularDeliveryDay() == null
                ? 15
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
                valueOrDefault(
                        profile == null ? null : profile.animalType(),
                        "미등록"),
                nonNegative(profile == null
                        ? null
                        : profile.livestockCount()),
                nonNegative(profile == null
                        ? null
                        : profile.monthlyFeedQuantity()),
                valueOrDefault(
                        profile == null ? null : profile.preferredFeed(),
                        "상담 후 지정"),
                deliveryDay,
                assignment.warehouse(),
                assignment.distanceKm(),
                "회원가입 자동 등록 · " + locatedFarm.source());
        return farmCustomerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public Optional<FarmCustomer> findByMemberId(Long memberId) {
        return farmCustomerRepository.findByMemberId(memberId);
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
