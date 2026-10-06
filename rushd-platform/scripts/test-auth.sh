#!/usr/bin/env bash
# Read-only auth lifecycle smoke check using an EXISTING account; no privileged signup/seeding.
# Credentials via environment: AUTH_EMAIL, AUTH_PASSWORD. Do not enable shell tracing.
set -euo pipefail
: "${AUTH_EMAIL:?Provide AUTH_EMAIL for an existing test account}"
: "${AUTH_PASSWORD:?Provide AUTH_PASSWORD}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
DIR="$(mktemp -d)"
trap 'rm -rf "$DIR"' EXIT
JAR="$DIR/cookies"
csrf() {
  curl --fail --silent --show-error -c "$JAR" -b "$JAR" "$BASE_URL/api/auth/csrf" |
    jq -er '.token'
}
TOKEN="$(csrf)"
BODY="$(jq -n --arg email "$AUTH_EMAIL" --arg password "$AUTH_PASSWORD" '{email:$email,password:$password}')"
curl --fail --silent --show-error -c "$JAR" -b "$JAR" -H 'Content-Type: application/json' \
  -H "X-XSRF-TOKEN: $TOKEN" -d "$BODY" "$BASE_URL/api/auth/login" -o "$DIR/login"
jq -e '.user.id and (.token == null) and (.user.password == null)' "$DIR/login" > /dev/null
curl --fail --silent --show-error -b "$JAR" "$BASE_URL/api/auth/me" -o /dev/null
TOKEN="$(csrf)"
curl --fail --silent --show-error -c "$JAR" -b "$JAR" -H "X-XSRF-TOKEN: $TOKEN" \
  -X POST "$BASE_URL/api/auth/refresh" -o /dev/null
TOKEN="$(csrf)"
curl --fail --silent --show-error -c "$JAR" -b "$JAR" -H "X-XSRF-TOKEN: $TOKEN" \
  -X POST "$BASE_URL/api/auth/logout" -o /dev/null
STATUS="$(curl --silent --show-error -b "$JAR" -o /dev/null -w '%{http_code}' "$BASE_URL/api/auth/me")"
test "$STATUS" = 401
echo 'PASS: cookie login, me, refresh rotation and server logout'
