# 설계

## Q1. 테이블

`customers`에는 `customer_id`와 `name`만 있습니다. 초기 데이터는 C001 Kim, C002 Lee, C003 Park입니다.

`transfers`는 송금 한 건입니다. `transfer_id`의 기본 키는 두 행이 같은 id를 쓰지 못하게 합니다. `customer_id`의 외래 키는 없는 고객의 송금을 막습니다. `UNIQUE (customer_id, idempotency_key)`는 한 고객이 같은 키를 두 번 저장하지 못하게 하므로, 동시에 들어온 두 INSERT 중 하나만 커밋됩니다. `partner_ref`는 필수이고 유일해서, 두 송금이 같은 파트너 참조를 쓸 수 없습니다. `failure_reason`은 선택 텍스트입니다. 파트너 호출이 세 번 실패하기 전에는 null이고, 실패하면 `partner failed 3 times`를 저장합니다. 체크 제약은 송금액 10000 미만, 수수료 3000 미만, 송금액과 수수료의 합이 아닌 총 출금액, 그리고 REQUESTED, SENDING, COMPLETED, FAILED, CANCELLED 밖의 상태를 막습니다. `(customer_id, created_at)` 인덱스는 고객 한 명과 시간 범위에 대한 일일 한도 합계를 돕습니다.

`callback_events`는 파트너 콜백 한 건을 저장합니다. `event_id`의 기본 키는 같은 이벤트가 두 번 저장되지 않게 합니다. 각 행에는 `partner_ref`와 `received_at`도 있습니다. `transfers`로의 외래 키는 없습니다.

## Q2. 멱등성

재시도는 `Idempotency-Key` 헤더와 `request_hash`로 구분합니다. 해시는 `customerId|sendCurrency|파싱된 sendAmount|receiveCurrency|recipientName|partnerRef`의 SHA-256입니다. 글자 `"500000"`과 숫자 `500000`은 같은 정수가 되므로 해시가 같습니다. 해시에 들어가는 `partnerRef`는 클라이언트가 보낸 값만입니다. 생략하면 그 자리는 빈 문자열입니다. 서버가 만든 `PR-` id는 해시에 넣지 않습니다.

서비스는 본문을 검증하고 헤더를 확인한 다음 `TransferInserter`를 호출합니다. 고객 행 락 안에서 inserter는 키를 먼저 읽습니다. 해시가 같으면 저장된 송금을 201로 돌려주고 일일 한도는 다시 보지 않습니다. 해시가 다르면 409입니다. 새 키는 그 트랜잭션 안에서 INSERT합니다. `uk_transfers_customer_idempotency` 또는 `uk_transfers_partner_ref`가 INSERT를 거절하면 그 트랜잭션은 롤백됩니다. 서비스는 그 밖에서 다시 읽습니다. 해시가 같으면 저장된 송금을 201로 돌려줍니다. 해시가 다르면 409입니다. 그 키의 행이 없으면 진 유일 키는 `partner_ref`이고, 응답은 409 `partnerRef already used`입니다.

한 고객에게 같은 요청 두 개가 같이 오면 고객 락이 하나씩 처리합니다. 두 번째는 저장된 행을 보고 같은 `transferId`를 돌려줍니다. 두 고객은 같은 `partner_ref`로 여전히 겨룰 수 있습니다. 그 유일 키는 하나만 커밋되게 합니다.

## Q3. 금액

코드의 금액은 모두 `BigDecimal`입니다. `double`은 없습니다. 수수료율 `0.01`과 환율 `0.0412`는 문자열로 만듭니다.

수수료는 `sendAmount * 0.01`을 소수 0자리에서 `HALF_UP`으로 한 번 반올림한 뒤, 3000보다 작으면 3000으로 올립니다. `totalDebit`은 송금액과 수수료의 합이고 추가 반올림은 없습니다. `receiveAmount`는 `sendAmount * 0.0412`를 소수 2자리에서 `HALF_UP`으로 한 번 반올림합니다. 반올림은 `TransferCalculator`의 각 계산 끝에서만 합니다. 이후 조회나 재시도는 저장된 숫자를 `toPlainString()`으로 돌려줍니다. 다시 계산하지 않으므로 `20600.00`의 끝자리 0이 유지됩니다.

API는 금액을 JSON 문자열로 보냅니다. `sendAmount`만 JSON 문자열이나 JSON 정수로 받을 수 있습니다. `1.5` 같은 JSON 소수는 거절합니다. 데이터베이스에서 KRW 송금액, 수수료, 총 출금액은 `BIGINT`입니다. PHP 수취액은 `DECIMAL(19,2)`입니다. `partner_ref`, `failure_reason`, `callback_events`에는 금액이 없습니다.

## Q4. 일일 한도 락

사용한 메커니즘은 고객 행에 거는 비관적 락입니다. `SELECT ... FOR UPDATE`이고, JPA에서는 `LockModeType.PESSIMISTIC_WRITE`입니다. 락은 생성 트랜잭션이 커밋될 때까지 유지됩니다.

락 이후 순서는 멱등성 키를 읽고, 일일 한도를 확인하고, 그다음 INSERT입니다. 같은 키와 같은 해시는 저장된 행을 돌려주고 한도를 건너뜁니다. 다른 해시는 409입니다. 한도는 그 고객의 서울 날짜 `sendAmount`를 더하며 `CANCELLED`와 `FAILED`는 빼니다. 3,000,000을 넘으면 422입니다. 정확히 3,000,000은 허용합니다.

포기한 것은 같은 고객의 생성 병렬 처리입니다. 그래서 그 고객의 처리량은 낮아집니다. 다른 고객은 이 락에 막히지 않습니다.

락을 빼면 서로 다른 키 두 개가 둘 다 예전 합계를 읽고, 둘 다 통과하고, 둘 다 INSERT할 수 있습니다. 고객은 3,000,000을 넘을 수 있습니다. 유일 키는 같은 키가 두 번 INSERT되는 것만 막습니다. 합계는 지키지 못합니다.

## Q5. 콜백 경쟁

P6는 현재 코드로 경쟁 세 가지를 돌렸습니다. 실패한 시나리오는 없습니다.

A에서 가짜 파트너는 `ACCEPTED`를 반환하기 직전에 콜백을 호출합니다. send는 이미 `SENDING`을 커밋했고, `ACCEPTED` 뒤에 상태를 다시 쓰지 않습니다. 콜백이 `COMPLETED`로 바꿉니다. 최종 상태도 `COMPLETED`입니다.

B에서 가짜 파트너는 `FAILED`를 세 번 반환하고, 마지막 시도 전에 콜백을 호출합니다. 콜백이 `COMPLETED`로 바꿉니다. 실패 저장은 `COMPLETED`가 `FAILED`로 갈 수 없음을 보고, `partner failure not saved`를 로그한 뒤 UPDATE 전에 반환합니다. 상태는 `COMPLETED`로 남습니다. 이것은 0행 UPDATE가 아니라 상태 검사입니다. 결과는 같으므로 코드를 바꿀 필요가 없습니다.

C에서 송금이 아직 `REQUESTED`일 때 온 콜백은 409 `cannot complete transfer in status REQUESTED`입니다. 이벤트 행은 롤백됩니다. 이것은 한계입니다. 파트너는 송금이 `SENDING`이 된 뒤에 다시 시도해야 합니다. 이른 콜백을 저장해 나중에 적용하지는 않습니다.
