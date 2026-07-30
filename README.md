# finalProject - 배합사료 재고·유통 관리

Spring Boot 3.5.16, Java 21, Gradle, JPA, H2, Thymeleaf 기반 예제입니다.

## STS에서 실행

1. `File > Import > Existing Gradle Project`를 선택합니다.
2. 이 `finalProject` 폴더를 지정합니다.
3. Gradle Refresh가 끝나면 `FinalProjectApplication`을 Spring Boot App으로 실행합니다.
4. 브라우저에서 `http://localhost:8080`에 접속합니다.

Java 21과 Spring Boot Nature, Gradle Project Nature는 프로젝트 메타데이터에 설정되어 있습니다.
Buildship이 Gradle 8.14.3을 관리하도록 지정되어 있으므로 최초 동기화 시 인터넷 연결이 필요합니다.

## VS Code에서 프론트 수정

- 화면: `src/main/resources/templates`
- 스타일: `src/main/resources/static/css/app.css`
- 동작: `src/main/resources/static/js/app.js`

DevTools가 포함되어 있어 파일 저장 후 새로고침으로 변경 내용을 확인할 수 있습니다.

## 주요 URL

- `/inventory`: 상품/LOT 재고, 입고, FIFO 출고, 재고 조정, 이력
- `/distribution`: 주문, 운송장 등록, 배송 상태 추적
- `/api/inventory/summary`: 재고 요약 JSON API
- `/h2-console`: H2 데이터베이스 콘솔

H2 접속 URL은 `jdbc:h2:file:./data/finalproject`, 사용자명은 `sa`, 비밀번호는 비어 있습니다.
