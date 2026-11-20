# Evidencia RQ-03 · Suite de Pruebas Aislada en Un Solo Comando (UC-67 / HU-36)

- **Escenario:** `RQ-03 · Testabilidad` (sin levantar Kubernetes, Docker Compose ni navegador).
- **Comando único ejecutado (Windows PowerShell):** `powershell -ExecutionPolicy Bypass -File scripts/run-isolated-tests.ps1` (equivalente Linux/WSL: `make test` o `./scripts/run-isolated-tests.sh`).
- **Tiempo total de ejecución:** **`49.85 s`** (`< 60 s`, cumpliendo la medida de RQ-03).
- **Invariancia por Locale:** Verificada explícitamente ejecutando `calculo-service` bajo **`es_CO`** (`13/13` tests OK) y **`en_US`** (`13/13` tests OK).
- **Casos cubiertos:** 18 verificaciones unitarias en C (`native-lib/test_finanzas.c`: SPIKE-00, CR-08..CR-14, Multi-TIR CR-16, TIRM CR-17, flujo sin cambio de signo `-3`, no convergencia `-4`) + 13 pruebas en `calculo-service` (CR-01..CR-17 + hoja de control 200 ítems RQ-01 + sensibilidad + desembolsos + seguridad OAuth2) + 42 pruebas en `insumo-service` + 5 pruebas integradas en `presupuesto-service`.

## Salida Real Capturada

```text
=== [1/3] Suite aislada en C (native-lib / finanzas.c) ===
=== SUITE AISLADA DE PRUEBAS EN C (libfinanzas.so) - RQ-03 ===
[PASS] SPIKE-00 - fin_ping_suma(25.5, 14.5) | Esperado: 40.000000 | Obtenido: 40.000000
[PASS] CR-08 - VPN al 10% de [-1000, 500, 500, 500] | Esperado: 243.425995 | Obtenido: 243.425995
[PASS] CR-09 - VPN al 0% de [-1000, 400, 350, 500] | Esperado: 250.000000 | Obtenido: 250.000000
[PASS] CR-10-RC - Retorno TIR [-1000, 1100] | Codigo Esperado: 0 | Obtenido: 0
[PASS] CR-10-VAL - Valor TIR [-1000, 1100] | Esperado: 0.100000 | Obtenido: 0.100000
[PASS] CR-11-RC - Retorno TIR [-10000, 3500, 4000, 4500] | Codigo Esperado: 0 | Obtenido: 0
[PASS] CR-11-VAL - Valor TIR Obra 90 Dias (>= 4 dec) | Esperado: 0.092825 | Obtenido: 0.092825
[PASS] CR-12 - Caso Limite: Flujo sin cambio de signo devuelve FIN_ERR_SIN_CAMBIO_SIGNO (-3) | Codigo Esperado: -3 | Obtenido: -3
[PASS] CR-13-RC - Caso Limite: Flujo sin raiz real devuelve FIN_ERR_NO_CONVERGIO (-4) | Codigo Esperado: -4 | Obtenido: -4
[PASS] CR-13-ITER - Caso Limite: Reporta iteraciones alcanzadas (50) | Codigo Esperado: 50 | Obtenido: 50
[PASS] CR-14-RC - Retorno Amortizacion 10000 al 2% en 3 periodos | Codigo Esperado: 0 | Obtenido: 0
[PASS] CR-14-CUOTA - Cuota fija periodo 1 | Esperado: 3467.546726 | Obtenido: 3467.546726
[PASS] CR-14-SALDO - Saldo final en periodo 3 es 0.00 | Esperado: 0.000000 | Obtenido: 0.000000
[PASS] CR-16-RC - Multi-TIR [-1000, 2500, -1550] converge a raiz real | Codigo Esperado: 0 | Obtenido: 0
[PASS] CR-16-VPN - VPN(TIR) en raiz Multi-TIR es 0 | Esperado: 0.000000 | Obtenido: 0.000000
[PASS] CR-17-RC - Retorno TIRM [-10000, 3500, 4000, 4500] (rf=5%, rr=8%) | Codigo Esperado: 0 | Obtenido: 0
[PASS] CR-17-VAL - Valor TIRM esperado (12902.40/10000)^(1/3)-1 | Esperado: 0.088655 | Obtenido: 0.088655
[PASS] CR-17-ERR - TIRM sin cambio de signo devuelve FIN_ERR_SIN_CAMBIO_SIGNO (-3) | Codigo Esperado: -3 | Obtenido: -3
==============================================================
Resumen C: 18 pruebas ejecutadas, 0 fallos.

=== [2/3] Suites aisladas Java en paralelo (calculo [es_CO], insumo, presupuesto) ===
--- calculo-service (es_CO) ---
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
[PASS] calculo-service (es_CO)
--- insumo-service ---
[INFO] Tests run: 42, Failures: 0, Errors: 0, Skipped: 0
[PASS] insumo-service
--- presupuesto-service ---
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[PASS] presupuesto-service

=== [3/3] Verificacion de invariancia en Locale en_US (calculo-service) ===
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
[PASS] calculo-service (en_US)

==============================================================
SUITE AISLADA COMPLETA EXITOSA en 49.85 s (Meta RQ-03 < 60s)
==============================================================
```
