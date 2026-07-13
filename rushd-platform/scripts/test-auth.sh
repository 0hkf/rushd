#!/usr/bin/env bash
# =============================================================================
# test-auth.sh — اختبار يدوي لـ Authentication API في مشروع Rushd
#
# الاستخدام:
#   bash rushd-platform/scripts/test-auth.sh
#
# المتطلبات:
#   - curl
#   - Backend يعمل على http://localhost:8080
# =============================================================================

BASE_URL="http://localhost:8080"
PASS=0
FAIL=0

# ─── ألوان للطباعة ───────────────────────────────────────────────────────────
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RESET='\033[0m'

# ─── دوال مساعدة ─────────────────────────────────────────────────────────────

print_header() {
  echo ""
  echo -e "${CYAN}══════════════════════════════════════════${RESET}"
  echo -e "${CYAN}  $1${RESET}"
  echo -e "${CYAN}══════════════════════════════════════════${RESET}"
}

check() {
  local description="$1"
  local expected_status="$2"
  local actual_status="$3"
  local body="$4"

  if [ "$actual_status" = "$expected_status" ]; then
    echo -e "  ${GREEN}✓${RESET} $description → HTTP $actual_status"
    PASS=$((PASS + 1))
  else
    echo -e "  ${RED}✗${RESET} $description → توقعنا $expected_status، وصلنا $actual_status"
    if [ -n "$body" ]; then
      echo -e "    Body: $body"
    fi
    FAIL=$((FAIL + 1))
  fi
}

check_no_password() {
  local description="$1"
  local body="$2"

  if echo "$body" | grep -q '"password"'; then
    echo -e "  ${RED}✗${RESET} $description → كلمة المرور ظهرت في الـ response!"
    FAIL=$((FAIL + 1))
  else
    echo -e "  ${GREEN}✓${RESET} $description → كلمة المرور غائبة من الـ response"
    PASS=$((PASS + 1))
  fi
}

# ─── التحقق من تشغيل الـ Backend ─────────────────────────────────────────────

print_header "فحص الاتصال بالـ Backend"

health_status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$BASE_URL/health" 2>/dev/null)

if [ "$health_status" != "200" ]; then
  echo -e "${RED}✗ الـ Backend غير متاح على $BASE_URL${RESET}"
  echo -e "${YELLOW}  تأكد من تشغيل: mvn spring-boot:run${RESET}"
  exit 1
fi

echo -e "  ${GREEN}✓ Backend متاح — HTTP $health_status${RESET}"

# ─── Register — إنشاء المستخدمين التجريبيين ──────────────────────────────────

print_header "تسجيل المستخدمين التجريبيين"

# BUYER
resp=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"name":"Demo Buyer","email":"buyer@rushd.local","password":"Buyer123","role":"BUYER"}')
status=$(echo "$resp" | tail -1)
body=$(echo "$resp" | head -1)

if [ "$status" = "201" ]; then
  check "تسجيل Demo Buyer (BUYER)" "201" "$status" "$body"
  check_no_password "كلمة مرور BUYER" "$body"
elif [ "$status" = "409" ]; then
  echo -e "  ${YELLOW}⚠${RESET}  Demo Buyer موجود مسبقاً (409 — طبيعي)"
  PASS=$((PASS + 1))
else
  check "تسجيل Demo Buyer (BUYER)" "201" "$status" "$body"
fi

# SELLER
resp=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"name":"Demo Seller","email":"seller@rushd.local","password":"Seller123","role":"SELLER"}')
status=$(echo "$resp" | tail -1)
body=$(echo "$resp" | head -1)

if [ "$status" = "201" ]; then
  check "تسجيل Demo Seller (SELLER)" "201" "$status" "$body"
  check_no_password "كلمة مرور SELLER" "$body"
elif [ "$status" = "409" ]; then
  echo -e "  ${YELLOW}⚠${RESET}  Demo Seller موجود مسبقاً (409 — طبيعي)"
  PASS=$((PASS + 1))
else
  check "تسجيل Demo Seller (SELLER)" "201" "$status" "$body"
fi

# ADMIN
resp=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"name":"Demo Admin","email":"admin@rushd.local","password":"Admin123","role":"ADMIN"}')
status=$(echo "$resp" | tail -1)
body=$(echo "$resp" | head -1)

if [ "$status" = "201" ]; then
  check "تسجيل Demo Admin (ADMIN)" "201" "$status" "$body"
  check_no_password "كلمة مرور ADMIN" "$body"
elif [ "$status" = "409" ]; then
  echo -e "  ${YELLOW}⚠${RESET}  Demo Admin موجود مسبقاً (409 — طبيعي)"
  PASS=$((PASS + 1))
else
  check "تسجيل Demo Admin (ADMIN)" "201" "$status" "$body"
fi

# ─── Register — حالات الخطأ ───────────────────────────────────────────────────

print_header "اختبار حالات الخطأ في Register"

# بريد مكرر
resp=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"name":"Demo Buyer","email":"buyer@rushd.local","password":"Buyer123","role":"BUYER"}')
status=$(echo "$resp" | tail -1)
body=$(echo "$resp" | head -1)
check "بريد إلكتروني مكرر → 409" "409" "$status" "$body"

# اسم فارغ
resp=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"name":"","email":"newuser@rushd.local","password":"Test123","role":"BUYER"}')
status=$(echo "$resp" | tail -1)
body=$(echo "$resp" | head -1)
check "اسم فارغ → 400" "400" "$status" "$body"

# بريد غير صحيح
resp=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","email":"not-an-email","password":"Test123","role":"BUYER"}')
status=$(echo "$resp" | tail -1)
body=$(echo "$resp" | head -1)
check "بريد إلكتروني غير صحيح → 400" "400" "$status" "$body"

# كلمة مرور قصيرة
resp=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","email":"short@rushd.local","password":"123","role":"BUYER"}')
status=$(echo "$resp" | tail -1)
body=$(echo "$resp" | head -1)
check "كلمة مرور أقل من 6 أحرف → 400" "400" "$status" "$body"

# دور غير صحيح
resp=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","email":"badrole@rushd.local","password":"Test123","role":"MANAGER"}')
status=$(echo "$resp" | tail -1)
body=$(echo "$resp" | head -1)
check "دور غير صحيح (MANAGER) → 400" "400" "$status" "$body"

# ─── Login ────────────────────────────────────────────────────────────────────

print_header "اختبار Login"

# دخول ناجح
resp=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"buyer@rushd.local","password":"Buyer123"}')
status=$(echo "$resp" | tail -1)
body=$(echo "$resp" | head -1)
check "دخول ناجح → 200" "200" "$status" "$body"
check_no_password "كلمة مرور في Login response" "$body"

# استخراج الـ token
BUYER_TOKEN=$(echo "$body" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
if [ -n "$BUYER_TOKEN" ]; then
  echo -e "  ${GREEN}✓${RESET} تم استخراج JWT token بنجاح"
else
  echo -e "  ${RED}✗${RESET} فشل استخراج JWT token"
  FAIL=$((FAIL + 1))
fi

# كلمة مرور خاطئة
resp=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"buyer@rushd.local","password":"wrongpassword"}')
status=$(echo "$resp" | tail -1)
body=$(echo "$resp" | head -1)
check "كلمة مرور خاطئة → 401" "401" "$status" "$body"

# بريد غير موجود
resp=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"ghost@rushd.local","password":"anything"}')
status=$(echo "$resp" | tail -1)
body=$(echo "$resp" | head -1)
check "بريد غير موجود → 401" "401" "$status" "$body"

# ─── GET /api/auth/me ─────────────────────────────────────────────────────────

print_header "اختبار GET /api/auth/me"

# بدون token
resp=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/auth/me")
status=$(echo "$resp" | tail -1)
body=$(echo "$resp" | head -1)
check "بدون token → 401" "401" "$status" "$body"

# token غير صحيح
resp=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/auth/me" \
  -H "Authorization: Bearer this.is.not.valid")
status=$(echo "$resp" | tail -1)
body=$(echo "$resp" | head -1)
check "token غير صحيح → 401" "401" "$status" "$body"

# token صحيح (BUYER)
if [ -n "$BUYER_TOKEN" ]; then
  resp=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/auth/me" \
    -H "Authorization: Bearer $BUYER_TOKEN")
  status=$(echo "$resp" | tail -1)
  body=$(echo "$resp" | head -1)
  check "token BUYER صحيح → 200" "200" "$status" "$body"
  check_no_password "كلمة مرور في /me response" "$body"
else
  echo -e "  ${YELLOW}⚠${RESET}  تم تخطي اختبار /me بـ valid token (لم يُستخرج Token)"
fi

# ─── اختبار SELLER و ADMIN ────────────────────────────────────────────────────

print_header "اختبار تسجيل دخول SELLER و ADMIN"

# SELLER
resp=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"seller@rushd.local","password":"Seller123"}')
status=$(echo "$resp" | tail -1)
check "دخول SELLER → 200" "200" "$status"

# ADMIN
resp=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@rushd.local","password":"Admin123"}')
status=$(echo "$resp" | tail -1)
check "دخول ADMIN → 200" "200" "$status"

# ─── النتيجة النهائية ─────────────────────────────────────────────────────────

print_header "النتائج النهائية"

TOTAL=$((PASS + FAIL))
echo -e "  الاختبارات: $TOTAL"
echo -e "  ${GREEN}نجح: $PASS${RESET}"
echo -e "  ${RED}فشل: $FAIL${RESET}"

if [ "$FAIL" = "0" ]; then
  echo ""
  echo -e "  ${GREEN}✓ جميع الاختبارات نجحت!${RESET}"
  echo ""
  exit 0
else
  echo ""
  echo -e "  ${RED}✗ بعض الاختبارات فشلت. راجع التفاصيل أعلاه.${RESET}"
  echo ""
  exit 1
fi
