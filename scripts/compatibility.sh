#!/usr/bin/env bash
# Runs both samples, with all their scenario tests, on every supported Spring Boot and Spring AI version pair,
# against boot-mcp-doctor as it is published. Use it before a release to update docs/compatibility.md. Each pair
# boots the samples many times, so a full run takes a while.
#
#   scripts/compatibility.sh [workers] [pairs-file]
#
# workers defaults to 4. pairs-file limits the run to its lines, each "spring-boot spring-ai", such as
# "4.1.0 2.0.0"; without it, every supported pair is run. Everything, including a separate local Maven repository,
# goes to target/compatibility. Results are in target/compatibility/results.txt.
set -euo pipefail

# The supported versions, as in docs/compatibility.md.
SPRING_BOOT='4\.1\.[0-9]+'
SPRING_AI='2\.0\.[0-9]+'

ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK="$ROOT/target/compatibility"
WORKERS="${1:-4}"
PAIRS="${2:-}"
SAMPLES=samples/demo-app,samples/secured-demo-app
# Workers share the local repository, so downloads take file locks.
MAVEN=(-B -ntp -Dmaven.repo.local="$WORK/m2" -Daether.syncContext.named.factory=file-lock
    -Daether.syncContext.named.nameMapper=file-gav)

versions() {
    curl -fsS "https://repo.maven.apache.org/maven2/$1/maven-metadata.xml" | grep -oE '<version>[^<]+' \
        | sed 's/<version>//' | grep -xE "$2"
}

all_pairs() {
    local boot ai
    for boot in $(versions org/springframework/boot/spring-boot-dependencies "$SPRING_BOOT"); do
        for ai in $(versions org/springframework/ai/spring-ai-bom "$SPRING_AI"); do
            echo "$boot $ai"
        done
    done
}

copy_sources() {
    rm -rf "${1:?}"
    mkdir -p "$1"
    rsync -a --exclude target --exclude .git "$ROOT/" "$1/"
}

# Runs one pair in worker directory $1 and prints its result line. It never fails, so that every pair reports.
run_pair() {
    local dir="$1" boot="$2" ai="$3" log deps module status totals failed
    log="$WORK/logs/spring-boot-$boot-spring-ai-$ai.log"
    (cd "$dir" && ./mvnw "${MAVEN[@]}" -pl "$SAMPLES" -Dspring-boot.version="$boot" -Dspring-ai.version="$ai" \
        clean verify dependency:list -DoutputFile=target/deps.txt -DincludeScope=test \
        -Dmaven.test.failure.ignore=true > "$log" 2>&1) || true
    deps=""
    for module in ${SAMPLES//,/ }; do
        deps="$deps $dir/$module/target/deps.txt"
    done
    status=$({ grep -oE 'BUILD (SUCCESS|FAILURE)' "$log" || true; } | tail -1)
    totals=$({ grep -E '^\[(INFO|ERROR|WARNING)\] Tests run: [0-9]+, F[^-]*$' "$log" || true; } \
        | sed -E 's/.*Tests run: ([0-9]+), Failures: ([0-9]+), Errors: ([0-9]+).*/\1 \2 \3/' \
        | awk '{ t += $1; f += $2 + $3 } END { print t + 0, f + 0 }')
    failed=$({ grep -E '^\[ERROR\]   [A-Za-z]+Tests\.' "$log" || true; } \
        | sed -E 's/^\[ERROR\]   ([A-Za-z]+Tests\.[A-Za-z]+).*/\1/' | sort -u | tr '\n' ' ')
    resolved() { { grep -hoE "$1:jar:[^:]+" $deps 2>/dev/null || true; } | sed 's/.*jar://' | sort -u | tr '\n' ' '; }
    # A pair only passes when the build ran its tests, none failed, and the requested versions were resolved.
    local result=FAIL
    if [ "$status" = "BUILD SUCCESS" ] && [ "${totals#* }" = 0 ] && [ "${totals% *}" -gt 0 ] \
            && [ "$(resolved org.springframework.boot:spring-boot)" = "$boot " ] \
            && [ "$(resolved org.springframework.ai:spring-ai-mcp)" = "$ai " ]; then
        result=PASS
    fi
    local summary="$result spring-boot=$boot spring-ai=$ai tests=${totals% *} failed=${totals#* }"
    echo "$summary mcp-sdk=$(resolved io.modelcontextprotocol.sdk:mcp-core)${failed:+ failing: $failed}"
}

mkdir -p "$WORK/logs"
if [ -n "$PAIRS" ]; then
    grep -E '^[0-9]' "$PAIRS" > "$WORK/pairs.txt"
else
    all_pairs > "$WORK/pairs.txt"
fi
echo "$(wc -l < "$WORK/pairs.txt" | tr -d ' ') pairs on $WORKERS workers; logs in $WORK/logs"

copy_sources "$WORK/src"
if ! (cd "$WORK/src" && ./mvnw "${MAVEN[@]}" install -DskipTests -pl boot-mcp-doctor-spring-boot-starter -am \
        > "$WORK/logs/install.log" 2>&1); then
    echo "Installing boot-mcp-doctor failed; see $WORK/logs/install.log"
    exit 1
fi

: > "$WORK/results.txt"
for worker in $(seq 1 "$WORKERS"); do
    copy_sources "$WORK/worker-$worker"
    (
        set +e
        awk -v n="$WORKERS" -v w="$worker" '(NR - 1) % n == w - 1' "$WORK/pairs.txt" | while read -r boot ai; do
            run_pair "$WORK/worker-$worker" "$boot" "$ai" | tee -a "$WORK/results.txt"
        done
    ) &
done
wait

sort -k2,2V -k3,3V -o "$WORK/results.txt" "$WORK/results.txt"
echo
echo "$(grep -c '^PASS' "$WORK/results.txt" || true) passed, $(grep -c '^FAIL' "$WORK/results.txt" || true) failed:"
grep '^FAIL' "$WORK/results.txt" || echo "  none"
! grep -q '^FAIL' "$WORK/results.txt"
