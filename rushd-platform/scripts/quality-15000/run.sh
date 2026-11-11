#!/usr/bin/env bash
# No URL/database arguments. Every HTTP write targets the process/container owned here.
set -euo pipefail
umask 077
fail(){ printf 'Quality verification refused: %s\n' "$*" >&2; exit 70; }
[ "$#" -eq 0 ] || fail 'no arguments are accepted'
DIR=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(CDPATH= cd -- "$DIR/../.." && pwd)
for tool in docker mvn java javac node curl lsof; do command -v "$tool" >/dev/null || fail "missing $tool; no installation/startup attempted"; done
while IFS= read -r variable; do
  [ -n "${!variable}" ] || continue
  case "$variable" in SPRING_*|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|JAVA_OPTS|MAVEN_OPTS|MAVEN_ARGS|MAVEN_PROJECTBASEDIR|MAVEN_BASEDIR|NODE_OPTIONS) fail "unset inherited $variable; value intentionally not printed";; esac
done < <(compgen -e)
directory="$ROOT/backend"
while :; do
  for name in maven.config jvm.config extensions.xml; do [ ! -s "$directory/.mvn/$name" ] || fail "unsupported Maven override: $directory/.mvn/$name"; done
  [ "$directory" != / ] || break; directory=$(dirname -- "$directory")
done
if [ -n "${DOCKER_CONTEXT:-}" ]; then endpoint=$(docker context inspect "$DOCKER_CONTEXT" --format '{{.Endpoints.docker.Host}}')
elif [ -n "${DOCKER_HOST:-}" ]; then endpoint="$DOCKER_HOST"
else endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}'); fi
case "$endpoint" in unix://*) ;; *) fail 'local Unix-socket Docker only';; esac
docker info --format '{{.ServerVersion}}' >/dev/null || fail 'Docker not ready; no restart attempted'
image=$(docker image inspect postgres:16 --format '{{.Id}}') || fail 'cached postgres:16 required; no pull attempted'
if lsof -nP -iTCP:18096 -sTCP:LISTEN >/dev/null; then fail 'owned test port 18096 is occupied; no existing process will be stopped'; fi
# Image storage rejects symlink ancestors, including /tmp -> /private/tmp on macOS.
RESULTS="$(CDPATH= cd -- "${TMPDIR:-/tmp}" && pwd -P)/rushd-test-results"
mkdir -p "$RESULTS"
WORK=$(mktemp -d "$RESULTS/quality-15000-XXXXXXXX")
owner="rushd-quality-$(date +%s)-$$-$RANDOM"; container=''; backend_pid=''; attempted=0
printf 'RESULTS=%s\nOWNER=%s\n' "$WORK" "$owner"
cleanup(){
  local result=$? target identity
  trap - EXIT HUP INT TERM
  if [ -n "$backend_pid" ] && kill -0 "$backend_pid" 2>/dev/null; then
    identity=$(ps -p "$backend_pid" -o command=)
    case "$identity" in *"--spring.application.name=$owner"*) kill "$backend_pid"; wait "$backend_pid" 2>/dev/null || true;; *) printf 'Refused to stop an unattested PID\n' >&2; result=70;; esac
  fi
  if [ "$attempted" -eq 1 ]; then
    target="${container:-$owner}"
    if identity=$(docker inspect "$target" --format '{{.Id}}|{{.Name}}|{{index .Config.Labels "com.rushd.quality.owner"}}' 2>/dev/null); then
      case "$identity" in *"|/$owner|$owner")
        local found=${identity%%|*}
        if [ -n "$container" ] && [ "$found" != "$container" ]; then result=70
        else docker stop --time 10 "$found" >/dev/null || result=70; fi;;
        *) printf 'Refused to stop an unattested container\n' >&2; result=70;;
      esac
    elif [ -n "$(docker ps -aq --filter "name=^/${owner}$")" ]; then result=70; fi
  fi
  printf 'CLEANUP finished. Original user services were not restarted. Results retained: %s\n' "$WORK"
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP
mkdir "$WORK/java" "$WORK/images"
chmod 700 "$WORK/images"
(cd "$ROOT/backend"; MAVEN_SKIP_RC=1 mvn --batch-mode --no-transfer-progress -DskipTests compile dependency:build-classpath "-Dmdep.outputFile=$WORK/classpath") > "$WORK/compile.log" 2>&1
CP="$ROOT/backend/target/classes:$(<"$WORK/classpath")"
javac --release 17 -cp "$CP" -d "$WORK/java" "$DIR/BackendMatrix.java" > "$WORK/javac.log" 2>&1
set +e
node "$DIR/frontend.mjs" "$WORK/frontend.jsonl" > "$WORK/frontend-summary.log" 2>&1; front=$?
java -cp "$WORK/java:$CP" BackendMatrix "$WORK/backend.jsonl" "$WORK" > "$WORK/backend-summary.log" 2>&1; back=$?
set -e
[[ "$front" = 0 || "$front" = 2 ]] || fail "frontend matrix infrastructure failure; see $WORK/frontend-summary.log"
[[ "$back" = 0 || "$back" = 2 ]] || fail "Java matrix infrastructure failure; see $WORK/backend-summary.log"
password="isolated-quality-only-$$-$RANDOM"
attempted=1
container=$(docker run --detach --rm --pull=never --name "$owner" --label "com.rushd.quality.owner=$owner" \
  --publish '127.0.0.1::5432' --tmpfs /var/lib/postgresql/data:rw \
  --env POSTGRES_DB=rushd_quality --env POSTGRES_USER=rushd_quality --env "POSTGRES_PASSWORD=$password" postgres:16)
[[ "$container" =~ ^[0-9a-f]{64}$ ]] || fail 'invalid container ID'
[ "$(docker inspect --format '{{.Image}}' "$container")" = "$image" ] || fail 'cached image identity mismatch'
published=$(docker port "$container" 5432/tcp)
[[ "$published" =~ ^127\.0\.0\.1:([0-9]+)$ ]] || fail 'loopback-only ephemeral port required'
port=${BASH_REMATCH[1]};ready=0
for ((i=0;i<45;i++)); do if docker exec "$container" pg_isready -h127.0.0.1 -U rushd_quality -d rushd_quality >/dev/null 2>&1; then ready=1;break;fi;sleep 1;done
[ "$ready" = 1 ] || fail 'owned PostgreSQL startup timed out'
for migration in 20261013-empty-database-baseline.sql 20261018-property-specifications.sql 20261020-property-images.sql 20261021-property-favorites.sql; do
  docker exec -i "$container" psql -X -v ON_ERROR_STOP=1 -U rushd_quality -d rushd_quality < "$ROOT/backend/db/$migration" >> "$WORK/schema.log" 2>&1
done
java -cp "$CP" com.rushd.RushdApplication \
  "--spring.application.name=$owner" --server.address=127.0.0.1 --server.port=18096 \
  "--spring.datasource.url=jdbc:postgresql://127.0.0.1:$port/rushd_quality" \
  --spring.datasource.username=rushd_quality "--spring.datasource.password=$password" \
  --spring.datasource.driver-class-name=org.postgresql.Driver --spring.jpa.hibernate.ddl-auto=validate \
  --spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect --spring.sql.init.mode=never \
  --security.jwt.secret=quality-isolated-only-signing-key-with-at-least-32-bytes \
  --security.auth.cookie-secure=false --security.auth.frontend-origin=http://127.0.0.1:5173 \
  --rushd.favorites.enabled=true --rushd.images.enabled=true "--rushd.images.root=$WORK/images" > "$WORK/server.log" 2>&1 &
backend_pid=$!;ready=0
for ((i=0;i<45;i++));do if curl -fsS http://127.0.0.1:18096/health >/dev/null 2>&1;then ready=1;break;fi;kill -0 "$backend_pid" 2>/dev/null || break;sleep 1;done
[ "$ready" = 1 ] || fail 'owned backend did not start; inspect server.log'
node "$DIR/ownership.mjs" "$WORK" "$owner" "$container" "$backend_pid" "$port"
node "$DIR/http.mjs" seed "$WORK" > "$WORK/seed.log" 2>&1
docker exec "$container" psql -X -v ON_ERROR_STOP=1 -U rushd_quality -d rushd_quality --command \
  "DO \$guard\$ BEGIN IF current_database()<>'rushd_quality' OR current_user<>'rushd_quality' THEN RAISE EXCEPTION 'Not isolated'; END IF; END;\$guard\$; UPDATE public.users SET role='ADMIN' WHERE email='quality-admin@example.invalid' AND role='BUYER'; UPDATE public.users SET role='SELLER' WHERE email='quality-seller@example.invalid' AND role='BUYER';" >> "$WORK/seed.log"
set +e
node "$DIR/http.mjs" run "$WORK" > "$WORK/http-summary.log" 2>&1; http=$?
set -e
[[ "$http" = 0 || "$http" = 2 ]] || fail "HTTP matrix infrastructure failure; see $WORK/http-summary.log"
node "$DIR/summarize.mjs" "$WORK"
