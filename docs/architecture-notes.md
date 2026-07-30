# 아키텍처 노트 — 다중 창고(Center) 확장 준비

WMS 1.0 은 **단일 물류센터 안에 창고 건물 2동(`WH1` · `WH2`)** 이 있는 구조를 전제로 만들었다.
다음 에픽인 "전국 다중 창고 기반 물류망 확장" 에서 손대야 할 지점을 미리 정리한다.

## 현재 창고를 표현하는 방식

| 요소 | 현재 | 위치 |
|---|---|---|
| 창고 구분 | `Warehouse` **enum** (`WH1`, `WH2`) — DB 테이블이 아니다 | `domain/Warehouse.java` |
| 구역의 소속 | `WarehouseBin.warehouse` (enum 컬럼) | `domain/WarehouseBin.java` |
| 재고의 소속 | **없음.** `Inventory` 는 구역(`bin`)만 참조하고 창고를 직접 모른다 | `domain/Inventory.java` |

`Inventory` 가 창고를 직접 모르는 것은 정규화 관점에서 맞다. 창고는 구역의 속성이고,
재고는 구역에 속하므로 `inventory → bin → warehouse` 로 도달할 수 있다.
다만 **창고 단위로 재고를 필터링하는 쿼리가 아직 없다.**

## Center 도입 시 영향 지점

### 1. 창고를 enum → 엔티티로 전환해야 한다

전국 단위가 되면 창고는 운영 중에 늘고 줄어든다. enum 은 코드 배포 없이 추가할 수 없다.

| 대상 | 영향 |
|---|---|
| `Warehouse` enum | `Center` 엔티티로 전환 (centerId, name, address, region, active …) |
| `WarehouseBin.warehouse` | `@ManyToOne Center` 로 변경 |
| `WarehouseBinForm.warehouse` | select 옵션을 enum 상수 대신 DB 조회로 |
| `data.sql` | `centers` 테이블 시드 추가, `warehouseBins.warehouse` → `centerId` |
| `docs/erd-*.tsv` | 테이블 1개 · 관계 1건 추가 |

### 2. 재고 조회에 창고 축이 없다

현재 필터는 **품목 · 구역 · 존(zone)** 뿐이다. 전국 단위에서는 "이 센터의 재고" 가
가장 기본적인 조회 단위가 되므로 아래 시그니처에 창고 조건이 추가되어야 한다.

| 위치 | 현재 시그니처 |
|---|---|
| `InventoryRepository.search` | `(productId, binId, zone)` |
| `InventoryService.getInventories` | `(productId, binId, zone)` |
| `InventoryService.getDisposalTargets` | `(productId, zone, expiredOnly)` |
| `InventoryMoveService.getMovableInventories` | `(binId)` |

> **이번 리팩토링에서 이 파라미터들을 조건 객체로 묶지 않았다.**
> Spring Data JPA 의 `@Query` 는 `@Param` 으로 개별 인자를 받으므로, 서비스에서 객체로 묶어도
> Repository 경계에서 다시 풀린다. 변환 코드만 늘고 이득이 없다.
> 또한 필터 축이 `centerId` 가 될지 `Center` 엔티티가 될지 미확정인 상태에서 미리 추상화하면
> 잘못된 형태로 굳는다. 호출부가 각각 1곳뿐이라 그때 시그니처를 바꾸는 비용이 더 낮다.

### 3. 창고별로 달라져야 하는 전역 상수

| 대상 | 현재 | 확장 시 |
|---|---|---|
| 도면 격자 크기 | `WarehouseBinForm.GRID_COLUMNS = 26` · `GRID_ROWS = 14` (전역) | 센터마다 건물 크기가 달라 **센터별 속성**이어야 한다. Bean Validation `@Max` 가 컴파일 상수를 요구하므로 폼 검증 방식도 함께 바꿔야 한다 |
| 부대시설(출입구·벽·검수실) | `WarehouseFacilityDto.forWarehouse()` 의 `switch (WH1 / WH2)` — Java 상수 | 센터가 늘면 switch 가 계속 커진다. `centerFacilities` 테이블로 이관 |

### 4. 창고 간 재고 이동 (지금은 없는 개념)

`InventoryMoveService` 는 **같은 창고 안에서의 구역 간 이동**만 다룬다.
센터 간 이동(재고 이관)은 성격이 다르다.

- 출발 센터에서 나가고 도착 센터에 들어오는 사이에 **운송 중(in-transit)** 상태가 존재한다
- 즉시 완료되는 현재 이동과 달리 출고 → 입고 2단계이며 그 사이 재고의 소유 위치가 애매하다
- `MovementType` 에 `TRANSFER_OUT` / `TRANSFER_IN` 같은 유형이 필요할 수 있다

현재 `MOVE`(sign 0, 총량 불변)를 센터 간 이동에 재사용하면 안 된다.
센터 간에는 한쪽 센터의 재고가 실제로 줄어들기 때문이다.

## 이번 리팩토링에서 확장에 유리하게 정리한 것

| 항목 | 확장 시 이점 |
|---|---|
| `BinCapacityChecker` 추출 | 판정 단위가 **구역(bin)** 이라 구역이 어느 센터에 속하든 코드가 바뀌지 않는다. 적재 한도는 센터가 아니라 구역의 속성이다 |
| `StockPolicy` 상수 통합 | 유통기한 임박 기준이 3곳에 흩어져 있던 것을 한곳으로 모았다. 센터별 정책이 필요해지면 이 클래스 한 곳만 설정 주입으로 바꾸면 된다 |
| 재고 3계층 불변식 유지 | `Product.totalStock` = Σ`ProductLot.lotQuantity` = Σ`Inventory.quantity`. 센터가 늘어도 이 불변식은 그대로 성립한다 (`totalStock` 은 전국 합계가 된다) |

## 주의: 전국 확장 시 깨지는 불변식

`Product.totalStock` 은 현재 **전 창고 합계**다. 센터가 늘어나면 이 값만으로는
"어느 센터에 얼마나 있는지" 를 알 수 없다. 재고 정합성 점검(`/admin/inventory/sync`)도
전국 합계 기준으로만 검증하므로, 센터별 재고 현황이 필요하면 집계 쿼리를 센터 축으로
추가해야 한다. `totalStock` 자체를 센터별로 쪼개는 것은 B2C 쇼핑몰이 이 컬럼을
"판매 가능 수량" 으로 쓰고 있어 영향 범위가 크다.
