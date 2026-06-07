#!/usr/bin/env bash
# =====================================================================
#  Build script for the report (report/main.tex).
#  Runs pdfLaTeX twice so the table of contents and cross-references
#  resolve. There is no latexmk on the target box, hence the manual
#  double pass. Usage:
#      ./build.sh          build main.pdf
#      ./build.sh clean    remove LaTeX build artifacts
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")"

ENGINE="${LATEX_ENGINE:-pdflatex}"
JOB="main"

clean() {
  rm -f "$JOB".{aux,log,out,toc,synctex.gz,fls,fdb_latexmk} sections/*.aux
  echo "[build] artifacts removed"
}

if [[ "${1:-}" == "clean" ]]; then
  clean
  exit 0
fi

run_pass() {
  if ! "$ENGINE" -interaction=nonstopmode -halt-on-error "$JOB.tex" >/dev/null 2>&1; then
    echo "[build] FAILED — last lines of $JOB.log:" >&2
    tail -n 40 "$JOB.log" >&2 || true
    exit 1
  fi
}

echo "[build] pass 1/2 ($ENGINE)…"
run_pass
echo "[build] pass 2/2 ($ENGINE)…"
run_pass
echo "[build] done -> $JOB.pdf"
