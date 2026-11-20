# Evidencia RQ-05 · Benchmark Real de Desempeño y Costo Nativo JNA en Docker (`UC-86`)

- **Escenario:** `RQ-05 · Desempeño / Frontera Nativa JNA`.
- **Ambiente medido:** Contenedores Docker locales en ejecución (`docker compose`), con cuotas estrictas aplicadas al servicio de cálculo (`0.50 CPU` = `500000000 nanoCpus` y `512 MiB` RAM = `536870912 bytes`).
- **Fecha de medición:** `2026-11-18`.
- **Integración nativa verificada:** `GET /api/v1/calculos/spike-jna` $\rightarrow$ `bibliotecaCargada=true`, `nombreBiblioteca="libfinanzas.so"`, `arquitecturaContenedor="amd64 / Linux"`, `resultadoNativoC=40.0`.

---

## 1. Costo de Cruce de Frontera JNA y CPU (`GET /api/v1/calculos/benchmark?periodos=120&corridas=1000`)

Se ejecutó 1 corrida de calentamiento (*warmup* JIT) seguida de 3 corridas medidas consecutivas directamente contra `presupuesto-calculo-service` dentro del contenedor (`motorActivo="NATIVE_C_LIBFINANZAS"`, `79` iteraciones de TIR por corrida, resultado convergente `tirResultado=0.010844`):

| Corrida | Java Puro (`tiempoJavaMsExacto`) | C Nativo 1 llamada/corrida (`fin_tir`, `1,000` llamadas JNA) | C Nativo 1 llamada/iteración (`fin_vpn`, `75,000` llamadas JNA) | Factor de sobrecosto JNA (`porIteración / porCorrida`) |
| :---: | :---: | :---: | :---: | :---: |
| **Warmup (JIT)** | `196.083 ms` | `297.667 ms` | `618.819 ms` | `2.08x` |
| **Corrida 1** | `49.094 ms` | `91.762 ms` | `216.817 ms` | **`2.36x`** |
| **Corrida 2** | `24.895 ms` | `84.009 ms` | `281.771 ms` | **`3.35x`** |
| **Corrida 3** | `91.114 ms` | `97.262 ms` | `230.088 ms` | **`2.37x`** |
| **Mediana (Corridas 1–3)** | **`49.094 ms`** | **`91.762 ms`** | **`230.088 ms`** | **`2.37x`** |

### Hallazgo Técnico #1 (Honesto y Empírico)
1. **Regla de granularidad (validación de `D-07`):** Mantener el ciclo completo de Newton-Raphson/bisección dentro de C (`fin_tir`: `1,000` llamadas JNA) evita `75,000` cruces del puente JNI/JNA y reduce la latencia total **`2.36x`–`3.35x`** frente a evaluar `fin_vpn` en C iteración por iteración desde Java.
2. **Realidad de vectores pequeños (`N=120` doubles $\approx 960\text{ B}$):** Para arreglos de 120 períodos, el costo de *marshalling* FFI de JNA por llamada supera el tiempo de ejecución del ciclo optimizado por HotSpot JIT en Java (`~91.8 ms` en C vía JNA vs `~49.1 ms` de mediana en Java puro). Esto confirma que, en este tamaño de problema, la biblioteca `libfinanzas.so` se justifica por interoperabilidad académica (`Core Systems`), aislamiento numérico reutilizable (**RQ-02**) y verificación cruzada (**RQ-03**), no por ganancia neta de micro-latencia frente a JIT en vectores cortos.

---

## 2. Huella de Binario Nativo e Imagen Docker (`NATIVE` vs `JAVA`-only)

Se comparó la imagen oficial multietapa `uceva/calculo-service:sprint1` (`NATIVE`, construida con etapa `gcc:13-bookworm` $\to$ etapa final `eclipse-temurin:21-jre-jammy` sin compilador ni fuentes) contra una imagen sonda idéntica `JAVA`-only sin `/usr/lib/libfinanzas.so` ni `ldconfig`:

| Métrica | Valor Medido | Detalle / Comando |
| :--- | :---: | :--- |
| **Peso de `/usr/lib/libfinanzas.so` en runtime** | **`16 KB`** | `docker exec presupuesto-calculo-service ls -lh /usr/lib/libfinanzas.so` (`16K`) |
| **Imagen `uceva/calculo-service:sprint1` (`NATIVE`)** | **`127,880,047 bytes` (`121.96 MiB`)** | `docker image inspect --format '{{.Size}}'` (capa comprimida/OCI) · `docker images` muestra `465MB` descomprimidos |
| **Imagen sonda `JAVA`-only equivalente** | **`127,870,857 bytes` (`121.95 MiB`)** | Misma base `eclipse-temurin:21-jre-jammy` y mismo `/app/app.jar`, sin `.so` ni `ldconfig` |
| **Incremento neto de imagen por soporte C** | **`+9,190 bytes` (`+8.97 KiB` / `0.007%`)** | Gracias al build multietapa (**D-10**), `gcc:13-bookworm` no entra al contenedor de ejecución |

---

## 3. Tiempo de Arranque en Frío en Contenedor (`0.50 CPU`, `512 MiB` RAM)

Medición de tiempo real *wall-clock* desde `docker run` hasta HTTP `200 UP` en `GET /actuator/health` y tiempo interno reportado por Spring Boot (`Started CalculoServiceApplication`), alternando 2 arranques en frío por modo en la red Docker Compose:

| Corrida | Arranque HTTP `UP` (Wall) | Log Spring Boot | JVM activa | Observación |
| :--- | :---: | :---: | :---: | :--- |
| **`NATIVE` #1** | `16,888 ms` (`16.89 s`) | `13.19 s` | `15.237 s` | Carga `libfinanzas.so` vía JNA |
| **`JAVA` #1** | `16,729 ms` (`16.73 s`) | `13.203 s` | `15.303 s` | `CALCULO_FINANZAS_MODO=JAVA` |
| **`NATIVE` #2** | `16,677 ms` (`16.68 s`) | `12.911 s` | `15.171 s` | Carga `libfinanzas.so` vía JNA |
| **`JAVA` #2** | `18,726 ms` (`18.73 s`) | `15.314 s` | `17.346 s` | `CALCULO_FINANZAS_MODO=JAVA` |

**Conclusión de arranque:** En ambos modos el arranque bajo `0.50 CPU` oscila entre `~16.7 s` y `~18.7 s` (`~13–15 s` internos de Spring Boot). La carga dinámica de `libfinanzas.so` (`16 KB`) no añade penalización perceptible de arranque.

---

## 4. Huella de Memoria RSS del Contenedor (`512 MiB` límite)

Medición con `docker stats --no-stream` inmediatamente tras completar el arranque de Spring Boot en las 4 sondas:

| Contenedor / Modo | Memoria RSS Medida (`MemUsage`) | % sobre cuota `512 MiB` |
| :--- | :---: | :---: |
| **`NATIVE` #1** | `146.6 MiB / 512 MiB` | `28.6%` |
| **`NATIVE` #2** | `159.0 MiB / 512 MiB` | `31.1%` |
| **`JAVA` #1** | `149.3 MiB / 512 MiB` | `29.2%` |
| **`JAVA` #2** | `149.4 MiB / 512 MiB` | `29.2%` |

**Conclusión de memoria:** El consumo estable de un contenedor recién iniciado ronda los **`147–159 MiB`**, dentro del rango normal de la JVM para Spring Boot + Tomcat, sin incremento significativo atribuible a `libfinanzas.so` ni JNA.
