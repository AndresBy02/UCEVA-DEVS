# 5. Contrato Congelado de `calculo-service` y Casos de Referencia (Oráculo)

**Proyecto:** Presupuestador de Obra — UCEVA Devs  
**Cursos:** Programación en Ambiente Web 2 · Arquitectura de Computadores (Core Systems)  
**Referencia:** UC-81 / HU-43 · RQ-01 · RQ-02 · RQ-03  
**Estado del Contrato:** `CONGELADO (v1.0.0 - Semana 1/2)`

---

## 1. Propósito del Contrato Congelado

Este documento establece el contrato inmutable tanto de la **API REST de `calculo-service`** como de la **interfaz JNA (`FinanzasLibrary`)** y los **12+ casos de referencia con su valor esperado**.
- Permite que el equipo de **Frontend Angular** y **`presupuesto-service`** consuman los endpoints sin depender de cambios internos en C.
- Hace medible el escenario **RQ-02 (Modificabilidad)**: cualquier mejora o nueva función interna en `libfinanzas.so` no modifica este contrato REST (`0 cambios` en la API publicada).

---

## 2. Interfaz JNA Exacta en Java (`FinanzasLibrary.java`)

```java
package co.edu.uceva.calculoservice.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.IntByReference;

public interface FinanzasLibrary extends Library {

    int FIN_OK = 0;
    int FIN_ERR_PARAMETROS_NULOS = -1;
    int FIN_ERR_DIMENSION_INVALIDA = -2;
    int FIN_ERR_SIN_CAMBIO_SIGNO = -3;
    int FIN_ERR_NO_CONVERGIO = -4;
    int FIN_ERR_TASA_INVALIDA = -5;

    /**
     * Spike / Healthcheck: suma dos valores en C para comprobar la carga de libfinanzas.so.
     */
    double fin_ping_suma(double a, double b);

    /**
     * Valor Presente Neto: fórmula cerrada en una sola pasada.
     */
    double fin_vpn(double tasa, double[] flujos, int n);

    /**
     * Tasa Interna de Retorno por aproximaciones sucesivas.
     * Devuelve 0 (FIN_OK) si convergió, negativo si no.
     * La tasa sale por tirOut y el conteo de iteraciones por iterOut.
     */
    int fin_tir(double[] flujos, int n, double tol, int maxIter,
                DoubleByReference tirOut, IntByReference iterOut);

    /**
     * Tabla de amortización (Sistema Francés): llena el arreglo cuotasOut
     * (reservado por Java con tamaño periodos * 4) con filas de [cuota, interes, abono, saldo].
     */
    int fin_amortizacion(double capital, double tasaPeriodo, int periodos,
                         double[] cuotasOut);
}
```

---

## 3. Endpoints REST Congelados de `calculo-service` (Base URL: `/api/v1/calculos`)

Todos los valores numéricos de dinero viajan en JSON con representación decimal exacta (`BigDecimal`), sin notación científica y con las escalas definidas en `03-representacion-dinero-y-redondeo.md`.

### 3.1. `GET /api/v1/calculos/spike-jna` (Verificación del Contenedor Nativo — HU-44)
Comprueba en tiempo real que `libfinanzas.so` está cargada en el contenedor Linux y ejecuta una operación nativa en C.
- **Query Params opcionales:** `a` (default `25.5`), `b` (default `14.5`).
- **Response `200 OK`:**
```json
{
  "bibliotecaCargada": true,
  "nombreBiblioteca": "libfinanzas.so",
  "operandoA": 25.5,
  "operandoB": 14.5,
  "resultadoNativoC": 40.0,
  "arquitecturaContenedor": "amd64 / Linux",
  "mensaje": "Biblioteca nativa libfinanzas.so invocada exitosamente via JNA"
}
```

---

### 3.2. `POST /api/v1/calculos/presupuesto` (Cálculo Exacto de Presupuesto, AIU, Márgenes y Punto de Equilibrio — RF-03, RF-04, RF-05, RF-06, RQ-01)
Recibe la estructura de capítulos e ítems junto con los porcentajes de AIU y datos de costo fijo, aplicando el protocolo único de redondeo (`RoundingMode.HALF_UP`).

- **Request Body (`CalculoPresupuestoRequest`):**
```json
{
  "porcentajeAdministracion": 8.00,
  "porcentajeImprevistos": 3.00,
  "porcentajeUtilidad": 5.00,
  "costoFijo": 15000000.00,
  "precioVentaUnitario": 250000.00,
  "costoVariableUnitario": 175000.00,
  "capitulos": [
    {
      "capituloId": "CAP-01",
      "nombre": "01. PRELIMINARES",
      "items": [
        {
          "itemId": "ITEM-001",
          "codigoInsumo": "PRE-001",
          "descripcion": "Localización y replanteo topográfico",
          "unidad": "M2",
          "cantidad": 125.500,
          "precioUnitario": 12.345
        }
      ]
    }
  ]
}
```

- **Response `200 OK` (`CalculoPresupuestoResponse`):**
```json
{
  "capitulos": [
    {
      "capituloId": "CAP-01",
      "nombre": "01. PRELIMINARES",
      "subtotalCapitulo": 1549.30,
      "items": [
        {
          "itemId": "ITEM-001",
          "codigoInsumo": "PRE-001",
          "cantidad": 125.500,
          "precioUnitario": 12.345,
          "valorParcial": 1549.30
        }
      ]
    }
  ],
  "costoDirecto": 1549.30,
  "porcentajeAdministracion": 8.00,
  "valorAdministracion": 123.94,
  "porcentajeImprevistos": 3.00,
  "valorImprevistos": 46.48,
  "porcentajeUtilidad": 5.00,
  "valorUtilidad": 77.47,
  "precioVenta": 1797.19,
  "margenSobreCostoPorcentaje": 16.00,
  "rotuloMargenSobreCosto": "Margen sobre el costo (Markup = (PV - CD) / CD)",
  "margenSobrePrecioVentaPorcentaje": 13.79,
  "rotuloMargenSobrePrecioVenta": "Margen sobre el precio de venta (Gross Margin = (PV - CD) / PV)",
  "puntoEquilibrio": {
    "viable": true,
    "cantidadEquilibrio": 200.00,
    "margenContribucionUnitario": 75000.00,
    "mensaje": "El trabajo deja de perder dinero a partir de 200.00 unidades"
  }
}
```
- **Comportamiento cuando el margen de contribución es $\le 0$ (RF-06):**
```json
{
  "puntoEquilibrio": {
    "viable": false,
    "cantidadEquilibrio": null,
    "margenContribucionUnitario": -10000.00,
    "mensaje": "con este precio no hay punto de equilibrio"
  }
}
```

---

### 3.3. `POST /api/v1/calculos/evaluacion-financiera` (VPN, TIR y Tabla de Amortización en C — RF-08)
Invoca `libfinanzas.so` (o el respaldo Java si está configurado) para resolver el VPN, la TIR y la tabla de amortización.

- **Request Body (`EvaluacionFinancieraRequest`):**
```json
{
  "tasaDescuento": 0.015,
  "flujosCaja": [-10000000.00, 3500000.00, 4000000.00, 4500000.00],
  "toleranciaTir": 1e-9,
  "maxIteracionesTir": 1000,
  "financiacion": {
    "capital": 10000000.00,
    "tasaPeriodo": 0.018,
    "periodos": 3
  }
}
```

- **Response `200 OK` — Caso Convergente (`EvaluacionFinancieraResponse`):**
```json
{
  "motorUtilizado": "NATIVE_C_LIBFINANZAS",
  "vpn": 1528412.68,
  "tir": {
    "convergio": true,
    "estado": "CONVERGIO",
    "codigoRetornoC": 0,
    "tasaInternaRetorno": 0.092825,
    "tasaInternaRetornoPorcentaje": 9.2825,
    "iteraciones": 5,
    "mensaje": "Convergencia alcanzada en 5 iteraciones"
  },
  "amortizacion": {
    "capital": 10000000.00,
    "tasaPeriodo": 0.018,
    "periodos": 3,
    "cuotaFija": 3454015.05,
    "filas": [
      { "periodo": 1, "cuota": 3454015.05, "interes": 180000.00, "abonoCapital": 3274015.05, "saldo": 6725984.95 },
      { "periodo": 2, "cuota": 3454015.05, "interes": 121067.73, "abonoCapital": 3332947.32, "saldo": 3393037.63 },
      { "periodo": 3, "cuota": 3454015.05, "interes": 61074.68,  "abonoCapital": 3392940.37, "saldo": 0.00 }
    ]
  }
}
```

- **Response `200 OK` (o estado de negocio controlado) — Cuando la TIR NO Converge o no hay cambio de signo (RF-08):**
> **Regla Crítica RF-08:** *Nunca devuelve `0` haciéndolo pasar por una respuesta.* Si no converge, `tasaInternaRetorno` es `null`, `estado` es `"no convergió"` y se reporta el número exacto de iteraciones alcanzado.
```json
{
  "motorUtilizado": "NATIVE_C_LIBFINANZAS",
  "vpn": -450000.00,
  "tir": {
    "convergio": false,
    "estado": "no convergió",
    "codigoRetornoC": -4,
    "tasaInternaRetorno": null,
    "tasaInternaRetornoPorcentaje": null,
    "iteraciones": 1000,
    "mensaje": "no convergió (alcanzado el límite de 1000 iteraciones)"
  },
  "amortizacion": null
}
```

---

## 4. Los 14 Casos de Referencia Oficiales (Oráculo para C y Java — RQ-03)

Estos casos se definieron y calcularon de forma independiente antes de escribir el código. La suite aislada (`make test` en C y `mvn test` en Java) debe pasar el 100% de estos casos en menos de 60 segundos.

| ID | Categoría | Entrada | Valor Esperado (Oráculo) | Qué Verifica |
| :--- | :--- | :--- | :--- | :--- |
| **CR-01** | Insumo / Parcial (RF-01) | $Q = 1.000$, $P = 12.345$ | Precio conservado $= 12.345$; Valor parcial redondeado $= 12.35$ | Conservación de 3 decimales en precio y redondeo `HALF_UP` en parcial |
| **CR-02** | Costo Directo + AIU (RF-04) | $CD = 1000.00$, $A=8\%$, $I=3\%$, $U=5\%$ | $A=80.00$, $I=30.00$, $U=50.00$, $PV=1160.00$ | AIU discriminado y suma verificable de los 4 valores |
| **CR-03** | Los Dos Márgenes (RF-05) | $CD = 100.00$, $PV = 125.00$ | $\text{Margen}_{\text{costo}} = 25.00\%$, $\text{Margen}_{\text{venta}} = 20.00\%$ | No confundir Markup ($25\%$) con Gross Margin ($20\%$) |
| **CR-04** | Punto Equilibrio Normal (RF-06) | $CF = 15000000$, $PV_u = 250000$, $CV_u = 175000$ | $MCU = 75000.00 \implies PE = 200.00\text{ unidades}$ | Cálculo exacto de punto de equilibrio |
| **CR-05** | **Caso Límite 1:** Margen Contribución $= 0$ (RF-06) | $CF = 10000000$, $PV_u = 100000$, $CV_u = 100000$ | `viable = false`, mensaje `"con este precio no hay punto de equilibrio"` | Prevención de división por cero (`Infinity` / `ArithmeticException`) |
| **CR-06** | **Caso Límite 2:** Margen Contribución $< 0$ (RF-06) | $CF = 10000000$, $PV_u = 90000$, $CV_u = 100000$ | `viable = false`, mensaje `"con este precio no hay punto de equilibrio"` | Rechazo semántico cuando se pierde dinero en cada unidad |
| **CR-07** | **Caso Límite 3:** Presupuesto Vacío (RQ-03) | $0\text{ capítulos}$ o $0\text{ ítems}$, $A=8\%, I=3\%, U=5\%$ | $CD = 0.00$, $A=0.00, I=0.00, U=0.00$, $PV = 0.00$, márgenes $= 0.00\%$ | Manejo seguro de colección vacía sin `NullPointerException` ni `NaN` |
| **CR-08** | VPN Flujo Estándar (RF-08) | $r = 10\%\;(0.10)$, flujos $= [-1000, 500, 500, 500]$ | $\text{VPN} = 243.425995 \to 243.43$ | Fórmula cerrada de VPN en `libfinanzas.so` y Java |
| **CR-09** | VPN Tasa Cero (RF-08) | $r = 0\%\;(0.00)$, flujos $= [-1000, 400, 350, 500]$ | $\text{VPN} = 250.00$ | Suma algebraica directa cuando la tasa de descuento es $0\%$ |
| **CR-10** | TIR Flujo Clásico 1 Periodo (RF-08) | flujos $= [-1000, 1100]$, $\text{tol} = 10^{-9}$ | $\text{ código} = 0$, $\text{TIR} = 0.100000\;(10.0000\%)$ | Convergencia exacta en caso analítico elemental |
| **CR-11** | TIR Obra 90 Días (RF-08) | flujos $= [-10000, 3500, 4000, 4500]$, $\text{tol} = 10^{-9}$ | $\text{código} = 0$, $\text{TIR} = 0.092825\;(9.2825\%)$ | Precisión $\ge 4$ decimales en flujo multipaso realista |
| **CR-12** | **Caso Límite 4:** Flujo Sin Cambio de Signo (RQ-03) | flujos $= [-1000, -500, -200]$ (todos negativos) o todos positivos | $\text{código} = -3$ (`FIN_ERR_SIN_CAMBIO_SIGNO`), `convergio = false` | Detección inmediata de inexistencia matemática de TIR |
| **CR-13** | **Caso Límite 5:** Flujo No Convergente / Oscilante (RF-08 / RQ-03) | flujos $= [-1000, 3000, -3500, 1500]$ con `max_iter = 3` y $\text{tol} = 10^{-12}$ (o polinomio sin raíz real $[-1000, 500, -500]$) | $\text{código} = -4$ (`FIN_ERR_NO_CONVERGIO`), `estado = "no convergió"`, `iteraciones = max_iter`, `tir = null` | Garantiza que jamás se devuelva `0` cuando la TIR no converge |
| **CR-14** | Amortización Francés (RF-08) | $\text{Capital} = 10000$, $r = 2\%\;(0.02)$, $n = 3\text{ periodos}$ | Cuota fija $= 3467.55$; Saldo final en $t=3$ es $0.00$ | Llenado en bloque de `double[12]` sin fugas de memoria en C |
| **CR-15** | **Oráculo Mayor RQ-01:** Hoja de Control de 200 Ítems | `docs/control/hoja_control_200_items.csv` (200 ítems en 5 capítulos) | **Desviación $\$0.00$** en los 200 parciales, 5 subtotales, Costo Directo, $A$, $I$, $U$ y Precio de Venta | Prueba integral parametrizada de exactitud monetaria |
