# 4. Delimitación y Especificación del Entregable Nativo (`libfinanzas.so`)

**Proyecto:** Presupuestador de Obra — UCEVA Devs  
**Cursos:** Arquitectura de Computadores (Core Systems) · Programación en Ambiente Web 2  
**Referencia:** UC-80 / HU-42

---

## 1. Propósito y Alcance del Componente Nativo

`libfinanzas.so` es una biblioteca compartida escrita en **C estándar (C11)**, compilada en Linux con `gcc -shared -fPIC -O2 -Wall -Wextra -Werror` e integrada dentro de la imagen Docker de `calculo-service`.

- **Qué resuelve:** Los cálculos financieros intensivos e iterativos del presupuesto de obra (**RF-08**): Valor Presente Neto (**VPN**), Tasa Interna de Retorno (**TIR**) mediante métodos numéricos iterativos, y generación en bloque de la **Tabla de Amortización** (sistema francés de cuota fija). Además, incluye una función de diagnóstico (`fin_ping_suma`) para el *smoke test* / *healthcheck* de JNA en el arranque del contenedor.
- **Qué NO hace:** No realiza conexiones de red, no lee bases de datos, no formatea JSON ni reserva memoria dinámica (`malloc`/`calloc`) que deba ser liberada por Java.

---

## 2. Firmas Exactas en C (`finanzas.h`)

```c
#ifndef FINANZAS_H
#define FINANZAS_H

#ifdef __cplusplus
extern "C" {
#endif

/* Códigos de estado de retorno */
#define FIN_OK                     0
#define FIN_ERR_PARAMETROS_NULOS  -1
#define FIN_ERR_DIMENSION_INVALIDA -2
#define FIN_ERR_SIN_CAMBIO_SIGNO  -3
#define FIN_ERR_NO_CONVERGIO      -4
#define FIN_ERR_TASA_INVALIDA     -5

/**
 * 0. Función de verificación de enlace JNA (Spike / Healthcheck).
 *    Suma dos enteros de 64 bits o dos doubles para comprobar la carga de libfinanzas.so.
 */
double fin_ping_suma(double a, double b);

/**
 * 1. Valor Presente Neto (VPN): fórmula cerrada en una sola pasada.
 *    VPN = sum_{t=0}^{n-1} flujos[t] / (1 + tasa)^t
 *
 * @param tasa   Tasa de descuento por periodo (ej. 0.015 para 1.5%). Debe ser > -1.0.
 * @param flujos Arreglo de flujos de caja desde t=0 hasta t=n-1.
 * @param n      Número total de periodos (elementos en flujos).
 * @return       Valor Presente Neto en punto flotante de doble precisión, o NaN si n <= 0 o tasa <= -1.0.
 */
double fin_vpn(double tasa, const double *flujos, int n);

/**
 * 2. Tasa Interna de Retorno (TIR) por aproximaciones sucesivas
 *    (Newton-Raphson con salvaguarda de Bisección/Secante).
 *    Encuentra la tasa r tal que |VPN(r)| <= tol.
 *
 * @param flujos   Arreglo de flujos de caja (debe tener al menos un flujo < 0 y uno > 0).
 * @param n        Número de elementos en flujos (n >= 2).
 * @param tol      Tolerancia de convergencia (ej. 1e-9).
 * @param max_iter Máximo de iteraciones permitidas (ej. 1000).
 * @param tir_out  [OUT] Puntero donde se escribe la TIR encontrada si converge.
 * @param iter_out [OUT] Puntero donde se escribe SIEMPRE el conteo de iteraciones ejecutadas.
 * @return         0 (FIN_OK) si convergió; código negativo (FIN_ERR_*) si falló o no convergió.
 */
int fin_tir(const double *flujos, int n, double tol, int max_iter,
            double *tir_out, int *iter_out);

/**
 * 3. Tabla de Amortización (Sistema Francés de cuota constante).
 *    Llena el arreglo pre-reservado `cuotas_out` de tamaño (periodos * 4) con:
 *    [cuota, interes, abono, saldo] por cada fila t = 1..periodos.
 *
 * @param capital      Monto financiado (> 0).
 * @param tasa_periodo Tasa de interés por periodo (>= 0).
 * @param periodos     Número de cuotas (> 0).
 * @param cuotas_out   [OUT] Arreglo reservado por Java con capacidad para (periodos * 4) doubles.
 * @return             0 (FIN_OK) si se calculó correctamente; negativo si algún parámetro es inválido.
 */
int fin_amortizacion(double capital, double tasa_periodo, int periodos,
                     double *cuotas_out);

#ifdef __cplusplus
}
#endif

#endif /* FINANZAS_H */
```

---

## 3. Reglas de Memoria y Manejo de Errores en la Frontera JNA

1. **Propiedad de la Memoria (Cero `malloc` huérfanos):**
   - **Quien llama (Java / JNA) reserva toda la memoria de entrada y salida.**
   - En `fin_tir`, Java pasa instancias de `DoubleByReference` (`tirOut`) e `IntByReference` (`iterOut`).
   - En `fin_amortizacion`, Java reserva `double[] cuotasOut = new double[periodos * 4]` y lo pasa a C. La biblioteca en C escribe directamente sobre ese bloque contiguo en una **única llamada** (evitando hacer $N$ llamadas JNA fila por fila) y Java recolecta el arreglo normalmente con su Garbage Collector.
2. **Reporte de Errores desde C:**
   - C no tiene excepciones. Todo error o condición no convergente se reporta mediante **código entero de retorno (`int`)**:
     - `0` (`FIN_OK`): Éxito.
     - `-1` (`FIN_ERR_PARAMETROS_NULOS`): Puntero `NULL` recibido.
     - `-2` (`FIN_ERR_DIMENSION_INVALIDA`): `n < 2` en TIR, `periodos <= 0` o `capital <= 0` en amortización.
     - `-3` (`FIN_ERR_SIN_CAMBIO_SIGNO`): Todos los flujos tienen el mismo signo (no existe TIR real).
     - `-4` (`FIN_ERR_NO_CONVERGIO`): Se alcanzó `max_iter` sin que $|\text{VPN}| \le \text{tol}$. En este caso, `*iter_out` contiene `max_iter` y el backend reporta explícitamente `"NO_CONVERGIO"` con las iteraciones alcanzadas (nunca devuelve `0.0%` haciéndolo pasar por respuesta válida).

---

## 4. Cronograma de Entrega del Componente Nativo

| Hito / Entregable | Fecha Comprometida | Responsable | Estado en Sprint 1 |
| :--- | :---: | :--- | :---: |
| **Hito 1:** Especificación de `finanzas.h`, contrato JNA congelado y casos de referencia (Oráculo) | **Semana 1 (Sprint 1)** | Frente Core Systems + Web 2 | **COMPLETADO** |
| **Hito 2:** Spike en Docker multietapa (`gcc` $\to$ `jre` sin compilador) + implementación funcional de `fin_vpn`, `fin_tir` y `fin_amortizacion` con suite en C (`make test`) | **Semana 1–2 (Sprint 1)** | Frente Core Systems | **COMPLETADO** |
| **Hito 3:** Integración completa en `calculo-service` con conmutador de respaldo Java (`calculo.engine.mode=native\|java`) | **Semana 2 (Sprint 1)** | Frente Web 2 | **COMPLETADO** |
| **Hito 4:** Medición de las 4 cifras (Benchmark C vs. Java 120 periodos $\times$ 1000 corridas, overhead JNA 1x vs. iteración, tamaño de imagen y tiempo de arranque) | **Semana 3–4 (Sprint 2)** | Frente Core Systems | Preparado |

---

## 5. Verificación Aislada (Sin Levantar el Sistema Completo — RQ-03)

El componente nativo se verifica en menos de **5 segundos** sin necesidad de levantar Kubernetes, Docker Swarm, PostgreSQL ni Angular:
1. **Nivel C puro (`native-lib/`):**
   - Ejecutando `make test` dentro de `native-lib/` (o mediante contenedor efímero `docker build --target native-test native-lib/`).
   - Compila `finanzas.c` junto con el arnés de pruebas `test_finanzas.c` y ejecuta los casos de referencia verificando VPN, TIR convergente, flujo sin cambio de signo (`-3`), flujo no convergente (`-4`) y tabla de amortización.
2. **Nivel JNA + Respaldo Java (`services/calculo-service/`):**
   - Ejecutando `mvn test` en `calculo-service`, que corre la suite parametrizada contra el motor de cálculo y contra la hoja de control de 200 ítems.

---

## 6. Plan de Contingencia (¿Qué ocurre si el componente nativo falla o no está disponible?)

Para garantizar que la evaluación de **Programación en Ambiente Web 2** nunca quede bloqueada por problemas de arquitectura del sistema operativo anfitrión (por ejemplo, un desarrollador corriendo pruebas locales en Windows sin GCC instalado):

- `calculo-service` implementa el patrón **Strategy (`MotorFinancieroStrategy`)** con dos implementaciones idénticas en contrato y casos de prueba:
  1. `JnaFinanzasStrategy`: Invoca a `libfinanzas.so` mediante JNA.
  2. `JavaPuroFinanzasStrategy`: Implementa exactamente las mismas ecuaciones de VPN, TIR (Newton-Raphson + Bisección) y Amortización en Java puro (que además es **obligatoria como línea base** para la medición de desempeño C vs. Java).
- La selección se controla por configuración (`calculo.finanzas.modo=AUTO | NATIVE | JAVA`):
  - En contenedor de producción (`NATIVE`), exige `libfinanzas.so`.
  - En modo `AUTO`, intenta cargar `libfinanzas.so` y, si el sistema operativo local no dispone del `.so`, conmuta de forma segura a `JavaPuroFinanzasStrategy` dejando constancia en el log y en el campo `motorUtilizado` de la respuesta JSON.
