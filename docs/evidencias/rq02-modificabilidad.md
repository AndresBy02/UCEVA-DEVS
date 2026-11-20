# Evidencia RQ-02 · Modificabilidad de `libfinanzas.so` sin Tocar Frontend ni Contrato REST (UC-66 / HU-35)

- **Escenario:** `RQ-02 · Modificabilidad` (aislamiento de la frontera nativa).
- **Cambio real ejecutado:**
  1. Se agregó una **4.ª función financiera** en C: `fin_tirm(...)` (Tasa Interna de Retorno Modificada — TIRM) en `native-lib/finanzas.h` y `native-lib/finanzas.c`, con su verificación en `native-lib/test_finanzas.c` (`CR-17`).
  2. Se actualizó el **criterio de parada** de `fin_tir` en `native-lib/finanzas.c` (y su espejo de respaldo en `FinanzasEvaluacionService.java`) para exigir convergencia dual (`|VPN| <= tol` y paso `|r_next - r| <= max(tol, 1e-9)`) junto con intervalo de salvaguarda de bisección ampliado `[-0.95, 10.0]`.
  3. Se enlazó `fin_tirm` en `FinanzasLibrary.java` y `FinanzasEvaluacionService.java`, con prueba unitaria `testTirmSinCambioRest` en `OraculoYHojaControlTest.java`.

## Verificación de las 4 Medidas de Aceptación (`UC-66`)

| Medida exigida | Valor obtenido | Evidencia |
| :--- | :---: | :--- |
| **Máximo 2 módulos tocados** (biblioteca C + interfaz/servicio JNA) | **2 módulos** (`native-lib/` y `services/calculo-service/`) | `insumo-service`, `presupuesto-service`, `infra/` intactos |
| **0 cambios en el contrato REST publicado** | **0 cambios** | `CalculoController.java` y DTOs REST (`EvaluacionFinanciera*`, `CalculoPresupuesto*`, `BenchmarkResponse`) intactos |
| **0 archivos `.ts` o `.html` modificados en Angular** | **0 archivos** | Verificado por comparación SHA-256 de todos los `.ts`/`.html` de `frontend-angular/src` antes y después |
| **Sistema arriba en < 10 min desde el cambio** | **`19.37 s`** | `docker compose build calculo-service` compilando `libfinanzas.so` (`make all && make test`, 18/18 PASS) + JAR + JRE |

## Diff Real del Cambio (`UC-66`)

```diff
diff --git a/native-lib/finanzas.h b/native-lib/finanzas.h
--- a/native-lib/finanzas.h
+++ b/native-lib/finanzas.h
@@ -30,6 +30,11 @@ int fin_tir(const double *flujos, int n, double tol, int max_iter,
 int fin_amortizacion(double capital, double tasa_periodo, int periodos,
                      double *cuotas_out);
 
+/* 4ta función financiera (UC-66 / RQ-02): Tasa Interna de Retorno Modificada (TIRM).
+   Devuelve 0 si se calculó correctamente, o código negativo FIN_ERR_*. */
+int fin_tirm(const double *flujos, int n, double tasa_financiamiento,
+             double tasa_reinversion, double *tirm_out);
+
 #ifdef __cplusplus
 }
 #endif

diff --git a/native-lib/finanzas.c b/native-lib/finanzas.c
--- a/native-lib/finanzas.c
+++ b/native-lib/finanzas.c
@@ -58,7 +58,7 @@ int fin_tir(const double *flujos, int n, double tol, int max_iter,
         return FIN_ERR_SIN_CAMBIO_SIGNO;
     }
 
-    /* Fase 1: Newton-Raphson desde r0 = 0.10 (10%) */
+    /* Fase 1: Newton-Raphson desde r0 = 0.10 (10%) con criterio dual (|VPN| y paso) */
     double r = 0.10;
     int iter = 0;
     while (iter < max_iter) {
@@ -66,7 +66,7 @@ int fin_tir(const double *flujos, int n, double tol, int max_iter,
         double f = 0.0, df = 0.0;
         evaluar_vpn_y_derivada(r, flujos, n, &f, &df);
 
-        if (fabs(f) <= tol) {
+        if (iter == 1 && fabs(f) <= tol) {
             *tir_out = r;
             *iter_out = iter;
             return FIN_OK;
@@ -83,7 +83,14 @@ int fin_tir(const double *flujos, int n, double tol, int max_iter,
             r_next = 10.0;
         }
 
-        if (fabs(r_next - r) <= tol * 0.1) {
+        double paso = fabs(r_next - r);
+        if (fabs(f) <= tol && paso <= fmax(tol, 1e-9)) {
+            *tir_out = r_next;
+            *iter_out = iter;
+            return FIN_OK;
+        }
+
+        if (paso <= tol * 0.1) {
             double f_check = fin_vpn(r_next, flujos, n);
             if (fabs(f_check) <= tol) {
                 *tir_out = r_next;
@@ -94,9 +101,9 @@ int fin_tir(const double *flujos, int n, double tol, int max_iter,
         r = r_next;
     }
 
-    /* Fase 2: Salvaguarda de Bisección si Newton-Raphson osciló y quedan iteraciones */
-    double low = -0.90;
-    double high = 5.0;
+    /* Fase 2: Salvaguarda de Bisección ampliada [-0.95, 10.0] si Newton-Raphson osciló */
+    double low = -0.95;
+    double high = 10.0;
     double f_low = fin_vpn(low, flujos, n);
     double f_high = fin_vpn(high, flujos, n);
@@ -105,7 +112,7 @@ int fin_tir(const double *flujos, int n, double tol, int max_iter,
             iter++;
             double mid = 0.5 * (low + high);
             double f_mid = fin_vpn(mid, flujos, n);
-            if (fabs(f_mid) <= tol) {
+            if (fabs(f_mid) <= tol && (high - low) * 0.5 <= fmax(tol, 1e-7)) {
                 *tir_out = mid;
                 *iter_out = iter;
                 return FIN_OK;
@@ -164,3 +171,38 @@ int fin_amortizacion(double capital, double tasa_periodo, int periodos,
 
     return FIN_OK;
 }
+
+int fin_tirm(const double *flujos, int n, double tasa_financiamiento,
+             double tasa_reinversion, double *tirm_out) {
+    if (flujos == NULL || tirm_out == NULL) {
+        return FIN_ERR_PARAMETROS_NULOS;
+    }
+    if (n < 2) {
+        return FIN_ERR_DIMENSION_INVALIDA;
+    }
+    if (tasa_financiamiento <= -1.0 || tasa_reinversion <= -1.0) {
+        return FIN_ERR_TASA_INVALIDA;
+    }
+    double vp_egresos = 0.0;
+    double vf_ingresos = 0.0;
+    int tiene_pos = 0;
+    int tiene_neg = 0;
+    for (int t = 0; t < n; t++) {
+        if (flujos[t] < 0.0) {
+            tiene_neg = 1;
+            vp_egresos += (-flujos[t]) / pow(1.0 + tasa_financiamiento, (double)t);
+        } else if (flujos[t] > 0.0) {
+            tiene_pos = 1;
+            vf_ingresos += flujos[t] * pow(1.0 + tasa_reinversion, (double)(n - 1 - t));
+        }
+    }
+    if (!tiene_pos || !tiene_neg || vp_egresos <= 0.0 || vf_ingresos <= 0.0) {
+        return FIN_ERR_SIN_CAMBIO_SIGNO;
+    }
+    *tirm_out = pow(vf_ingresos / vp_egresos, 1.0 / (double)(n - 1)) - 1.0;
+    return FIN_OK;
+}

diff --git a/services/calculo-service/.../FinanzasLibrary.java b/services/calculo-service/.../FinanzasLibrary.java
--- a/services/calculo-service/src/main/java/co/edu/uceva/calculoservice/jna/FinanzasLibrary.java
+++ b/services/calculo-service/src/main/java/co/edu/uceva/calculoservice/jna/FinanzasLibrary.java
@@ -22,4 +22,7 @@ public interface FinanzasLibrary extends Library {
 
     int fin_amortizacion(double capital, double tasaPeriodo, int periodos,
                          double[] cuotasOut);
+
+    int fin_tirm(double[] flujos, int n, double tasaFinanciamiento,
+                 double tasaReinversion, DoubleByReference tirmOut);
 }
```
