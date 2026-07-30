# 아키텍처 노트 — 다중 창고(Center) 확장

WMS 1.0 은 **단일 물류센터 안에 창고 건물 2동(`WH1` · `WH2`)** 이 있는 구조를 전제로 만들었다.
에픽 "전국 다중 창고 기반 물류망 확장" 에서 손대야 할 지점을 정리한 문서다.

## 에픽 진행 상태

| Phase | 내용 | 상태 |
|---|---|---|
| **P1 기초 공사** | `Warehouse` enum → `Center` 엔티티, 구역(Bin) 매핑 전환 | ✅ 완료 |
| **P2 검색 및 조회** | 재고 · 이력 조회에 센터 필터 축 추가 | ✅ 완료 |
| P3 센터 간 이동 | `TRANSFER_OUT` / `TRANSFER_IN`, 운송 중(in-transit) 상태 | ⏳ 예정 |
| P4 대시보드 · 라우팅 | 센터별 수요 시각화, 스마트 주문 할당 | ⏳ 예정 |

## 창고를 표현하는 방식 (P1 이후)

| 요소 | 현재 | 위치 |
|---|---|---|
| 센터 구분 | `centers` **테이블** (운영 중 추가 · 중지 가능) | `domain/Center.java` |
| 구역의 소속 | `WarehouseBin.center` — `@ManyToOne(LAZY, optional = false)` | `domain/WarehouseBin.java` |
| 재고의 소속 | **없음.** `Inventory` 는 구역(`bin`)만 참조하고 센터를 직접 모른다 | `domain/Inventory.java` |

`Inventory` 가 센터를 직접 모르는 것은 정규화 관점에서 맞다. 센터는 구역의 속성이고,
재고는 구역에 속하므로 `inventory → bin → center` 로 도달할 수 있다.
다만 **센터 단위로 재고를 필터링하는 쿼리가 아직 없다** (P2에서 추가).

## P1 에서 실제로 바꾼 것

| 대상 | 변경 |
|---|---|
| `Warehouse` enum | **파일 삭제.** enum 을 남겨두면 센터 정보가 두 곳에서 관리된다 |
| `Center` 엔티티 신설 | `centerId` · `centerCode` · `name` · `region` · `address` · `note` · `active` · `createdAt` |
| `WarehouseBin.warehouse` | `@ManyToOne Center center` (`centerId` FK, `optional = false`) |
| `WarehouseBinForm.warehouse` | `centerId` — select 옵션을 enum 상수 대신 `CenterRepository` 조회로 |
| 모든 구역 조회 쿼리 | `join fetch b.center` 추가 — `locationLabel()` 이 센터명을 쓰므로 없으면 N+1 |
| `WarehouseMapRow` | 센터 컴포넌트 제거. 도면은 센터 단위로 그리므로 행마다 담을 필요가 없다 |
| `WarehouseFacilityDto` | `forWarehouse(Warehouse)` → `forCenter(Long)`. `switch` 제거 (WH1/WH2 배치가 완전히 동일했다) |
| `data.sql` | `centers` 시드 2행 추가, `warehouseBins.warehouse` → `centerId` (40행) |
| `docs/erd-*.tsv` · `erd.md` | `centers` 테이블 · 관계 반영 |

**센터의 의미는 바꾸지 않았다.** `centerCode` 는 `WH1`/`WH2`, `name` 은 `제1창고`/`제2창고` 그대로다.
구조 전환과 데이터 의미 변경을 한 커밋에 섞으면 화면 결과가 달라져 **회귀인지 의도된 변경인지 구분할 수 없다.**
전국 센터 코드 체계 도입은 P2 이후 별도로 한다.

## 남은 영향 지점

### 1. 재고 조회의 센터 축 (P2 완료)

| 위치 | 현재 시그니처 | 센터 축 |
|---|---|---|
| `InventoryRepository.search` | `(centerId, productId, binId, zone)` | ✅ |
| `InventoryService.getInventories` | `(centerId, productId, binId, zone)` | ✅ |
| `InventoryRepository.findStockByCenter` | `(productId)` → 센터별 집계 | ✅ 신규 |
| `InventoryService.getDisposalTargets` | `(productId, zone, expiredOnly)` | ⏳ 미적용 |
| `InventoryMoveService.getMovableInventories` | `(binId)` | 불필요 (구역이 센터를 결정한다) |

폐기 화면은 만료 재고를 **전국 단위로 훑는 것이 기본 동작**이라 센터 조건을 넣지 않았다.
요구가 나오면 `:centerId` 를 추가한다.

> **이 파라미터들을 조건 객체로 묶지 않았다.**
> Spring Data JPA 의 `@Query` 는 `@Param` 으로 개별 인자를 받으므로, 서비스에서 객체로 묶어도
> Repository 경계에서 다시 풀린다. 변환 코드만 늘고 이득이 없다.
> `search` 가 인자 4개가 되면서 경계에 다가섰다. **다음에 축이 하나 더 붙으면 묶는다.**

### 1-1. 정렬 우선순위는 유통기한이다 (P2 결정)

재고 현황 목록을 센터 순으로 먼저 정렬하지 않았다. 이 화면은 **FEFO 출고 순서를 눈으로
확인하는 용도**이므로, 센터를 1순위로 두면 가장 급한 재고가 화면 아래로 밀린다.
유통기한이 같을 때만 `centerCode → binCode` 로 묶어 같은 센터 행이 흩어지지 않게 했다.

### 1-2. 전국 기준 지표와 필터된 목록을 구분해야 한다 (P2 결정)

요약 카드(적재 위치 수 · 총 보관 수량)는 **전국 기준을 유지**했다. 필터에 따라 카드까지
바뀌면 "전국에 얼마나 있는지" 를 볼 방법이 사라진다. 대신 두 가지를 추가했다.

- 카드에 `전국` 뱃지 — 기준을 명시
- `InventorySearchDto` — 지금 보고 있는 목록의 합계를 함께 내려 목록 헤더에 표시

센터별 분포(`findStockByCenter`)도 **목록 필터와 무관하게** 집계한다. 목록을 자바에서
그룹핑하면 센터를 하나 고른 순간 분포도 그 센터 하나로 줄어들어, 정작 필요한
"다른 센터에도 재고가 있다" 는 정보가 사라진다.

### 2. 센터별로 달라져야 하는 전역 상수

| 대상 | 현재 | 확장 시 |
|---|---|---|
| 도면 격자 크기 | `WarehouseBinForm.GRID_COLUMNS = 26` · `GRID_ROWS = 14` (전역) | 센터마다 건물 크기가 달라 **센터별 속성**이어야 한다. Bean Validation `@Max` 가 컴파일 상수를 요구하므로 폼 검증 방식도 함께 바꿔야 한다 |
| 부대시설(출입구·벽·검수실) | `WarehouseFacilityDto.forCenter(Long)` — 모든 센터가 같은 배치 | 센터별 배치가 달라지면 `centerFacilities` 테이블로 이관. P1 에서 `switch` 를 걷어냈으니 이관 지점이 한 곳으로 좁혀졌다 |

### 3. 센터 간 재고 이동 (P3)

`InventoryMoveService` 는 **같은 센터 안에서의 구역 간 이동**만 다룬다.
센터 간 이동(재고 이관)은 성격이 다르다.

- 출발 센터에서 나가고 도착 센터에 들어오는 사이에 **운송 중(in-transit)** 상태가 존재한다
- 즉시 완료되는 현재 이동과 달리 출고 → 입고 2단계이며 그 사이 재고의 소유 위치가 애매하다
- `MovementType` 에 `TRANSFER_OUT` / `TRANSFER_IN` 유형이 필요하다

현재 `MOVE`(sign 0, 총량 불변)를 센터 간 이동에 재사용하면 안 된다.
센터 간에는 한쪽 센터의 재고가 실제로 줄어들기 때문이다.

> P1 · P2 에서 `InventoryMoveService` 의 이동 로직은 손대지 않았다. 구역 간 이동의 판정
> 단위는 **구역**이고, 구역이 어느 센터에 속하는지는 이동 로직의 관심사가 아니다.
>
> **다만 이동 화면은 여전히 다른 센터의 구역을 고를 수 있다.** 막으려면 `TRANSFER_*` 유형과
> 운송 중 상태가 함께 있어야 하므로 P3 에서 한 번에 처리한다.
> P2 에서는 그런 이력이 생겼을 때 **조용히 묻히지 않도록** 감지 장치만 넣었다 —
> `TraceEventDto.isCenterTransfer()` 가 출발·도착 센터가 다른 `MOVE` 를 판정하고,
> 타임라인과 로트 요약이 경고로 표시한다.

## 유지한 불변식

| 항목 | 확장 시 이점 |
|---|---|
| `BinCapacityChecker` | 판정 단위가 **구역(bin)** 이라 구역이 어느 센터에 속하든 코드가 바뀌지 않는다. 적재 한도는 센터가 아니라 구역의 속성이다 |
| `StockPolicy` 상수 통합 | 유통기한 임박 기준이 한곳에 모여 있다. 센터별 정책이 필요해지면 이 클래스만 설정 주입으로 바꾸면 된다 |
| 재고 3계층 | `Product.totalStock` = Σ`ProductLot.lotQuantity` = Σ`Inventory.quantity`. 센터가 늘어도 성립한다 |
| `zoneLabel()` / `locationLabel()` 분리 | 센터를 별도 컬럼으로 보여주는 화면과 그렇지 않은 화면이 같은 라벨을 쓰면 센터명이 한 행에 두 번 나온다. 표기 책임을 화면이 고르게 했다 |
| 집계 record → 화면 DTO 변환 규약 | `StockSyncRow → StockSyncResultDto`, `DailySalesRow → SalesChartDto`, `CenterStockRow → CenterStockDto`. 비중(%) 처럼 **다른 행과의 관계에서 나오는 값**은 행 하나만 아는 projection 이 계산할 수 없다 |

## `Product.totalStock` 은 전국 합계로 유지한다 (확정)

센터별로 쪼개지 않는다. **B2C 쇼핑몰이 이 컬럼을 "판매 가능 수량" 으로 읽고 있어
의미를 바꾸면 연동이 깨진다.** 재고 정합성 점검(`/admin/inventory/sync`)도 전국 합계 기준을 유지한다.

센터별 가용 재고가 필요하면 `inventories` 를 `bin → center` 로 조인해 집계 쿼리를 따로 만든다.
비정규화 캐시를 하나 더 두는 것(`centerStock`)은 갱신 지점이 늘어 정합성 위험만 커진다.
