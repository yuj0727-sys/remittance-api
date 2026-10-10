#!/usr/bin/env bash

# Prints live responses. Do not stop on 422, a failed grep, or a missing log.
# APP_LOG is the file from: ./mvnw spring-boot:run | tee app.log

BASE_URL="${BASE_URL:-http://localhost:8080}"
APP_LOG="${APP_LOG:-}"

# The running app accepts OK_REF.
# It rejects BAD_REF, because abs(hashCode) % 10 is less than 3.
# The string FAIL_REF is accepted, so S9 uses BAD_REF unless you override it.
OK_REF="${OK_REF:-OK_REF}"
FAIL_REF="${FAIL_REF:-BAD_REF}"

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT
LAST_BODY="$WORKDIR/last.json"
STATUS=""

KEY_S7_1="77777771-7777-4777-8777-777777777771"
KEY_S7_2="77777772-7777-4777-8777-777777777772"
KEY_S7_3="77777773-7777-4777-8777-777777777773"
KEY_S7_4="77777774-7777-4777-8777-777777777774"
KEY_S8="88888888-8888-4888-8888-888888888888"
KEY_S9="99999999-9999-4999-8999-999999999999"
EVENT_S8="s8-event-1"

body_for() {
  local customer="$1"
  local amount="$2"
  local partner_ref="${3:-}"
  if [[ -n "$partner_ref" ]]; then
    cat <<EOF
{
  "customerId": "${customer}",
  "sendCurrency": "KRW",
  "sendAmount": "${amount}",
  "receiveCurrency": "PHP",
  "recipientName": "Juan",
  "partnerRef": "${partner_ref}"
}
EOF
  else
    cat <<EOF
{
  "customerId": "${customer}",
  "sendCurrency": "KRW",
  "sendAmount": "${amount}",
  "receiveCurrency": "PHP",
  "recipientName": "Juan"
}
EOF
  fi
}

callback_body() {
  cat <<EOF
{
  "partnerRef": "$1",
  "eventId": "$2",
  "status": "COMPLETED"
}
EOF
}

pretty() {
  if command -v jq >/dev/null 2>&1; then
    jq . "$1"
  elif command -v python3 >/dev/null 2>&1; then
    python3 -m json.tool "$1"
  else
    cat "$1"
  fi
}

field_from() {
  local file="$1"
  local name="$2"
  if command -v jq >/dev/null 2>&1; then
    jq -r --arg name "$name" '.[$name] // empty' "$file"
  else
    sed -n "s/.*\"${name}\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p" "$file" | head -n 1
  fi
}

transfer_id_from() {
  field_from "$1" "transferId"
}

invoke() {
  local method="$1"
  local path="$2"
  local key="$3"
  local body="$4"
  local outfile="$WORKDIR/body.json"

  local -a args=(-s -o "$outfile" -w "%{http_code}" -X "$method" "${BASE_URL}${path}")
  if [[ -n "$body" ]]; then
    args+=(-H "Content-Type: application/json" --data-binary "$body")
  fi
  if [[ -n "$key" ]]; then
    args+=(-H "Idempotency-Key: ${key}")
  fi

  STATUS=$(curl "${args[@]}" || true)
  cp "$outfile" "$LAST_BODY"
}

show() {
  local title="$1"
  local method="$2"
  local path="$3"
  local key="$4"
  local body="$5"

  echo "### ${title}"
  echo "Request:"
  echo '```'
  echo "${method} ${path}"
  if [[ -n "$body" ]]; then
    echo "Content-Type: application/json"
  fi
  if [[ -n "$key" ]]; then
    echo "Idempotency-Key: ${key}"
  fi
  if [[ -n "$body" ]]; then
    echo
    printf '%s\n' "$body"
  fi
  echo '```'

  invoke "$method" "$path" "$key" "$body"

  echo "Status: ${STATUS}"
  echo "Response:"
  echo '```json'
  pretty "$LAST_BODY" || true
  echo '```'
  echo
}

brief() {
  local title="$1"
  local method="$2"
  local path="$3"

  echo "### ${title}"
  echo "Request: ${method} ${path}"
  echo "Status: ${STATUS}"
  echo "transferId: $(field_from "$LAST_BODY" "transferId")"
  echo "status: $(field_from "$LAST_BODY" "status")"
  echo "partnerRef: $(field_from "$LAST_BODY" "partnerRef")"
  local reason
  reason=$(field_from "$LAST_BODY" "failureReason")
  if [[ -n "$reason" ]]; then
    echo "failureReason: ${reason}"
  fi
  echo
}

S7_BODY=$(body_for "C002" "1000000")
invoke "POST" "/api/transfers" "$KEY_S7_1" "$S7_BODY"
invoke "POST" "/api/transfers" "$KEY_S7_2" "$S7_BODY"
show "S7 마지막 성공 (3번째)" "POST" "/api/transfers" "$KEY_S7_3" "$S7_BODY"
show "S7 거절 (4번째)" "POST" "/api/transfers" "$KEY_S7_4" "$S7_BODY"

S8_BODY=$(body_for "C001" "500000" "$OK_REF")
invoke "POST" "/api/transfers" "$KEY_S8" "$S8_BODY"
brief "S8 생성" "POST" "/api/transfers"
S8_ID=$(transfer_id_from "$LAST_BODY")
invoke "POST" "/api/transfers/${S8_ID}/send" "" ""
brief "S8 send" "POST" "/api/transfers/${S8_ID}/send"
S8_CALLBACK=$(callback_body "$OK_REF" "$EVENT_S8")
show "S8 콜백 1" "POST" "/api/callbacks/partner" "" "$S8_CALLBACK"
show "S8 콜백 2" "POST" "/api/callbacks/partner" "" "$S8_CALLBACK"
show "S8 조회" "GET" "/api/transfers/${S8_ID}" "" ""

S9_BODY=$(body_for "C001" "500000" "$FAIL_REF")
invoke "POST" "/api/transfers" "$KEY_S9" "$S9_BODY"
brief "S9 생성" "POST" "/api/transfers"
S9_ID=$(transfer_id_from "$LAST_BODY")
show "S9 send" "POST" "/api/transfers/${S9_ID}/send" "" ""
show "S9 조회" "GET" "/api/transfers/${S9_ID}" "" ""

echo "### S9 로그"
echo '```'
if [[ -z "$APP_LOG" ]]; then
  echo "APP_LOG is not set"
elif [[ ! -f "$APP_LOG" ]]; then
  echo "APP_LOG file not found: ${APP_LOG}"
else
  grep -F "partner attempt" "$APP_LOG" | grep -F "$S9_ID" || true
fi
echo '```'
echo
