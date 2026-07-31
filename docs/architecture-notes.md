# 아키텍처 노트 — 다중 창고(Center) 확장

WMS 1.0 은 **단일 물류센터 안에 창고 건물 2동(`WH1` · `WH2`)** 이 있는 구조를 전제로 만들었다.
에픽 "전국 다중 창고 기반 물류망 확장" 에서 손대야 할 지점을 정리한 문서다.

## 에픽 진행 상태

| Phase | 내용 | 상태 |
|---|---|---|
| **P1 기초 공사** | `Warehouse` enum → `Center` 엔티티, 구역(Bin) 매핑 전환 | ✅ 완료 |
| **P2 검색 및 조회** | 재고 · 이력 조회에 센터 필터 축 추가 | ✅ 완료 |
| **P3a 센터 간 이동** | `TRANSFER_OUT` / `TRANSFER_IN`, `IN_TRANSIT` 가상 구역 | ✅ 완료 |
| P3b 운송 중 상태 관리 | 도착 처리 분리 · 이관 전표 | ⏸ 보류 (포트폴리오 범위 초과) |
| **P4a 대시보드** | 센터별 재고 분포 · 실적 시각화 | ✅ 완료 |
| P4b 스마트 주문 할당 | 배송지 → 센터 라우팅 | ⏸ 보류 (판단 근거 데이터 부재) |

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

### 3. 센터 간 재고 이관 (P3a 완료)

`MOVE` 로 센터를 넘을 수 있었던 문제를 해결했다. 이제 센터가 다르면
`TRANSFER_OUT` + `TRANSFER_IN` 두 건으로 기록되고, 그 사이 재고는
**출발 센터 소속 `IN_TRANSIT` 가상 구역**을 경유한다.

| 구분 | 이력 | sign |
|---|---|---|
| 같은 센터 | `MOVE` 1건 | 0 |
| 다른 센터 | `TRANSFER_OUT` + `TRANSFER_IN` 2건 | -1 / +1 (합 0) |

**운송 중 재고를 `Inventory` 밖에 두지 않은 이유** — 3계층 불변식이 정합성 점검의
근거인데, 밖에 두면 운송 중에 불변식이 깨져 점검 로직에 예외를 넣어야 한다.
그 예외가 진짜 어긋남을 가려버린다. 안전 장치를 위해 안전 장치를 무디게 하는
거래는 하지 않았다. 상세는 [epic-p3-design.md](epic-p3-design.md).

**가상 구역이 새어 나가지 않도록 막은 곳** — 2D 도면 / 구역 선택 목록 /
등록·수정 폼의 용도 select / 적재 한도 검증 / 적재율 통계.
대신 **정합성 점검 화면에서는 잔류 재고를 보여준다.** 감추기만 하면
이관이 중간에 깨져도 알아챌 방법이 없다.

**P3b 확장 지점** — 두 구간이 이미 분리되어 있으므로, 실제 운송 중 상태가
필요해지면 `recordTransfer` 의 두 번째 구간을 즉시 호출하지 않으면 된다.
구조를 다시 짜지 않는다.

> P1 · P2 에서 `InventoryMoveService` 의 이동 로직은 손대지 않았다. 구역 간 이동의 판정
> 단위는 **구역**이고, 구역이 어느 센터에 속하는지는 이동 로직의 관심사가 아니다.
>
> **다만 이동 화면은 여전히 다른 센터의 구역을 고를 수 있다.** 막으려면 `TRANSFER_*` 유형과
> 운송 중 상태가 함께 있어야 하므로 P3 에서 한 번에 처리한다.
> P2 에서는 그런 이력이 생겼을 때 **조용히 묻히지 않도록** 감지 장치만 넣었다 —
> `TraceEventDto.isCenterTransfer()` 가 출발·도착 센터가 다른 `MOVE` 를 판정하고,
> 타임라인과 로트 요약이 경고로 표시한다.

### 4. 전국 대시보드 (P4a 완료)

센터별 재고 분포 · 적재율 · 유통기한 경보 · 기간 실적을 대시보드에 올렸다.
`CenterDashboardService` 를 `DashboardService` 와 <b>분리</b>했다 — 전자는 센터별로
쪼개서 비교하고, 후자는 전국 합계를 요약한다. 두 관점을 한 서비스에 섞으면
메서드마다 "이건 전국인가 센터별인가" 를 확인해야 한다.

**조립 쿼리 6회로 고정** — 센터 목록 · 재고 · 수용량 · 경보 · 실적 · 축종 구성을
각각 한 번씩 집계해 센터 단위로 짝짓는다. 센터마다 반복 조회하면 센터 수만큼 늘어난다.

**센터 목록을 기준으로 조립한다.** 집계 쿼리는 `group by` 결과라 재고가 있는 센터만
돌려준다. 집계 결과를 기준으로 조립하면 <b>재고 0 인 센터가 화면에서 사라져</b>
신설 센터에 배분이 누락된 사실을 아무도 알아채지 못한다.

**이관 합계는 출고 기준으로만 센다.** 출고와 입고는 짝이므로 더하면 같은 물량이
두 번 잡힌다. 60포대 이관이 120으로 보인다.

**적재율 경계는 `BinLoadStatus` 상수를 재사용한다.** 여기서 60 / 90 을 다시 적으면
한쪽만 고쳐졌을 때 같은 센터가 2D 도면에서는 '보통', 대시보드에서는 '포화' 로 보인다.

### 4-1. 스마트 주문 할당을 만들지 않은 이유 (P4b 보류)

배송지 기반 라우팅에는 세 가지가 필요한데 <b>판단 근거 데이터가 없다.</b>

| 필요 | 현재 |
|---|---|
| 센터 좌표 | `Center` 에 `region`·`address` 문자열만 있다 |
| 주문 배송지 좌표 | `Order.shippingAddress` 문자열뿐. 지오코딩 API 가 없으면 주소 파싱이 된다 |
| 권역 → 센터 우선순위 | 없다 |

주소 문자열 파싱은 규칙이 지저분하고(`경북 상주시` / `경상북도 상주시` / `상주시`)
예외가 끝없이 나온다. 그러면 <b>보여주려는 것(전국 물류망 설계)이 아니라
주소 처리 코드에 시간을 쓰게 된다.</b> 만들려면 `Center` 에 좌표 컬럼을 추가하고
권역 매핑 테이블을 두는 것이 먼저다.

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
