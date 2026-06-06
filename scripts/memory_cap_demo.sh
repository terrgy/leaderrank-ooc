#!/usr/bin/env bash
set -u -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LAUNCHER="$ROOT/build/install/leaderrank/bin/leaderrank"

CAP="${CAP:-256M}"
XMX="${XMX:--Xmx128m}"
XMX_GENEROUS="${XMX_GENEROUS:--Xmx1g}"
SCALE="${SCALE:-20}"
EDGES="${EDGES:-10000000}"
SEED="${SEED:-42}"
THREADS="${THREADS:-$(nproc)}"
GRAPH="${GRAPH:-$ROOT/build/memcap-demo.csv}"

say()  { printf '%s\n' "$*"; }
rule() { printf '%s\n' "------------------------------------------------------------"; }

ensure_dist() {
    if [ ! -x "$LAUNCHER" ]; then
        say "Сборка дистрибутива (./gradlew installDist)..."
        (cd "$ROOT" && ./gradlew -q installDist)
    fi
}

ensure_graph() {
    if [ ! -f "$GRAPH" ]; then
        say "Генерация графа: scale=$SCALE, рёбер=$EDGES, seed=$SEED -> $GRAPH"
        "$LAUNCHER" generate "$GRAPH" --scale="$SCALE" --edges="$EDGES" --seed="$SEED"
    fi
    local size
    size=$(du -h "$GRAPH" | cut -f1)
    say "Граф: $GRAPH ($size на диске)"
}

ensure_enforcement() {
    if ! command -v systemd-run >/dev/null 2>&1; then
        say "ОШИБКА: systemd-run не найден. Демонстрация требует cgroup v2 + systemd."
        exit 1
    fi
    if ! systemd-run --user --scope --quiet -p MemoryMax=64M -p MemorySwapMax=0 true >/dev/null 2>&1; then
        say "ОШИБКА: systemd-run --user не может применить MemoryMax в этой среде."
        say "Контроллер памяти cgroup v2 не делегирован пользовательскому менеджеру."
        exit 1
    fi
}

run_capped() {
    local heap="$1"; shift
    systemd-run --user --scope --quiet \
        -p MemoryMax="$CAP" -p MemorySwapMax=0 \
        env JAVA_OPTS="$heap" \
        "$LAUNCHER" rank "$GRAPH" /dev/null --threads="$THREADS" "$@"
}

ensure_dist
ensure_graph
ensure_enforcement

say ""
rule
say "Демонстрация ограничения памяти ядром (cgroup v2, swap отключён)"
say "  жёсткий предел процесса (MemoryMax): $CAP"
say "  куча JVM (-Xmx):                     $XMX"
say "  потоков:                            $THREADS"
say ""
say "  -Xmx меньше предела намеренно: RSS = куча + ~70 МБ нативного"
say "  пола JVM (libjvm.so, code cache, CDS, стеки, NIO). Под весь"
say "  процесс закладываем запас на этот пол."
rule

say ""
say "[A] Потоковый out-of-core движок, куча $XMX, предел $CAP"
say "    Ожидание: успешное завершение, пик RSS заметно ниже $CAP."
rule
out_a=$(run_capped "$XMX" 2>&1); rc_a=$?
printf '%s\n' "$out_a" | grep -E 'vertices|edges|iterations|heap ceiling|peak RSS|preprocess|compute'
rss_a=$(printf '%s\n' "$out_a" | sed -n 's/^peak RSS: //p')
say "    -> код выхода: $rc_a"

say ""
say "[B] Наивный in-memory движок, та же куча $XMX, тот же предел $CAP"
say "    Ожидание: нехватка кучи Java (OutOfMemoryError) — представление O(E) не помещается."
rule
out_b=$(run_capped "$XMX" --graph=in-memory 2>&1); rc_b=$?
printf '%s\n' "$out_b" | grep -iE 'OutOfMemoryError|Exception in' | head -1
say "    -> код выхода: $rc_b"

say ""
say "[C] Наивный in-memory движок, щедрая куча $XMX_GENEROUS, тот же предел $CAP"
say "    Ожидание: процесс убит ядром (cgroup OOM, сигнал KILL, код 137)."
say "    Смысл: даже когда JVM разрешено расти, ядро не даёт превысить $CAP."
rule
out_c=$(run_capped "$XMX_GENEROUS" --graph=in-memory 2>&1); rc_c=$?
say "    -> код выхода: $rc_c"

say ""
rule
say "ИТОГ"
rule
if [ "$rc_a" -eq 0 ]; then
    say "  [A] потоковый движок:        ЗАВЕРШИЛСЯ (пик RSS: ${rss_a:-н/д})"
else
    say "  [A] потоковый движок:        НЕОЖИДАННО УПАЛ (код $rc_a)"
fi
if [ "$rc_b" -ne 0 ]; then
    say "  [B] in-memory, куча $XMX:   УПАЛ от нехватки кучи (код $rc_b)"
else
    say "  [B] in-memory, куча $XMX:   неожиданно завершился (код $rc_b)"
fi
if [ "$rc_c" -eq 137 ]; then
    say "  [C] in-memory, куча большая: УБИТ ЯДРОМ cgroup OOM (код 137)"
elif [ "$rc_c" -ne 0 ]; then
    say "  [C] in-memory, куча большая: УПАЛ (код $rc_c)"
else
    say "  [C] in-memory, куча большая: неожиданно завершился (код $rc_c)"
fi
rule
say "Один и тот же алгоритм и один и тот же предел памяти: потоковое"
say "out-of-core представление укладывается в бюджет, наивное O(E) — нет."
