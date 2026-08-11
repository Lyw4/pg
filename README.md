# finalProject - 배합사료 재고·유통 관리

Spring Boot 3.5.16, Java 21, Gradle, JPA, H2, Thymeleaf 기반 예제입니다.

## STS에서 실행

1. `File > Import > Existing Gradle Project`를 선택합니다.
2. 이 `finalProject` 폴더를 지정합니다.
3. Gradle Refresh가 끝나면 `FinalProjectApplication`을 Spring Boot App으로 실행합니다.
4. 브라우저에서 `http://localhost:8080`에 접속합니다.

관리자 기능을 사용하기 전에는 PowerShell에서 관리자 계정을 환경 변수로
설정합니다.

```powershell
$env:FEEDFLOW_ADMIN_USERNAME="admin"
$env:FEEDFLOW_ADMIN_PASSWORD="원하는-안전한-비밀번호"
.\gradlew.bat bootRun
```

Java 21과 Spring Boot Nature, Gradle Project Nature는 프로젝트 메타데이터에 설정되어 있습니다.
Buildship이 Gradle 8.14.3을 관리하도록 지정되어 있으므로 최초 동기화 시 인터넷 연결이 필요합니다.

## PortOne 결제 설정

고객 회원의 카드·카카오페이·가상계좌 결제를 사용하려면 STS의
`Run Configurations > Environment`에 아래 환경변수를 등록한 뒤 서버를
재시작합니다. 값은 PortOne 콘솔에서 확인합니다.

```text
PORTONE_CUSTOMER_CODE
PORTONE_API_KEY
PORTONE_API_SECRET
PORTONE_CARD_CHANNEL_KEY
PORTONE_KAKAO_CHANNEL_KEY
PORTONE_VBANK_CHANNEL_KEY
```

`PORTONE_API_SECRET`은 소스코드나 GitHub에 저장하지 않습니다. 가상계좌 채널키는
카드 채널키와 별도의 가상계좌 결제 채널키여야 합니다.

## 회원 비밀번호 찾기·영수증 테스트

- 비밀번호 찾기는 아이디·이메일·휴대전화가 일치해야 인증번호를 발급합니다.
- 인증번호는 6자리이며 5분 후 만료되고, 30초마다 재발급할 수 있습니다.
- 5회 틀리면 인증번호가 폐기됩니다.
- 외부 문자/메일 발송을 연결하기 전 로컬 시연에서는 PowerShell에
  `$env:FEEDFLOW_PASSWORD_RESET_EXPOSE_CODE="true"`를 설정하면 발급 응답과
  화면에 테스트용 인증번호가 표시됩니다. 운영 환경에서는 반드시 `false`로 둡니다.
- 로그인 회원은 `/payments/test-receipt`에서 최근 PortOne 결제의 영수증 URL과
  결제 상태를 확인할 수 있습니다.

결제 공급자 거래번호는 `customer_order.provider_transaction_id` 고유 인덱스로
한 주문에서만 사용할 수 있습니다. 결제 콜백·복구·취소 요청은 주문 행을
비관적 잠금으로 처리하여 중복 반영을 방지합니다.

## VS Code에서 프론트 수정

- 화면: `src/main/resources/templates`
- 스타일: `src/main/resources/static/css/app.css`
- 동작: `src/main/resources/static/js/app.js`

DevTools가 포함되어 있어 파일 저장 후 새로고침으로 변경 내용을 확인할 수 있습니다.

## 주요 URL

- `/`: 고객용 배합사료 판매 홈페이지
- `/admin/login`: 관리자 로그인
- `/admin/dashboard`: WMS 기반 통합 관리자 대시보드
- `/admin/products`: 판매 상품과 LOT 등록·수정
- `/inventory`: 상품/LOT 재고, 입고, FIFO 출고, 재고 조정, 이력
- `/distribution`: 고객 주문, 창고 배정, 출고, 배송, 회수 관리
- `/api/inventory/summary`: 관리자용 재고 요약 JSON API
- `/h2-console`: 관리자용 H2 데이터베이스 콘솔

`/admin`, `/inventory`, `/distribution`과 관리 API는 모두 관리자 로그인
세션을 공유합니다. 고객이 판매 홈페이지에서 주문하면 LOT 재고가 FEFO
기준으로 배정되고, 배송지에서 가까우면서 재고가 충분한 5개 거점 창고가
자동 선택됩니다. 같은 주문이 유통 관리와 재고 관리에 즉시 표시됩니다.

H2 접속 URL은 `jdbc:h2:file:./data/finalproject`, 사용자명은 `sa`, 비밀번호는 비어 있습니다.
