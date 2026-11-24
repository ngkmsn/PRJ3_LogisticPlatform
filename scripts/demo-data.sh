#!/usr/bin/env bash
# Seeds demo data through the public API gateway (no direct database access).
#
#   - driver2 / driver3 (role DRIVER, password Driver@123) with driver profiles
#   - GPS positions around Ho Chi Minh City for driver, driver2, driver3
#   - customer orders covering ASSIGNED, IN_TRANSIT, DELIVERED, FAILED and UNASSIGNED
#
# Safe to run repeatedly: existing users are reused, new orders are added each run.
# Usage: bash scripts/demo-data.sh            (GATEWAY defaults to http://localhost:8080)
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
DEMO_DRIVER_PASSWORD="Driver@123"

json() { python3 -c "import sys,json; d=json.load(sys.stdin); print(eval(sys.argv[1]))" "$1"; }

RESP_FILE=$(mktemp)
trap 'rm -f "$RESP_FILE"' EXIT
HTTP_STATUS=""

# request METHOD PATH TOKEN [BODY] -> sets HTTP_STATUS, response body in $RESP_FILE.
# Must be called directly (not inside $(...)) so HTTP_STATUS survives.
request() {
  local method=$1 path=$2 token=$3 body=${4:-}
  local args=(-s -o "$RESP_FILE" -w '%{http_code}' -X "$method" "$GATEWAY$path" -H 'Content-Type: application/json')
  if [[ -n "$token" ]]; then args+=(-H "Authorization: Bearer $token"); fi
  if [[ -n "$body" ]]; then args+=(-d "$body"); fi
  local attempt wait
  for attempt in 1 2 3; do
    HTTP_STATUS=$(curl "${args[@]}")
    [[ "$HTTP_STATUS" == "429" ]] || return 0
    wait=$(json "d.get('data',{}).get('retryAfterSeconds',10)" < "$RESP_FILE" 2>/dev/null || echo 10)
    echo "  rate limited, waiting ${wait}s..." >&2
    sleep "$wait"
  done
}
resp() { json "$1" < "$RESP_FILE"; }

expect_ok() {
  if [[ "$HTTP_STATUS" != 2* ]]; then
    echo "ERROR: $1 failed with HTTP $HTTP_STATUS: $(head -c 400 "$RESP_FILE")" >&2
    exit 1
  fi
}

# login USERNAME PASSWORD VAR -> stores the access token in VAR
login() {
  request POST /api/auth/login "" "{\"username\":\"$1\",\"password\":\"$2\"}"
  expect_ok "login $1"
  eval "$3=\$(resp \"d['data']['accessToken']\")"
}

ensure_driver_user() {
  local username=$1 full_name=$2 phone=$3 plate=$4 license=$5 user_id
  request POST /api/users "$ADMIN" "{\"username\":\"$username\",\"email\":\"$username@logistics.com\",\"password\":\"$DEMO_DRIVER_PASSWORD\",\"role\":\"DRIVER\"}"
  if [[ "$HTTP_STATUS" == "409" ]]; then
    request GET "/api/users?page=0&size=100" "$ADMIN"
    expect_ok "list users"
    user_id=$(resp "next(u['id'] for u in d['data']['content'] if u['username']=='$username')")
  else
    expect_ok "create user $username"
    user_id=$(resp "d['data']['id']")
  fi
  request POST /api/drivers "$ADMIN" "{\"userId\":\"$user_id\",\"fullName\":\"$full_name\",\"phoneNumber\":\"$phone\",\"licenseNumber\":\"$license\",\"licenseClass\":\"C\",\"vehicleType\":\"MOTORBIKE\",\"vehiclePlate\":\"$plate\",\"address\":\"TP. Ho Chi Minh\"}"
  [[ "$HTTP_STATUS" == 2* || "$HTTP_STATUS" == "409" ]] || { echo "ERROR: driver profile for $username failed (HTTP $HTTP_STATUS)" >&2; exit 1; }
}

set_availability() {
  request PATCH /api/drivers/me/availability "$1" "{\"availability\":\"$2\"}"
  expect_ok "set availability $2"
}

post_gps() {
  request POST /api/shipments/tracking/location "$1" "{\"latitude\":$2,\"longitude\":$3}"
  expect_ok "post GPS"
}

# create_order RECIPIENT ADDRESS PRODUCT PRICE VAR -> stores the new order id in VAR
create_order() {
  request POST /api/orders "$CUSTOMER" "{\"recipientName\":\"$1\",\"recipientPhone\":\"0987654321\",\"deliveryAddress\":\"$2\",\"notes\":\"demo\",\"items\":[{\"productName\":\"$3\",\"quantity\":1,\"unitPrice\":$4}]}"
  expect_ok "create order"
  eval "$5=\$(resp \"d['data']['id']\")"
}

# assigned_driver_token ORDER_ID -> sets T to the token of the driver the order was assigned to
# (waits for the asynchronous auto-assignment).
assigned_driver_token() {
  local order_id=$1 attempt tok
  for attempt in $(seq 1 20); do
    for tok in "$TOKEN_driver" "$TOKEN_driver2" "$TOKEN_driver3"; do
      request GET "/api/shipments/assignments/my-orders?size=50" "$tok"
      if grep -q "$order_id" "$RESP_FILE"; then
        T=$tok
        return 0
      fi
    done
    sleep 1
  done
  echo "ERROR: order $order_id was not assigned to any demo driver" >&2
  exit 1
}

driver_action() {
  local body='{}'
  if [[ $# -ge 4 ]]; then body=$4; fi
  request POST "/api/shipments/assignments/$2/$3" "$1" "$body"
  expect_ok "driver action $3 on $2"
}

echo "Gateway: $GATEWAY"
login admin 'Admin@123' ADMIN
login customer 'Customer@123' CUSTOMER

ensure_driver_user driver2 "Tran Van Hai" "0901000002" "59X1-222.22" "GPLX-DEMO-0002"
ensure_driver_user driver3 "Le Thi Ba" "0901000003" "59X1-333.33" "GPLX-DEMO-0003"

login driver "$DEMO_DRIVER_PASSWORD" TOKEN_driver
login driver2 "$DEMO_DRIVER_PASSWORD" TOKEN_driver2
login driver3 "$DEMO_DRIVER_PASSWORD" TOKEN_driver3

# drain_driver TOKEN: finishes the driver's open orders from earlier runs so the
# workload limit (3 active orders per driver) never blocks new assignments.
drain_driver() {
  local tok=$1 line order_id status
  request GET "/api/shipments/assignments/my-orders?size=100" "$tok"
  expect_ok "list driver orders"
  resp "'\\n'.join(o['orderId']+' '+o['status'] for o in d['data']['content'] if o['status'] in ('ASSIGNED','PICKED_UP','IN_TRANSIT'))" > "$RESP_FILE.open"
  while read -r order_id status; do
    [[ -z "$order_id" ]] && continue
    if [[ "$status" == "ASSIGNED" ]]; then driver_action "$tok" "$order_id" pickup; status=PICKED_UP; fi
    if [[ "$status" == "PICKED_UP" ]]; then driver_action "$tok" "$order_id" start-transit; fi
    driver_action "$tok" "$order_id" deliver '{"notes":"demo cleanup"}'
  done < "$RESP_FILE.open"
  rm -f "$RESP_FILE.open"
}

echo "Finishing open orders from earlier runs..."
drain_driver "$TOKEN_driver"; drain_driver "$TOKEN_driver2"; drain_driver "$TOKEN_driver3"

echo "Drivers available + GPS..."
set_availability "$TOKEN_driver" AVAILABLE;  post_gps "$TOKEN_driver"  10.7769 106.7009
set_availability "$TOKEN_driver2" AVAILABLE; post_gps "$TOKEN_driver2" 10.7626 106.6602
set_availability "$TOKEN_driver3" AVAILABLE; post_gps "$TOKEN_driver3" 10.8016 106.7147

echo "Orders..."
create_order "Nguyen Van An" "12 Nguyen Hue, Quan 1" "Ao thun" 150000 O_ASSIGNED
create_order "Pham Thi Binh" "45 Vo Van Tan, Quan 3" "Giay the thao" 890000 O_TRANSIT
create_order "Hoang Minh Chau" "88 Dien Bien Phu, Binh Thanh" "Tai nghe" 1250000 O_DELIVERED
create_order "Do Quoc Dung" "7 Le Van Sy, Phu Nhuan" "Sach" 210000 O_FAILED

assigned_driver_token "$O_TRANSIT"
driver_action "$T" "$O_TRANSIT" pickup;  driver_action "$T" "$O_TRANSIT" start-transit

assigned_driver_token "$O_DELIVERED"
driver_action "$T" "$O_DELIVERED" pickup; driver_action "$T" "$O_DELIVERED" start-transit
driver_action "$T" "$O_DELIVERED" deliver '{"notes":"Giao tan tay","proofOfDelivery":"demo-photo"}'

assigned_driver_token "$O_FAILED"
driver_action "$T" "$O_FAILED" pickup; driver_action "$T" "$O_FAILED" start-transit
driver_action "$T" "$O_FAILED" fail '{"failureReason":"CUSTOMER_UNAVAILABLE","notes":"Khach khong nghe may"}'

assigned_driver_token "$O_ASSIGNED"

echo "Unassigned order (all drivers temporarily unavailable)..."
for tok in "$TOKEN_driver" "$TOKEN_driver2" "$TOKEN_driver3"; do set_availability "$tok" UNAVAILABLE; done
create_order "Vu Thi Em" "200 Cach Mang Thang 8, Quan 10" "Noi com dien" 990000 O_UNASSIGNED
sleep 4
for tok in "$TOKEN_driver" "$TOKEN_driver2" "$TOKEN_driver3"; do set_availability "$tok" AVAILABLE; done

echo
printf '%-38s %s\n' "ORDER ID" "STATUS"
for id in "$O_ASSIGNED" "$O_TRANSIT" "$O_DELIVERED" "$O_FAILED" "$O_UNASSIGNED"; do
  request GET "/api/orders/$id" "$CUSTOMER"
  status=$(resp "d['data']['status']")
  printf '%-38s %s\n' "$id" "$status"
done
