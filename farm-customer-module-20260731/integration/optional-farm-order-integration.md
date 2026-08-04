# 선택 기능: 농장 정보로 테스트 주문 생성

고객 농장 표의 `이 농장으로 주문` 버튼까지 사용할 때만 적용합니다.

1. 주문 모달 `<form>` 안에 `frontend/optional-demo-order-fields.html`을 추가합니다.
2. 주문 생성 Controller에서 `farmCustomerId`를 선택값으로 받습니다.

```java
@RequestParam(name = "farmCustomerId", required = false)
Long farmCustomerId
```

3. `DistributionService.createDemoOrder(...)`에 `farmCustomerId`를 전달하고,
   농장을 조회해 거래 중인지 확인한 뒤 주문에 연결합니다.

```java
if (farmCustomerId != null) {
    var farmCustomer = farmCustomerRepository.findById(farmCustomerId)
            .orElseThrow(() -> new IllegalArgumentException(
                    "농장 고객사를 찾을 수 없습니다."));
    if (farmCustomer.getStatus()
            != FarmCustomer.CustomerStatus.ACTIVE) {
        throw new IllegalStateException(
                "거래 중인 농장 고객사만 주문할 수 있습니다.");
    }
    order.linkFarmCustomer(farmCustomer);
}
```

4. `CustomerOrder`에 `FarmCustomer` 다대일 연관과 연결 메서드가 필요합니다.

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "farm_customer_id")
private FarmCustomer farmCustomer;

public void linkFarmCustomer(FarmCustomer farmCustomer) {
    this.farmCustomer = farmCustomer;
}
```

5. `frontend/optional-farm-order.js`를 불러옵니다. 이 코드는 버튼의
   `data-farm-*` 값을 읽어 주문 모달의 담당자, 전화번호, 우편번호, 주소,
   위도·경도를 채웁니다. 팀원 주문 모달의 필드 ID가 다르면 그 ID에 맞게
   조정해야 합니다.

고객 농장 조회·검색·필터·거래 상태 변경만 전달하려면 이 파일의 내용은
적용하지 않아도 됩니다.
