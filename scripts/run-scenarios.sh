#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT
LAST_BODY="$WORKDIR/last.json"

KEY_S1="11111111-1111-4111-8111-111111111111"
KEY_S4="22222222-2222-4222-8222-222222222222"
KEY_S6="33333333-3333-4333-8333-333333333333"

body_for() {
  cat <<EOF
{
  "customerId": "C001",
  "sendCurrency": "KRW",
  "sendAmount": "$1",
  "receiveCurrency": "PHP",
  "recipientName": "Juan"
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

transfer_id_from() {
  if command -v jq >/dev/null 2>&1; then
    jq -r '.transferId' "$1"
  else
    sed -n 's/.*"transferId"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$1" | head -n 1
  fi
}

show() {
  local title="$1"
  local method="$2"
  local path="$3"
  local key="$4"
  local body="$5"
  local outfile="$WORKDIR/body.json"

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

  local -a args=(-s -o "$outfile" -w "%{http_code}" -X "$method" "${BASE_URL}${path}")
  if [[ -n "$body" ]]; then
    args+=(-H "Content-Type: application/json" --data-binary "$body")
  fi
  if [[ -n "$key" ]]; then
    args+=(-H "Idempotency-Key: ${key}")
  fi

  local status
  status=$(curl "${args[@]}")
  cp "$outfile" "$LAST_BODY"

  echo "Status: ${status}"
  echo "Response:"
  echo '```json'
  pretty "$outfile"
  echo '```'
  echo
}

S1_BODY=$(body_for "500000")
show "S1" "POST" "/api/transfers" "$KEY_S1" "$S1_BODY"
show "S2" "POST" "/api/transfers" "$KEY_S1" "$S1_BODY"
show "S3" "POST" "/api/transfers" "$KEY_S1" "$(body_for "600000")"
show "S4a" "POST" "/api/transfers" "$KEY_S4" "$(body_for "700000")"

TRANSFER_ID=$(transfer_id_from "$LAST_BODY")
if [[ -z "$TRANSFER_ID" || "$TRANSFER_ID" == "null" ]]; then
  echo "S4a response has no transferId" >&2
  exit 1
fi

show "S4b" "POST" "/api/transfers/${TRANSFER_ID}/cancel" "" ""
show "S5" "POST" "/api/transfers/${TRANSFER_ID}/cancel" "" ""
show "S6" "POST" "/api/transfers" "$KEY_S6" "$(body_for "5000")"
