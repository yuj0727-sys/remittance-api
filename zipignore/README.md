# 한국에서 필리핀으로 보내는 송금 API

## 실행

Java 17이 필요합니다. Maven은 설치하지 않아도 됩니다. `./mvnw`가 첫 실행 때 Maven 3.9.11을 받아 실행합니다.

```shell
./mvnw spring-boot:run
```

서버는 `http://localhost:8080` 에서 뜹니다. 데이터는 메모리에만 있고, 종료하면 사라집니다.

## 스택

Java 17, Spring Boot 3, Spring Data JPA, H2 인메모리입니다.

## 생성 요청을 안전하게 재시도하기

헤더 이름은 `Idempotency-Key`입니다. 값은 클라이언트가 만듭니다. UUID v4를 사용자 행동 1회당 하나 만들고, 같은 행동을 다시 보낼 때는 그 값을 그대로 씁니다.

헤더가 없거나 비어 있거나 64자를 넘으면 400입니다. 같은 키와 같은 본문이면 새 송금을 만들지 않고 원래 송금을 201로 돌려줍니다. 같은 키에 본문만 다르면 409입니다.

```shell
curl -i -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 11111111-1111-4111-8111-111111111111' \
  -d '{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan"}'
```

## 선택 필드 partnerRef

생성 요청의 `partnerRef`는 있어도 되고 없어도 됩니다. 보낼 때는 1자에서 64자의 영문, 숫자, 밑줄, 하이픈만 허용합니다. 그 외에는 400입니다. 생략하면 서버가 `PR-` 뒤에 UUID를 붙여 저장합니다. 클라이언트가 보낸 값만 멱등성 해시에 들어갑니다. 서버가 만든 값은 해시에 넣지 않습니다.

`partner_ref`는 유일합니다. 같은 키와 같은 본문은 원래 송금을 돌려줍니다. 다른 송금이 같은 `partnerRef`를 다시 쓰면 409 `partnerRef already used`입니다.

## 전송

생성만으로는 파트너를 호출하지 않습니다. `POST /api/transfers/{transferId}/send`가 전송을 시작합니다. 행은 `REQUESTED`에서 `SENDING`으로 바뀌고, 파트너를 최대 3번 호출합니다. `ACCEPTED`는 파트너가 요청을 받았다는 뜻입니다. 콜백이 `COMPLETED`로 바꾸기 전까지 상태는 `SENDING`입니다. 세 번 실패하면 `FAILED`가 되고 `failureReason`은 `partner failed 3 times`입니다. `S9FAIL`은 실패합니다. `S8OK`와 `OK_REF`는 성공합니다.

```shell
curl -i -X POST http://localhost:8080/api/transfers/TRANSFER_ID/send
```

## 파트너 콜백

`POST /api/callbacks/partner`에는 `partnerRef`, `eventId`, `status`가 필요합니다. `status`는 `COMPLETED`여야 합니다. 같은 `partnerRef`에 같은 `eventId`를 다시 보내면 200이고, 송금은 바뀌지 않습니다.

```shell
curl -i -X POST http://localhost:8080/api/callbacks/partner \
  -H 'Content-Type: application/json' \
  -d '{"partnerRef":"OK_REF","eventId":"event-1","status":"COMPLETED"}'
```

## AI 도구

- **Claude:** 구현 및 테스트 작업을 위한 프롬프트 작성
- **Cursor:** 프롬프트를 바탕으로 기능 구현 및 테스트 코드 작성
- **ChatGPT:** 이해가 부족한 기술 개념과 코드 동작 원리 확인

## 가정

- `Idempotency-Key`는 필수입니다.
- 같은 키의 범위는 `customerId` 단위입니다. 다른 고객은 같은 키를 쓸 수 있습니다.
- 같은 요청을 다시 보내도 201과 원래 본문을 돌려줍니다.
- `sendAmount`는 JSON 문자열이나 JSON 정수를 허용합니다. `1.5` 같은 JSON 소수는 거절합니다.
- 본문 검증이 멱등성 조회보다 먼저입니다. 잘못된 본문은 기존 키를 찾지 않습니다.
- 전송은 `POST /api/transfers/{id}/send`에서 시작합니다. 생성 때 파트너를 호출하지 않습니다. 생성 때 바로 호출하면 행이 `REQUESTED`를 지나가고, 취소 시나리오 S4가 실패합니다.
- 일일 한도는 `sendAmount` 합계만 봅니다. 수수료는 포함하지 않습니다. 서울 날짜 기준으로 정확히 3000000 KRW는 허용합니다. `CANCELLED`와 `FAILED`는 합계에서 뺍니다.
- 같은 `partnerRef`에 같은 콜백 `eventId`를 다시 보내면 200입니다. 송금은 다시 갱신하지 않습니다.
- 파트너 성공과 실패는 `partnerRef`로 정해집니다. `abs(hashCode) % 10 < 3`이면 실패입니다. 세 번 다시 시도해도 결과는 바뀌지 않습니다. `S9FAIL`은 실패합니다. `S8OK`와 `OK_REF`는 성공합니다.
- 파트너 호출 한 번은 로그에만 남습니다 (`partner attempt ...`). 시도 이력 테이블은 없습니다. `failure_reason`은 송금이 `FAILED`가 될 때만 글자를 저장합니다.

## 구현하지 않은 것

인증, 송금 목록 조회, 페이지네이션은 없습니다. 파트너 클라이언트는 원격 서버를 호출하지 않습니다. 송금이 아직 `REQUESTED`일 때 도착한 콜백은 409이고, 그 이벤트는 저장되지 않습니다. 파트너는 송금이 `SENDING`이 된 뒤에 같은 콜백을 다시 보내야 합니다.
