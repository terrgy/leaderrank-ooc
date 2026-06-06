#!/usr/bin/env bash
set -u -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LAUNCHER="$ROOT/build/install/leaderrank/bin/leaderrank"
DATA_DIR="${DATA_DIR:-$ROOT/bench/data}"
RESULTS_DIR="${RESULTS_DIR:-$ROOT/bench/results}"

XMX="${XMX:--Xmx2g}"
REPEAT="${REPEAT:-5}"
THREADS_SWEEP="${THREADS_SWEEP:-1 2 4 8 16 24}"
MEMORY_SWEEP="${MEMORY_SWEEP:--Xmx2g -Xmx1g -Xmx512m -Xmx256m -Xmx128m}"

SCALING_SCALE="${SCALING_SCALE:-20}"
SCALING_EDGES="${SCALING_EDGES:-16000000}"
SEED="${SEED:-42}"

GOOGLE_LIMIT="${GOOGLE_LIMIT:-30}"
LIVEJOURNAL_LIMIT="${LIVEJOURNAL_LIMIT:-30}"
GOOGLE_FLOOR_SWEEP="${GOOGLE_FLOOR_SWEEP:-256m 160m 128m 96m 80m 72m 64m 56m 48m 44m}"
LIVEJOURNAL_FLOOR_SWEEP="${LIVEJOURNAL_FLOOR_SWEEP:-1g 768m 512m 384m 320m 288m 264m 248m 232m 224m 216m}"
FLOOR_MAX_ITERATIONS="${FLOOR_MAX_ITERATIONS:-30}"

WEB_GOOGLE_URL="${WEB_GOOGLE_URL:-https://snap.stanford.edu/data/web-Google.txt.gz}"
LIVEJOURNAL_URL="${LIVEJOURNAL_URL:-https://snap.stanford.edu/data/soc-LiveJournal1.txt.gz}"

say()  { printf '%s\n' "$*"; }
rule() { printf '%s\n' "------------------------------------------------------------"; }

ensure_dist() {
    if [ ! -x "$LAUNCHER" ]; then
        say "Сборка дистрибутива (./gradlew installDist)..." 1>&2
        (cd "$ROOT" && ./gradlew -q installDist 1>&2)
    fi
}

to_csv_filter() {
    awk '{ sub(/\r$/, "") } NF==2 && $1 ~ /^[0-9]+$/ && $2 ~ /^[0-9]+$/ { print $1","$2 }'
}

fetch_snap() {
    local url="$1" base="$2"
    local gz="$DATA_DIR/$base.txt.gz" csv="$DATA_DIR/$base.csv"
    if [ -f "$csv" ]; then printf '%s' "$csv"; return 0; fi
    mkdir -p "$DATA_DIR"
    if [ ! -f "$gz" ]; then
        say "Загрузка $base из $url ..." 1>&2
        curl -fSL --retry 3 -o "$gz" "$url" 1>&2
    fi
    say "Распаковка и конвертация $base -> CSV (from,to) ..." 1>&2
    { echo "from,to"; gunzip -c "$gz" | to_csv_filter; } > "$csv"
    printf '%s' "$csv"
}

ensure_generated() {
    local scale="$1" edges="$2" seed="$3"
    local graph="$DATA_DIR/rmat-s${scale}-e${edges}-${seed}.csv"
    if [ ! -f "$graph" ]; then
        mkdir -p "$DATA_DIR"
        say "Генерация R-MAT: scale=$scale, рёбер=$edges, seed=$seed ..." 1>&2
        "$LAUNCHER" generate "$graph" --scale="$scale" --edges="$edges" --seed="$seed" 1>&2
    fi
    printf '%s' "$graph"
}

field() { sed -n "s/^$1[^0-9-]*//p" <<<"$2" | head -1 | sed -E 's/[^0-9.].*$//'; }

CSV_OUT=""
bench_run() {
    local experiment="$1" label="$2" graph="$3" threads="$4" xmx="$5"
    local out rc
    out=$(JAVA_OPTS="$xmx" "$LAUNCHER" rank "$graph" /dev/null \
            --engine=parallel --graph=out-of-core --threads="$threads" --repeat="$REPEAT" 2>&1)
    rc=$?
    if [ "$rc" -ne 0 ]; then
        say "  ОШИБКА: '$label' threads=$threads $xmx -> код $rc"
        printf '%s\n' "$out" | tail -6
        return 1
    fi
    local verts edges iters pre comp rss heap
    verts=$(field 'vertices:' "$out")
    edges=$(field 'edges:' "$out")
    iters=$(field 'iterations:' "$out")
    pre=$(field 'preprocess:' "$out")
    comp=$(field 'compute:' "$out")
    heap=$(field 'heap ceiling (-Xmx):' "$out")
    rss=$(field 'peak RSS:' "$out")
    printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
        "$experiment" "$label" "$verts" "$edges" "$threads" "$xmx" "$pre" "$comp" "$iters" "$rss" \
        >> "$CSV_OUT"
    printf '%-14s P=%-3s %-8s pre=%8s ms  compute=%8s ms  iters=%-4s RSS=%7s MiB\n' \
        "$label" "$threads" "$xmx" "${pre:-?}" "${comp:-?}" "${iters:-?}" "${rss:-?}"
}

start_csv() {
    CSV_OUT="$RESULTS_DIR/$1.csv"
    mkdir -p "$RESULTS_DIR"
    echo "experiment,graph,vertices,edges,threads,xmx,preprocess_ms,compute_ms,iterations,peak_rss_mib" > "$CSV_OUT"
}

speedup_table() {
    awk -F, 'NR>1 {pre[$5]=$7; comp[$5]=$8; rss[$5]=$10; iter[$5]=$9}
        END {
            base=comp["1"];
            printf "  %-8s %12s %12s %10s %12s %10s\n","threads","preprocess","compute","speedup","efficiency","RSS";
            n=asorti(comp, k, "@ind_num_asc");
            for (i=1;i<=n;i++){ t=k[i];
                sp=(comp[t]>0)?base/comp[t]:0; ef=sp/t;
                printf "  %-8s %9s ms %9s ms %9.2fx %11.0f%% %7s MiB\n", t, pre[t], comp[t], sp, ef*100, rss[t]; }
        }' "$CSV_OUT"
}

experiment_scaling() {
    local graph; graph=$(ensure_generated "$SCALING_SCALE" "$SCALING_EDGES" "$SEED")
    start_csv "scaling"
    say ""
    rule
    say "МАСШТАБИРОВАНИЕ ПО ПОТОКАМ (R-MAT scale=$SCALING_SCALE, рёбер=$SCALING_EDGES, $XMX)"
    say "  warm-up: --repeat=$REPEAT (последний прогон); compute = горячее время ядра"
    rule
    local t
    for t in $THREADS_SWEEP; do
        bench_run "scaling" "rmat-s${SCALING_SCALE}" "$graph" "$t" "$XMX" || true
    done
    say ""
    say "Сводка (ускорение по compute относительно P=1):"
    speedup_table
}

experiment_memory() {
    local graph; graph=$(ensure_generated "$SCALING_SCALE" "$SCALING_EDGES" "$SEED")
    start_csv "memory"
    say ""
    rule
    say "ЗАВИСИМОСТЬ ПИКА RSS ОТ -Xmx (R-MAT scale=$SCALING_SCALE, потоков=$(nproc))"
    say "  показывает: RSS ограничен -Xmx, а не размером графа; ядро адаптируется"
    rule
    local m
    for m in $MEMORY_SWEEP; do
        bench_run "memory" "rmat-s${SCALING_SCALE}" "$graph" "$(nproc)" "$m" || true
    done
}

experiment_snap() {
    start_csv "snap"
    say ""
    rule
    say "РЕАЛЬНЫЕ ГРАФЫ SNAP (потоков=$(nproc), $XMX, --repeat=$REPEAT)"
    rule
    local wg lj
    wg=$(fetch_snap "$WEB_GOOGLE_URL" "web-Google") \
        && bench_run "snap" "web-Google" "$wg" "$(nproc)" "$XMX" || true
    lj=$(fetch_snap "$LIVEJOURNAL_URL" "soc-LiveJournal1") \
        && bench_run "snap" "soc-LiveJournal1" "$lj" "$(nproc)" "$XMX" || true
}

experiment_quick() {
    local graph; graph=$(ensure_generated 14 200000 "$SEED")
    start_csv "quick"
    say ""
    rule
    say "БЫСТРАЯ ПРОВЕРКА (R-MAT scale=14, рёбер=200000)"
    rule
    local t
    for t in 1 2 4; do
        bench_run "quick" "rmat-s14" "$graph" "$t" "$XMX" || true
    done
    say ""
    speedup_table
}

floor_run() {
    local label="$1" graph="$2" xmx="$3" limit="$4"
    local out rc t0 t1 wall status
    t0=$(date +%s.%N)
    out=$(timeout -k 10 "$limit" env JAVA_OPTS="-Xmx$xmx" "$LAUNCHER" rank "$graph" /dev/null \
            --engine=parallel --graph=out-of-core --threads="$(nproc)" \
            --max-iterations="$FLOOR_MAX_ITERATIONS" --repeat=1 2>&1)
    rc=$?
    t1=$(date +%s.%N)
    wall=$(awk "BEGIN{printf \"%.0f\", ($t1-$t0)*1000}")
    if [ "$rc" -eq 124 ] || [ "$rc" -eq 137 ]; then
        status="THRASH-timeout>${limit}s"
    elif [ "$rc" -ne 0 ]; then
        if printf '%s' "$out" | grep -q "OutOfMemoryError"; then status="OOM-heap"; else status="FAIL-rc$rc"; fi
    else
        status="ok"
    fi
    local pre comp bins binsz waves rss
    pre=$(field 'preprocess:' "$out"); comp=$(field 'compute:' "$out"); rss=$(field 'peak RSS:' "$out")
    bins=$(sed -n 's/^bins: \([0-9]*\).*/\1/p' <<<"$out")
    binsz=$(sed -n 's/^bins: [0-9]* (<= \([0-9]*\).*/\1/p' <<<"$out")
    waves=$(sed -n 's/.*edges\/bin, \([0-9]*\) distribution.*/\1/p' <<<"$out")
    printf '%s,-Xmx%s,%s,%s,%s,%s,%s,%s,%s\n' \
        "$label" "$xmx" "$status" "$wall" "${pre:-}" "${comp:-}" "${bins:-}" "${binsz:-}" "${waves:-}" >> "$CSV_OUT"
    printf '  -Xmx%-5s %-22s wall=%7s ms  pre=%8s comp=%7s  bins=%-8s M=%-9s waves=%-4s RSS=%s\n' \
        "$xmx" "$status" "$wall" "${pre:-?}" "${comp:-?}" "${bins:-?}" "${binsz:-?}" "${waves:-?}" "${rss:-?} MiB" 1>&2
    printf '%s' "$status"
}

floor_sweep() {
    local label="$1" graph="$2" limit="$3"; shift 3
    say ""
    rule
    say "ПОИСК НИЖНЕЙ ГРАНИЦЫ ПАМЯТИ: $label (лимит ${limit}s, --max-iterations=$FLOOR_MAX_ITERATIONS)"
    say "  спуск по -Xmx до первого отказа (thrashing/OOM); последний ok = граница"
    rule
    local x last_ok=""
    for x in "$@"; do
        local status
        status=$(floor_run "$label" "$graph" "$x" "$limit")
        case "$status" in
            ok) last_ok="$x" ;;
            THRASH*) break ;;
        esac
    done
    say "  -> минимальный -Xmx с адекватным временем для $label: ${last_ok:-не найдено} (потоков=$(nproc))"
}

experiment_floor() {
    CSV_OUT="$RESULTS_DIR/floor.csv"
    mkdir -p "$RESULTS_DIR"
    echo "graph,xmx,status,wall_ms,preprocess_ms,compute_ms,bins,max_edges_per_bin,waves" > "$CSV_OUT"
    local wg lj
    wg=$(fetch_snap "$WEB_GOOGLE_URL" "web-Google") \
        && floor_sweep "web-Google" "$wg" "$GOOGLE_LIMIT" $GOOGLE_FLOOR_SWEEP
    lj=$(fetch_snap "$LIVEJOURNAL_URL" "soc-LiveJournal1") \
        && floor_sweep "soc-LiveJournal1" "$lj" "$LIVEJOURNAL_LIMIT" $LIVEJOURNAL_FLOOR_SWEEP
}

usage() {
    cat <<EOF
Использование: scripts/benchmark.sh [режим]

Режимы:
  quick     быстрая проверка на маленьком сгенерированном графе (секунды)
  scaling   масштабирование по потокам на R-MAT scale=$SCALING_SCALE (P: $THREADS_SWEEP)
  memory    зависимость пика RSS от -Xmx
  snap      реальные графы SNAP (web-Google, soc-LiveJournal1; авто-загрузка)
  floor     нижняя граница -Xmx для SNAP-графов с адекватным временем
  all       scaling + snap (по умолчанию)

Переменные окружения: XMX, REPEAT, THREADS_SWEEP, MEMORY_SWEEP,
  SCALING_SCALE, SCALING_EDGES, SEED, WEB_GOOGLE_URL, LIVEJOURNAL_URL.
Результаты: $RESULTS_DIR/<режим>.csv
EOF
}

main() {
    ensure_dist
    local mode="${1:-all}"
    case "$mode" in
        quick)   experiment_quick ;;
        scaling) experiment_scaling ;;
        memory)  experiment_memory ;;
        snap)    experiment_snap ;;
        floor)   experiment_floor ;;
        all)     experiment_scaling; experiment_snap ;;
        -h|--help|help) usage; return 0 ;;
        *) say "Неизвестный режим: $mode"; usage; return 2 ;;
    esac
    say ""
    say "CSV с результатами: $CSV_OUT"
}

main "$@"
