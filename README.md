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



## 구현하지 않은 것

Part 2는 없습니다. 파트너 호출, 일일 한도, 콜백은 구현하지 않았습니다. 인증, 송금 목록 조회, 페이지네이션도 없습니다.