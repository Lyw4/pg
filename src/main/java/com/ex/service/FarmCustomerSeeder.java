package com.ex.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.FarmCustomer;
import com.ex.entity.FarmCustomer.CustomerStatus;
import com.ex.entity.Warehouse;
import com.ex.repository.FarmCustomerRepository;
import com.ex.repository.WarehouseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FarmCustomerSeeder {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private record FarmSeed(
            String code,
            String warehouseCode,
            String name,
            String representative,
            String phone,
            String postalCode,
            String address,
            double latitude,
            double longitude,
            String animalType,
            int livestockCount,
            int monthlyFeedQuantity,
            String preferredFeed,
            int deliveryDay,
            CustomerStatus status,
            String notes) {
    }

    /*
     * 실제 업체 정보가 아닌 발표·기능 검증용 가상 농장 데이터입니다.
     * 주소는 거점 창고 주변 시·군·읍면 권역까지만 사용합니다.
     */
    private static final List<FarmSeed> FARM_SEEDS = List.of(
            seed("F-W01-01", "W01", "예산 고덕 한우농장", "김한우",
                    "010-0000-1001", "32400", "충남 예산군 고덕면 농장권역",
                    36.7420, 126.7040, "소", 180, 720,
                    "한우 성장 플러스", 1, CustomerStatus.ACTIVE,
                    "송아지·육성우 혼합 사육"),
            seed("F-W01-02", "W01", "당진 합덕 양돈농장", "이양돈",
                    "010-0000-1002", "31800", "충남 당진시 합덕읍 농장권역",
                    36.7900, 126.7600, "돼지", 2400, 1850,
                    "육성돈 그로우", 15, CustomerStatus.ACTIVE,
                    "육성돈 중심 월 2회 공급"),
            seed("F-W01-03", "W01", "홍성 광천 산란계농장", "박산란",
                    "010-0000-1003", "32290", "충남 홍성군 광천읍 농장권역",
                    36.5000, 126.6200, "조류(닭/오리)", 60000, 2380,
                    "산란계 산란 피크", 1, CustomerStatus.ACTIVE,
                    "산란 피크 사료 우선"),
            seed("F-W01-04", "W01", "아산 둔포 육계농장", "최육계",
                    "010-0000-1004", "31400", "충남 아산시 둔포면 농장권역",
                    36.9300, 127.0400, "조류(닭/오리)", 42000, 2100,
                    "육계 후기 사료", 15, CustomerStatus.ACTIVE,
                    "출하 주기별 분할 배송"),
            seed("F-W01-05", "W01", "서산 운산 한우농장", "홍운산",
                    "010-0000-1005", "31950", "충남 서산시 운산면 농장권역",
                    36.8100, 126.5900, "소", 220, 900,
                    "한우 비육 전기", 5, CustomerStatus.ACTIVE,
                    "육성우·비육우 단계별 공급"),
            seed("F-W01-06", "W01", "보령 천북 양돈농장", "차천북",
                    "010-0000-1006", "33400", "충남 보령시 천북면 농장권역",
                    36.4700, 126.5200, "돼지", 2800, 2050,
                    "모돈 포유기 파워", 10, CustomerStatus.ACTIVE,
                    "모돈·자돈 사료 혼합 납품"),
            seed("F-W01-07", "W01", "태안 원북 육계농장", "노원북",
                    "010-0000-1007", "32110", "충남 태안군 원북면 농장권역",
                    36.8200, 126.2600, "조류(닭/오리)", 50000, 2400,
                    "육계 전기 사료", 20, CustomerStatus.ACTIVE,
                    "육계 입추 일정 연동 공급"),
            seed("F-W01-08", "W01", "청양 정산 산란계농장", "백정산",
                    "010-0000-1008", "33350", "충남 청양군 정산면 농장권역",
                    36.4100, 126.9400, "조류(닭/오리)", 47000, 2150,
                    "산란계 육성 사료", 26, CustomerStatus.ACTIVE,
                    "산란 주령별 사료 전환 관리"),

            seed("F-W02-01", "W02", "김제 백산 육계농장", "정백산",
                    "010-0000-2001", "54320", "전북 김제시 백산면 농장권역",
                    35.8400, 126.8900, "조류(닭/오리)", 72000, 3100,
                    "육계 전기 사료", 3, CustomerStatus.ACTIVE,
                    "육계 전기·후기 혼합 공급"),
            seed("F-W02-02", "W02", "익산 왕궁 양돈농장", "강왕궁",
                    "010-0000-2002", "54570", "전북 익산시 왕궁면 농장권역",
                    35.9700, 127.0800, "돼지", 3100, 2200,
                    "비육돈 피니셔", 17, CustomerStatus.ACTIVE,
                    "비육돈 대량 수요 고객"),
            seed("F-W02-03", "W02", "정읍 태인 한우농장", "윤태인",
                    "010-0000-2003", "56110", "전북 정읍시 태인면 농장권역",
                    35.6500, 126.9300, "소", 230, 850,
                    "한우 비육 후기", 3, CustomerStatus.ACTIVE,
                    "비육 후기 사료 비중 높음"),
            seed("F-W02-04", "W02", "부안 계화 오리농장", "한계화",
                    "010-0000-2004", "56300", "전북 부안군 계화면 농장권역",
                    35.7600, 126.7000, "조류(닭/오리)", 28000, 1980,
                    "육용오리 그로워", 17, CustomerStatus.ACTIVE,
                    "오리 그로워 정기 공급"),
            seed("F-W02-05", "W02", "군산 대야 양돈농장", "문대야",
                    "010-0000-2005", "54060", "전북 군산시 대야면 농장권역",
                    35.9500, 126.8100, "돼지", 2600, 1900,
                    "육성돈 그로우", 7, CustomerStatus.ACTIVE,
                    "육성돈 월별 체중 구간 관리"),
            seed("F-W02-06", "W02", "완주 봉동 한우농장", "전봉동",
                    "010-0000-2006", "55320", "전북 완주군 봉동읍 농장권역",
                    35.9600, 127.1600, "소", 240, 920,
                    "한우 성장 플러스", 12, CustomerStatus.ACTIVE,
                    "송아지 성장기 사료 비중 확대"),
            seed("F-W02-07", "W02", "고창 흥덕 오리농장", "장흥덕",
                    "010-0000-2007", "56410", "전북 고창군 흥덕면 농장권역",
                    35.5200, 126.7000, "조류(닭/오리)", 38000, 2650,
                    "육용오리 그로워", 21, CustomerStatus.ACTIVE,
                    "출하 회차별 대량 납품"),
            seed("F-W02-08", "W02", "남원 운봉 산란계농장", "권운봉",
                    "010-0000-2008", "55710", "전북 남원시 운봉읍 농장권역",
                    35.4400, 127.5300, "조류(닭/오리)", 44000, 2050,
                    "산란계 산란 피크", 27, CustomerStatus.ACTIVE,
                    "산란율 기준 월 수요 보정"),

            seed("F-W03-01", "W03", "의성 단촌 한우농장", "신단촌",
                    "010-0000-3001", "37320", "경북 의성군 단촌면 농장권역",
                    36.4200, 128.7000, "소", 260, 940,
                    "한우 비육 전기", 5, CustomerStatus.ACTIVE,
                    "거점 인접 우선 배송"),
            seed("F-W03-02", "W03", "안동 풍산 양돈농장", "조풍산",
                    "010-0000-3002", "36620", "경북 안동시 풍산읍 농장권역",
                    36.5800, 128.5800, "돼지", 2700, 1960,
                    "양돈 장건강 프로", 19, CustomerStatus.ACTIVE,
                    "장건강 사료 고정 거래"),
            seed("F-W03-03", "W03", "영주 안정 산란계농장", "배안정",
                    "010-0000-3003", "36050", "경북 영주시 안정면 농장권역",
                    36.8300, 128.5600, "조류(닭/오리)", 52000, 2260,
                    "산란계 육성 사료", 5, CustomerStatus.ACTIVE,
                    "육성·산란 전환 수요"),
            seed("F-W03-04", "W03", "상주 함창 육계농장", "오함창",
                    "010-0000-3004", "37110", "경북 상주시 함창읍 농장권역",
                    36.5700, 128.1800, "조류(닭/오리)", 36000, 1720,
                    "육계 후기 사료", 19, CustomerStatus.PAUSED,
                    "계약 갱신 대기 시연 데이터"),
            seed("F-W03-05", "W03", "예천 호명 한우농장", "도호명",
                    "010-0000-3005", "36850", "경북 예천군 호명읍 농장권역",
                    36.5700, 128.4700, "소", 200, 790,
                    "한우 비육 후기", 8, CustomerStatus.ACTIVE,
                    "비육 후기 집중 출하 관리"),
            seed("F-W03-06", "W03", "문경 산양 양돈농장", "하산양",
                    "010-0000-3006", "36930", "경북 문경시 산양면 농장권역",
                    36.6100, 128.2600, "돼지", 2500, 1820,
                    "양돈 저단백 밸런스", 12, CustomerStatus.ACTIVE,
                    "저단백 배합 정기 거래"),
            seed("F-W03-07", "W03", "봉화 물야 육계농장", "이물야",
                    "010-0000-3007", "36200", "경북 봉화군 물야면 농장권역",
                    36.9800, 128.7300, "조류(닭/오리)", 39000, 1800,
                    "육계 후기 사료", 16, CustomerStatus.ACTIVE,
                    "산간권 공동 배송 노선"),
            seed("F-W03-08", "W03", "구미 선산 산란계농장", "고선산",
                    "010-0000-3008", "39120", "경북 구미시 선산읍 농장권역",
                    36.2400, 128.3000, "조류(닭/오리)", 56000, 2350,
                    "산란계 산란 피크", 23, CustomerStatus.ACTIVE,
                    "산란 피크 구간 집중 납품"),
            seed("F-W03-09", "W03", "청송 진보 한우농장", "구진보",
                    "010-0000-3009", "37400", "경북 청송군 진보면 농장권역",
                    36.5300, 129.0500, "소", 180, 710,
                    "반추위 안정화 사료", 27, CustomerStatus.ACTIVE,
                    "환절기 반추위 관리 수요"),

            seed("F-W04-01", "W04", "안성 미양 낙농목장", "서미양",
                    "010-0000-4001", "17590", "경기 안성시 미양면 농장권역",
                    36.9700, 127.2100, "소", 190, 780,
                    "젖소 착유우 밸런스", 8, CustomerStatus.ACTIVE,
                    "착유우 전용 사료 정기 공급"),
            seed("F-W04-02", "W04", "이천 설성 양돈농장", "임설성",
                    "010-0000-4002", "17410", "경기 이천시 설성면 농장권역",
                    37.1300, 127.5200, "돼지", 3400, 2450,
                    "비육돈 프리미엄 골드", 22, CustomerStatus.ACTIVE,
                    "프리미엄 비육돈 사료 수요"),
            seed("F-W04-03", "W04", "평택 청북 육계농장", "문청북",
                    "010-0000-4003", "17790", "경기 평택시 청북읍 농장권역",
                    37.0200, 126.9200, "조류(닭/오리)", 68000, 2880,
                    "육계 전기 사료", 8, CustomerStatus.ACTIVE,
                    "주 단위 출하 일정 연계"),
            seed("F-W04-04", "W04", "음성 금왕 한우농장", "유금왕",
                    "010-0000-4004", "27630", "충북 음성군 금왕읍 농장권역",
                    37.0000, 127.5900, "소", 210, 820,
                    "한우 프리미엄 마블", 22, CustomerStatus.ACTIVE,
                    "비육 후기 집중 관리"),
            seed("F-W04-05", "W04", "여주 가남 한우농장", "손가남",
                    "010-0000-4005", "12660", "경기 여주시 가남읍 농장권역",
                    37.2000, 127.5500, "소", 250, 980,
                    "한우 프리미엄 마블", 4, CustomerStatus.ACTIVE,
                    "고급육 프로그램 연계 공급"),
            seed("F-W04-06", "W04", "용인 백암 양돈농장", "신백암",
                    "010-0000-4006", "17180", "경기 용인시 백암면 농장권역",
                    37.1500, 127.3800, "돼지", 3200, 2310,
                    "비육돈 프리미엄 골드", 11, CustomerStatus.ACTIVE,
                    "비육돈 출하 주기별 공급"),
            seed("F-W04-07", "W04", "화성 우정 육계농장", "조우정",
                    "010-0000-4007", "18560", "경기 화성시 우정읍 농장권역",
                    37.0900, 126.8200, "조류(닭/오리)", 62000, 2700,
                    "육계 전기 사료", 18, CustomerStatus.ACTIVE,
                    "대형 계사 주 단위 분할 공급"),
            seed("F-W04-08", "W04", "진천 이월 산란계농장", "민이월",
                    "010-0000-4008", "27810", "충북 진천군 이월면 농장권역",
                    36.9300, 127.4300, "조류(닭/오리)", 49000, 2180,
                    "산란계 육성 사료", 26, CustomerStatus.ACTIVE,
                    "육성계 전환 시기 수요 관리"),

            seed("F-W05-01", "W05", "나주 문평 오리농장", "남문평",
                    "010-0000-5001", "58200", "전남 나주시 문평면 농장권역",
                    35.0500, 126.8500, "조류(닭/오리)", 45000, 3200,
                    "육용오리 그로워", 10, CustomerStatus.ACTIVE,
                    "거점 인접 최우선 배송"),
            seed("F-W05-02", "W05", "영암 신북 한우농장", "고신북",
                    "010-0000-5002", "58400", "전남 영암군 신북면 농장권역",
                    34.8900, 126.6900, "소", 170, 690,
                    "한우 성장 플러스", 24, CustomerStatus.ACTIVE,
                    "육성우 중심 고객"),
            seed("F-W05-03", "W05", "함평 학교 양돈농장", "송학교",
                    "010-0000-5003", "57160", "전남 함평군 학교면 농장권역",
                    35.0300, 126.5400, "돼지", 2100, 1580,
                    "자돈 스타터 2호", 10, CustomerStatus.ACTIVE,
                    "자돈·육성돈 혼합 공급"),
            seed("F-W05-04", "W05", "장흥 부산 육계농장", "장부산",
                    "010-0000-5004", "59300", "전남 장흥군 부산면 농장권역",
                    34.7200, 126.9000, "조류(닭/오리)", 33000, 1510,
                    "가금 프리미엄 믹스", 24, CustomerStatus.PAUSED,
                    "계절 계약 보류 시연 데이터"),
            seed("F-W05-05", "W05", "무안 현경 오리농장", "표현경",
                    "010-0000-5005", "58510", "전남 무안군 현경면 농장권역",
                    35.0200, 126.4100, "조류(닭/오리)", 40000, 2850,
                    "육용오리 그로워", 6, CustomerStatus.ACTIVE,
                    "오리 출하 회차별 대량 공급"),
            seed("F-W05-06", "W05", "해남 마산 한우농장", "엄마산",
                    "010-0000-5006", "59010", "전남 해남군 마산면 농장권역",
                    34.5800, 126.5800, "소", 210, 830,
                    "한우 성장 플러스", 12, CustomerStatus.ACTIVE,
                    "육성우 중심 월 정기 납품"),
            seed("F-W05-07", "W05", "화순 능주 양돈농장", "류능주",
                    "010-0000-5007", "58150", "전남 화순군 능주면 농장권역",
                    35.0000, 126.9600, "돼지", 2300, 1710,
                    "자돈 스타터 2호", 16, CustomerStatus.ACTIVE,
                    "자돈·육성돈 단계별 공급"),
            seed("F-W05-08", "W05", "강진 성전 육계농장", "배성전",
                    "010-0000-5008", "59200", "전남 강진군 성전면 농장권역",
                    34.6900, 126.7100, "조류(닭/오리)", 46000, 2250,
                    "육계 후기 사료", 21, CustomerStatus.ACTIVE,
                    "육계 출하 전 후기 사료 집중"),
            seed("F-W05-09", "W05", "담양 대전 산란계농장", "석대전",
                    "010-0000-5009", "57320", "전남 담양군 대전면 농장권역",
                    35.2800, 126.8900, "조류(닭/오리)", 51000, 2290,
                    "산란계 산란 피크", 28, CustomerStatus.ACTIVE,
                    "산란율 기반 수요 보정 고객"));

    private final FarmCustomerRepository farmCustomerRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional
    public void seed() {
        FARM_SEEDS.forEach(seed -> {
            Warehouse warehouse = warehouseRepository
                    .findByCode(seed.warehouseCode())
                    .orElseThrow(() -> new IllegalStateException(
                            "농장 담당 창고를 찾을 수 없습니다: "
                                    + seed.warehouseCode()));
            double distanceKm = distanceKm(
                    seed.latitude(),
                    seed.longitude(),
                    warehouse.getLatitude(),
                    warehouse.getLongitude());
            FarmCustomer customer = farmCustomerRepository
                    .findByFarmCode(seed.code())
                    .orElseGet(() -> new FarmCustomer(
                            seed.code(),
                            seed.name(),
                            seed.representative(),
                            seed.phone(),
                            seed.postalCode(),
                            seed.address(),
                            seed.latitude(),
                            seed.longitude(),
                            seed.animalType(),
                            seed.livestockCount(),
                            seed.monthlyFeedQuantity(),
                            seed.preferredFeed(),
                            seed.deliveryDay(),
                            warehouse,
                            distanceKm,
                            seed.status(),
                            seed.notes()));
            customer.updateDetails(
                    seed.name(),
                    seed.representative(),
                    seed.phone(),
                    seed.postalCode(),
                    seed.address(),
                    seed.latitude(),
                    seed.longitude(),
                    seed.animalType(),
                    seed.livestockCount(),
                    seed.monthlyFeedQuantity(),
                    seed.preferredFeed(),
                    seed.deliveryDay(),
                    warehouse,
                    distanceKm,
                    customer.getStatus(),
                    seed.notes());
            farmCustomerRepository.save(customer);
        });
    }

    private double distanceKm(
            double fromLatitude,
            double fromLongitude,
            double toLatitude,
            double toLongitude) {
        double latitudeDistance =
                Math.toRadians(toLatitude - fromLatitude);
        double longitudeDistance =
                Math.toRadians(toLongitude - fromLongitude);
        double value = Math.sin(latitudeDistance / 2)
                * Math.sin(latitudeDistance / 2)
                + Math.cos(Math.toRadians(fromLatitude))
                * Math.cos(Math.toRadians(toLatitude))
                * Math.sin(longitudeDistance / 2)
                * Math.sin(longitudeDistance / 2);
        double bounded = Math.min(1, Math.max(0, value));
        return EARTH_RADIUS_KM
                * 2
                * Math.atan2(
                        Math.sqrt(bounded),
                        Math.sqrt(1 - bounded));
    }

    private static FarmSeed seed(
            String code,
            String warehouseCode,
            String name,
            String representative,
            String phone,
            String postalCode,
            String address,
            double latitude,
            double longitude,
            String animalType,
            int livestockCount,
            int monthlyFeedQuantity,
            String preferredFeed,
            int deliveryDay,
            CustomerStatus status,
            String notes) {
        return new FarmSeed(
                code,
                warehouseCode,
                name,
                representative,
                phone,
                postalCode,
                address,
                latitude,
                longitude,
                animalType,
                livestockCount,
                monthlyFeedQuantity,
                preferredFeed,
                deliveryDay,
                status,
                notes);
    }
}
