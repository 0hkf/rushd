#!/usr/bin/env bash
# Run the complete backend suite against a disposable, locally published PostgreSQL.
# This script never accepts a database URL and never falls back to an existing DB.
set -euo pipefail
umask 077

fail() {
  printf 'PostgreSQL verification refused: %s\n' "$*" >&2
  exit 1
}

if [ "$#" -ne 0 ]; then
  fail 'no arguments are supported. Run scripts/verify-postgres.sh without Maven or database overrides.'
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
BACKEND_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
BASELINE="$BACKEND_ROOT/db/20261013-empty-database-baseline.sql"
UPGRADE="$BACKEND_ROOT/db/20261018-property-specifications.sql"
UPGRADE_BEFORE="$BACKEND_ROOT/src/test/resources/db/property-specifications-before.sql"
UPGRADE_AFTER="$BACKEND_ROOT/src/test/resources/db/property-specifications-after.sql"
readonly SCRIPT_DIR BACKEND_ROOT BASELINE UPGRADE UPGRADE_BEFORE UPGRADE_AFTER
[ -f "$BACKEND_ROOT/pom.xml" ] || fail 'backend pom.xml is missing.'
[ -f "$BASELINE" ] || fail "the reviewed empty-database baseline is missing: $BASELINE"
for file in "$UPGRADE" "$UPGRADE_BEFORE" "$UPGRADE_AFTER"; do
  [ -f "$file" ] || fail "the P04 upgrade or its isolated verification fixture is missing: $file"
done
command -v docker >/dev/null 2>&1 || fail 'Docker CLI is unavailable; no installation or startup will be attempted.'
command -v mvn >/dev/null 2>&1 || fail 'Maven is unavailable.'

# JVM/Maven options and external Spring config can outrank normal environment
# settings. Refuse them rather than accidentally connecting outside this container.
# Only these six inherited Spring variables are safe: all are replaced below.
while IFS= read -r variable; do
  [ -n "${!variable}" ] || continue
  case "$variable" in
    SPRING_DATASOURCE_URL|SPRING_DATASOURCE_USERNAME|SPRING_DATASOURCE_PASSWORD|SPRING_DATASOURCE_DRIVER_CLASS_NAME|SPRING_JPA_HIBERNATE_DDL_AUTO|SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT)
      ;;
    SPRING_*|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|JAVA_OPTS|MAVEN_OPTS|MAVEN_ARGS|MAVEN_PROJECTBASEDIR|MAVEN_BASEDIR)
      fail "unset inherited $variable before running; its value is intentionally not printed."
      ;;
  esac
done < <(compgen -e)

# Maven searches parent directories for project-local options. A new option file
# must be reviewed before this runner is extended to accept it.
directory="$BACKEND_ROOT"
while :; do
  for config in "$directory/.mvn/maven.config" "$directory/.mvn/jvm.config" "$directory/.mvn/extensions.xml"; do
    [ ! -s "$config" ] || fail "review and remove the unsupported Maven override file before running: $config"
  done
  [ "$directory" != / ] || break
  directory=$(dirname -- "$directory")
done

# A remote Docker context would publish its port remotely, not on this machine.
# Only local Unix-socket Docker daemons are supported on macOS/Linux.
if [ -n "${DOCKER_CONTEXT:-}" ]; then
  docker_endpoint=$(docker context inspect "$DOCKER_CONTEXT" --format '{{.Endpoints.docker.Host}}') || fail 'cannot inspect the selected Docker context.'
elif [ -n "${DOCKER_HOST:-}" ]; then
  docker_endpoint="$DOCKER_HOST"
else
  docker_endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}') || fail 'cannot inspect the Docker context.'
fi
case "$docker_endpoint" in
  unix://*) ;;
  *) fail 'Docker must use a local Unix socket; remote/TCP contexts are not supported.' ;;
esac
docker_version=$(docker info --format '{{.ServerVersion}}') || fail 'Docker is not ready; no restart will be attempted.'

readonly IMAGE='postgres:16'
image_id=$(docker image inspect --format '{{.Id}}' "$IMAGE") || fail 'cached postgres:16 is required; no image pull will be attempted.'
readonly image_id
printf 'Docker server: %s\nPostgreSQL image: %s (%s)\n' "$docker_version" "$IMAGE" "$image_id"

owner="rushd-pg-verify-$(date +%s)-$$-$RANDOM"
container_id=''
create_attempted=0
readonly owner

owned_container_is_absent() {
  local matching_ids
  matching_ids=$(docker container ls --all --quiet --no-trunc --filter "name=^/${owner}$") || return 2
  [ -z "$matching_ids" ]
}

cleanup() {
  local result=$?
  local identity target found_id found_name found_owner cleanup_failed=0
  trap - EXIT HUP INT TERM
  if [ "$create_attempted" -eq 1 ]; then
    # A signal may arrive after Docker creates the container but before command
    # substitution assigns its ID. Fall back only to this run's unique name,
    # then attest its exact name, label and full ID before stopping that ID.
    target="${container_id:-$owner}"
    if identity=$(docker container inspect --format '{{.Id}}|{{.Name}}|{{index .Config.Labels "com.rushd.verify.owner"}}' "$target" 2>/dev/null); then
      IFS='|' read -r found_id found_name found_owner <<< "$identity"
      if [[ ! "$found_id" =~ ^[0-9a-f]{64}$ ]] ||
        [ "$found_name" != "/$owner" ] || [ "$found_owner" != "$owner" ] ||
        { [ -n "$container_id" ] && [ "$found_id" != "$container_id" ]; }; then
        printf 'Cleanup refused: container ownership no longer matches; nothing was stopped.\n' >&2
        cleanup_failed=1
      elif ! docker container stop --time 10 "$found_id" >/dev/null 2>&1; then
        if owned_container_is_absent; then
          printf 'Owned disposable PostgreSQL container was already removed.\n'
        else
          printf 'Cleanup failed: could not stop owned container %s.\n' "$found_id" >&2
          cleanup_failed=1
        fi
      else
        printf 'Removed the owned disposable PostgreSQL container.\n'
      fi
    elif owned_container_is_absent; then
      printf 'No owned disposable PostgreSQL container remains.\n'
    else
      printf 'Cleanup could not attest owned container %s; verify Docker/container state manually.\n' "$target" >&2
      cleanup_failed=1
    fi
  fi
  if [ "$cleanup_failed" -ne 0 ]; then
    printf 'PostgreSQL verification cleanup failed (original exit status: %s).\n' "$result" >&2
    [ "$result" -ne 0 ] || result=70
  fi
  exit "$result"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

readonly database='rushd_verify'
readonly username='rushd_verify'
readonly password="isolated-tests-only-$$-$RANDOM-$RANDOM"
create_attempted=1
container_id=$(docker run --detach --rm --pull=never \
  --name "$owner" \
  --label "com.rushd.verify.owner=$owner" \
  --publish '127.0.0.1::5432' \
  --tmpfs /var/lib/postgresql/data:rw \
  --env "POSTGRES_DB=$database" \
  --env "POSTGRES_USER=$username" \
  --env "POSTGRES_PASSWORD=$password" \
  "$IMAGE") || fail 'cannot create disposable PostgreSQL; no existing database will be used.'
[[ "$container_id" =~ ^[0-9a-f]{64}$ ]] || fail 'Docker returned an unexpected container ID.'
actual_image=$(docker container inspect --format '{{.Image}}' "$container_id")
[ "$actual_image" = "$image_id" ] || fail 'container image identity differs from the inspected cached image.'

published_port=$(docker port "$container_id" 5432/tcp)
[[ "$published_port" =~ ^127\.0\.0\.1:([0-9]+)$ ]] || fail 'expected exactly one loopback-only PostgreSQL port mapping.'
host_port="${BASH_REMATCH[1]}"
readonly host_port
printf 'Owned container: %s\nTest database: 127.0.0.1:%s/%s\n' "$container_id" "$host_port" "$database"

ready=0
for ((attempt = 1; attempt <= 45; attempt++)); do
  # TCP stays closed during the image entrypoint's temporary initialization
  # server; do not mistake that Unix-socket-only server for final readiness.
  if docker exec "$container_id" pg_isready --host 127.0.0.1 --timeout 2 --username "$username" --dbname "$database" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
[ "$ready" -eq 1 ] || fail 'new PostgreSQL did not become ready within 45 checks; no fallback will be attempted.'

docker exec "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" --command 'SELECT version();'
printf 'Applying the reviewed empty-database baseline to this owned container only.\n'
if ! docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$BASELINE"; then
  fail 'empty-database baseline/guard failed; tests will not run and no other database will be used.'
fi

readonly jdbc_url="jdbc:postgresql://127.0.0.1:$host_port/$database"
printf 'Testing P04 partial-upgrade guard, legacy preservation, replay guard and constraints.\n'
docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$UPGRADE_BEFORE"
if docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$UPGRADE"; then
  fail 'P04 partial-upgrade guard unexpectedly accepted a probe column.'
fi
docker exec "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 --username "$username" --dbname "$database" \
  --command "DO \$check\$ BEGIN IF (SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='properties' AND left(column_name,5)='spec_') <> 1 THEN RAISE EXCEPTION 'Partial upgrade was not atomic'; END IF; END; \$check\$; ALTER TABLE public.properties DROP COLUMN spec_partial_probe;"
docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$UPGRADE"
if docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$UPGRADE"; then
  fail 'P04 replay guard unexpectedly accepted an already applied upgrade.'
fi
docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$UPGRADE_AFTER"
printf 'P04 migration checks passed; disposable fixtures removed before the application suite.\n'
readonly IMAGE_UPGRADE="$BACKEND_ROOT/db/20261020-property-images.sql"
readonly IMAGE_BEFORE="$BACKEND_ROOT/src/test/resources/db/property-images-before.sql"
readonly IMAGE_AFTER="$BACKEND_ROOT/src/test/resources/db/property-images-after.sql"
for file in "$IMAGE_UPGRADE" "$IMAGE_BEFORE" "$IMAGE_AFTER"; do
  [ -f "$file" ] || fail 'P10 migration or isolated fixture is missing.'
done
printf 'Testing P10 partial/replay guards, legacy preservation and image constraints.\n'
docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$IMAGE_BEFORE"
if docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$IMAGE_UPGRADE"; then
  fail 'P10 partial-upgrade guard unexpectedly accepted the probe table.'
fi
docker exec "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 --username "$username" --dbname "$database" \
  --command "DO \$check\$ BEGIN IF to_regclass('public.property_image_collections') IS NOT NULL THEN RAISE EXCEPTION 'P10 partial upgrade was not atomic'; END IF; END; \$check\$; DROP TABLE public.property_images;"
docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$IMAGE_UPGRADE"
if docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$IMAGE_UPGRADE"; then
  fail 'P10 replay guard unexpectedly accepted an applied migration.'
fi
docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$IMAGE_AFTER"
printf 'P10 migration checks passed; disposable fixtures removed.\n'
readonly FAVORITES_UPGRADE="$BACKEND_ROOT/db/20261021-property-favorites.sql"
readonly FAVORITES_BEFORE="$BACKEND_ROOT/src/test/resources/db/property-favorites-before.sql"
readonly FAVORITES_AFTER="$BACKEND_ROOT/src/test/resources/db/property-favorites-after.sql"
for file in "$FAVORITES_UPGRADE" "$FAVORITES_BEFORE" "$FAVORITES_AFTER"; do
  [ -f "$file" ] || fail 'P13 migration or isolated fixture is missing.'
done
printf 'Testing P13 partial/replay guards, legacy preservation, uniqueness and cascades.\n'
docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$FAVORITES_BEFORE"
if docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$FAVORITES_UPGRADE"; then
  fail 'P13 partial-upgrade guard unexpectedly accepted the probe index.'
fi
docker exec "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 --username "$username" --dbname "$database" \
  --command "DO \$check\$ BEGIN IF to_regclass('public.property_favorites') IS NOT NULL THEN RAISE EXCEPTION 'P13 partial upgrade was not atomic'; END IF; END; \$check\$; DROP INDEX public.ix_favorites_page;"
docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$FAVORITES_UPGRADE"
if docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$FAVORITES_UPGRADE"; then
  fail 'P13 replay guard unexpectedly accepted an applied migration.'
fi
docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$FAVORITES_AFTER"
printf 'P13 migration checks passed; disposable fixtures removed.\n'
for draft_migration in "$BACKEND_ROOT/db/20261104-admin-property-drafts.sql" "$BACKEND_ROOT/db/20261110-admin-draft-conflicts.sql"; do
  [ -f "$draft_migration" ] || fail 'Draft migration is missing.'
  docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
    --username "$username" --dbname "$database" < "$draft_migration"
  if docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
    --username "$username" --dbname "$database" < "$draft_migration"; then
    fail 'Draft migration replay guard unexpectedly accepted an applied migration.'
  fi
done
for draft_fixture in "$BACKEND_ROOT/src/test/resources/db/admin-drafts-after.sql" "$BACKEND_ROOT/src/test/resources/db/draft-conflicts-after.sql"; do
  docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
    --username "$username" --dbname "$database" < "$draft_fixture"
done
printf 'Draft/conflict migrations and constraints passed in the disposable database.\n'
readonly MARKET_UPGRADE="$BACKEND_ROOT/db/20261111-market-references.sql"
[ -f "$MARKET_UPGRADE" ] || fail 'P20 migration is missing.'
printf 'Testing P20 atomic partial/replay guards and private evidence constraints.\n'
docker exec "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 --username "$username" --dbname "$database" \
  --command 'CREATE TABLE market_reference_versions (probe integer);'
if docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$MARKET_UPGRADE"; then
  fail 'P20 partial-upgrade guard unexpectedly accepted a probe table.'
fi
docker exec "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 --username "$username" --dbname "$database" \
  --command "DO \$check\$ BEGIN IF to_regclass('public.market_sources') IS NOT NULL OR to_regclass('public.market_references') IS NOT NULL THEN RAISE EXCEPTION 'P20 partial upgrade was not atomic'; END IF; END; \$check\$; DROP TABLE market_reference_versions;"
docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$MARKET_UPGRADE"
if docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$MARKET_UPGRADE"; then
  fail 'P20 replay guard unexpectedly accepted an applied migration.'
fi
docker exec --interactive "$container_id" psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$username" --dbname "$database" < "$BACKEND_ROOT/src/test/resources/db/market-references-after.sql"
printf 'P20 migration and evidence guards passed; no market feature was enabled outside these tests.\n'
readonly jwt_secret='rushd-postgres-verification-only-signing-key-at-least-32-bytes'
printf 'Running full mvn clean test against disposable PostgreSQL, with Hibernate validate.\n'
if (
  cd -- "$BACKEND_ROOT"
  # Disable shell Maven rc files, which can inject JVM/Maven arguments.
  export MAVEN_SKIP_RC=1
  export SPRING_DATASOURCE_URL="$jdbc_url"
  export SPRING_DATASOURCE_USERNAME="$username"
  export SPRING_DATASOURCE_PASSWORD="$password"
  export SPRING_DATASOURCE_DRIVER_CLASS_NAME='org.postgresql.Driver'
  export SPRING_JPA_HIBERNATE_DDL_AUTO='validate'
  export SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT='org.hibernate.dialect.PostgreSQLDialect'
  export JWT_SECRET="$jwt_secret"
  export SECURITY_JWT_SECRET="$jwt_secret"
  # Explicit JVM test properties also prevent inherited application settings from
  # taking precedence over the isolated datasource and reviewed schema.
  mvn --batch-mode --no-transfer-progress \
    "-Dspring.datasource.url=$jdbc_url" \
    "-Dspring.datasource.username=$username" \
    "-Dspring.datasource.password=$password" \
    '-Dspring.datasource.driver-class-name=org.postgresql.Driver' \
    '-Dspring.jpa.hibernate.ddl-auto=validate' \
    '-Dspring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect' \
    '-Dspring.jpa.generate-ddl=false' \
    '-Dspring.sql.init.mode=never' \
    "-Dsecurity.jwt.secret=$jwt_secret" \
    clean test
); then
  printf 'Full PostgreSQL test suite passed.\n'
else
  result=$?
  printf 'Full PostgreSQL test suite failed with Maven exit status %s.\n' "$result" >&2
  exit "$result"
fi
