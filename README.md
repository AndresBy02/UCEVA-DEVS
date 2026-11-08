# Presupuestador de Obra — UCEVA Devs (Sprint 1: Base Arquitectónica y Entorno)

Repositorio oficial del proyecto **Presupuestador de Obra** para las asignaturas **Programación en Ambiente Web 2** y **Arquitectura de Computadores (Core Systems)**.

**Equipo UCEVA Devs:**
- Andrés Felipe Vargas Ceballos
- Esteban Lozano Jaramillo
- Juan David González Ospina
- Diego Alejandro Laverde Restrepo
- Luis Alejandro Pino Bellorín

---

## 1. Estructura del Repositorio

```text
├── docs/
│   ├── arquitectura/
│   │   ├── 01-drivers-y-escenarios-calidad.md          # UC-69 HU-38: Drivers + 5 Escenarios (Len Bass)
│   │   ├── 02-descomposicion-microservicios-interfaces.md # UC-72 HU-39: Corte en 3 servicios + Matriz Auth
│   │   ├── 03-representacion-dinero-y-redondeo.md      # UC-62 HU-34: BigDecimal + Protocolo RQ-01 + Aduana JNA
│   │   ├── 04-delimitacion-entregable-nativo.md        # UC-80 HU-42: Alcance, firmas C, memoria y plan B
│   │   ├── 05-contrato-calculo-service-y-oraculo.md    # UC-81 HU-43: Contrato REST + JNA + 15 Casos Oráculo
│   │   └── 06-bitacora-decisiones.md                   # UC-76 HU-40: Bitácora con alternativas descartadas
│   └── control/
│       ├── hoja_control_200_items.csv                  # UC-56 HU-32: Los 200 ítems de obra en 5 capítulos
│       ├── resumen_esperado_hoja_control.json          # Totales exactos de control ($0.00 desviación)
│       ├── generar_hoja_control.py                     # Generador determinista Decimal (ROUND_HALF_UP)
│       └── README-hoja-control.md                      # Regla de redondeo declarada por escrito
├── native-lib/                                         # Frente Arquitectura de Computadores (C11)
│   ├── finanzas.h                                      # Firmas C congeladas (VPN, TIR, Amortización, Spike)
│   ├── finanzas.c                                      # Implementación numérica en C
│   ├── test_finanzas.c                                 # Suite aislada en C (RQ-03)
│   └── Makefile                                        # Compilación gcc -shared -fPIC -O2 y target test
├── services/
│   ├── calculo-service/                                # Motor exacto BigDecimal + Puente JNA a libfinanzas.so
│   │   ├── Dockerfile                                  # Multietapa: gcc:13 -> maven:21 -> temurin:21-jre
│   │   ├── pom.xml
│   │   └── src/                                        # Endpoints congelados y suite de 200 ítems (RQ-01/RQ-03)
│   ├── insumo-service/                                 # Base para el microservicio de catálogo (8081)
│   └── presupuesto-service/                            # Base para el microservicio de presupuestos (8082)
├── infra/
│   ├── keycloak/
│   │   └── presupuesto-realm.json                      # Realm exportado con roles presupuestador y revisor (RA-01)
│   └── postgres/
│       └── init-multiple-dbs.sql                       # Esquemas NUMERIC(19,3) y NUMERIC(19,2) para PostgreSQL
├── docker-compose.yml                                  # Orquestación con límites explícitos de CPU y RAM
└── .env.example
```

---

## 2. Comandos Rápidos para el Equipo (UC-12 / HU-00)

### A. Ejecutar la Suite Aislada de Pruebas y Conciliación de 200 Ítems (RQ-01 y RQ-03)
Sin necesidad de levantar Docker ni bases de datos, cualquier integrante puede verificar los 15 casos de referencia y los 200 ítems de la hoja de control en menos de 10 segundos:

```bash
mvn -f services/calculo-service/pom.xml test
```

### B. Construir y Verificar el Contenedor Nativo (`libfinanzas.so` + JNA — HU-44)
El `Dockerfile` multietapa de `calculo-service` compila `libfinanzas.so` con `gcc`, ejecuta las pruebas en C (`make test`) durante el build, empaqueta el JAR de Spring Boot y copia únicamente el `.so` y el `.jar` a una imagen limpia `eclipse-temurin:21-jre-jammy` (sin compilador):

```bash
docker build -f services/calculo-service/Dockerfile -t uceva/calculo-service:sprint1 .
```

### C. Levantar el Entorno Orquestado Completo (PostgreSQL + Keycloak + `calculo-service`)
```bash
docker compose up -d
```

- **Keycloak (`presupuesto-realm`):** `http://localhost:8080`
  - Usuario `presupuestador`: `presupuestador1` / `presupuestador123`
  - Usuario `revisor`: `revisor1` / `revisor123`
- **Spike JNA en Contenedor (`calculo-service`):**
  ```bash
  curl "http://localhost:8083/api/v1/calculos/spike-jna?a=25.5&b=14.5"
  ```
- **Evaluación Financiera en C (`libfinanzas.so` vía JNA):**
  ```bash
  curl -X POST "http://localhost:8083/api/v1/calculos/evaluacion-financiera" \
    -H "Content-Type: application/json" \
    -d '{"tasaDescuento":0.015,"flujosCaja":[-10000,3500,4000,4500],"toleranciaTir":1e-9,"maxIteracionesTir":1000,"financiacion":{"capital":10000,"tasaPeriodo":0.02,"periodos":3}}'
  ```
