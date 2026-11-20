#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

START_TS=$(date +%s)
echo "=== [1/3] Suite aislada en C (native-lib) ==="
make -C native-lib test

echo "=== [2/3] Suites aisladas Java en paralelo (calculo [es_CO], insumo, presupuesto) ==="
mvn -f services/calculo-service/pom.xml test -DargLine="-Duser.language=es -Duser.country=CO" -B &
PID_CAL=$!
mvn -f services/insumo-service/pom.xml test -B &
PID_INS=$!
mvn -f services/presupuesto-service/pom.xml test -B &
PID_PRE=$!

wait $PID_CAL $PID_INS $PID_PRE

echo "=== [3/3] Verificacion de invariancia en Locale en_US (calculo-service) ==="
mvn -f services/calculo-service/pom.xml surefire:test -DargLine="-Duser.language=en -Duser.country=US" -B

END_TS=$(date +%s)
ELAPSED=$((END_TS - START_TS))
echo "=============================================================="
echo "SUITE AISLADA COMPLETA FINALIZADA EN ${ELAPSED}s (Meta RQ-03 < 60s)"
echo "=============================================================="
