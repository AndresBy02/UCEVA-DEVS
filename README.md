# Presupuestador de Obra — UCEVA Devs

Repositorio oficial del proyecto **Presupuestador de Obra** para las asignaturas:
- **Programación en Ambiente Web 2** (Frontend Angular, 3 Microservicios Spring Boot, Modelo de Datos, Keycloak OAuth2/OIDC, Escenarios de Calidad y Bitácora).
- **Arquitectura de Computadores — Core Systems** (Biblioteca nativa `libfinanzas.so` en C, interfaz JNA, contenedor multietapa y despliegue orquestado).

**Integrantes (Equipo UCEVA Devs):**
- Andrés Felipe Vargas Ceballos
- Esteban Lozano Jaramillo
- Juan David González Ospina
- Diego Alejandro Laverde Restrepo
- Luis Alejandro Pino Bellorín

---

## 1. ¿Qué necesito instalar en mi computador? (Dos formas de trabajar)

### Opción A: Solo levantar el entorno con Docker (La más rápida — No requiere instalar Java, ni Maven, ni GCC)
Si solo quieres levantar el sistema completo, probar los endpoints, usar PostgreSQL, Keycloak y ver `libfinanzas.so` funcionando en contenedor, **solo necesitas tener instalado**:
1. **Git**
2. **Docker Desktop** (con Docker Compose v2 activado)

*(El `Dockerfile` multietapa descarga internamente su propio `gcc:13`, su propio `maven:3.9.9-eclipse-temurin-21` y su propio runtime `eclipse-temurin:21-jre`, por lo que compila el código C y el código Java dentro del contenedor sin ensuciar tu máquina).*

---

### Opción B: Desarrollo local en tu IDE (IntelliJ IDEA / VS Code / Eclipse)
Si vas a programar en los microservicios, en la biblioteca en C o en el frontend Angular desde tu sistema operativo, instala estas versiones:

| Herramienta | Versión Requerida / Recomendada | Notas para el Equipo |
| :--- | :--- | :--- |
| **JDK (Java)** | **Java 21 LTS** (compatible con JDK 21 a 24) | El `pom.xml` compila con `<java.version>21</java.version>`. |
| **Apache Maven** | **3.9.x** o superior | Para ejecutar `mvn test` y `mvn clean package`. |
| **Docker Desktop** | **24.x+** (Compose v2) | Obligatorio para levantar PostgreSQL, Keycloak y probar `libfinanzas.so` en Linux. |
| **Node.js** *(para el frente Angular)* | **v20 LTS** o **v22 / v24** | Para el proyecto de Angular (Standalone + Signals). |
| **GCC + Make** *(solo frente C)* | **GCC 11+** (Linux / WSL2) | Si estás en Windows, no necesitas instalar GCC local: el Dockerfile compila y corre `make test` automáticamente, y en Windows Java usa el modo `AUTO` (respaldo Java) para que nunca te bloquees. |

---

## 2. Guía Paso a Paso: Cómo levantar todo desde cero con Docker

Sigue estos pasos tras clonar el repositorio en una máquina limpia:

### Paso 1: Clonar el repositorio
```bash
git clone https://github.com/AndresBy02/UCEVA-DEVS.git
cd UCEVA-DEVS
```

### Paso 2: Levantar todo el sistema con un solo comando
Ejecuta en la raíz del proyecto (PowerShell, CMD o terminal Linux/Mac):
```bash
docker compose up -d --build
```
> **¿Qué hace este comando automáticamente?**
> 1. Compila en Linux la biblioteca en C `native-lib/finanzas.c` $\to$ `libfinanzas.so` y ejecuta la suite de pruebas en C (`make test`).
> 2. Compila `insumo-service`, `presupuesto-service` y `calculo-service` y levanta los tres servicios en los puertos `8081`, `8082` y `8083`.
> 3. Levanta **PostgreSQL 16** en el puerto `5432` y ejecuta `infra/postgres/init-multiple-dbs.sql`, creando automáticamente las bases de datos `insumo_db` y `presupuesto_db` con columnas `NUMERIC(19,3)` y `NUMERIC(19,2)`.
> 4. Levanta **Keycloak 26** en el puerto `8080` e importa automáticamente el realm `presupuesto-realm` (`infra/keycloak/presupuesto-realm.json`) con los roles y usuarios de prueba listos.
> 5. Levanta **`insumo-service`** y **`presupuesto-service`** conectados a sus bases independientes, además de **`calculo-service`** en el puerto `8083` conectado a `libfinanzas.so` vía JNA.

### Paso 3: Verificar que los contenedores están corriendo
```bash
docker compose ps
```
Deberías ver `presupuesto-postgres`, `presupuesto-keycloak`, `presupuesto-insumo-service`, `presupuesto-presupuesto-service` y `presupuesto-calculo-service` en estado `Up`.

---

## 3. Cómo probar que todo funciona (Comandos de Verificación)

### 3.1. Probar el Spike de JNA (`libfinanzas.so` dentro del contenedor — HU-44)
En **PowerShell**:
```powershell
Invoke-RestMethod -Uri "http://localhost:8083/api/v1/calculos/spike-jna?a=25.5&b=14.5" | ConvertTo-Json
```
O con **curl** (Linux / Git Bash):
```bash
curl "http://localhost:8083/api/v1/calculos/spike-jna?a=25.5&b=14.5"
```
**Respuesta esperada:**
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

### 3.2. Probar el cálculo de VPN, TIR y Amortización en C (`RF-08`)
En **PowerShell**:
```powershell
$body = '{"tasaDescuento":0.015,"flujosCaja":[-10000,3500,4000,4500],"toleranciaTir":1e-9,"maxIteracionesTir":1000,"financiacion":{"capital":10000,"tasaPeriodo":0.02,"periodos":3}}'
Invoke-RestMethod -Method Post -Uri "http://localhost:8083/api/v1/calculos/evaluacion-financiera" -ContentType "application/json" -Body $body | ConvertTo-Json -Depth 5
```

### 3.3. Ejecutar la Suite Aislada y Conciliación contra la Hoja de Control de 200 Ítems (`RQ-01` y `RQ-03`)
Si tienes Maven y JDK instalados localmente, puedes correr todas las pruebas (los 15 casos de referencia + los 200 ítems de `docs/control/hoja_control_200_items.csv` con desviación de `$0.00`) sin depender de Docker:
```bash
mvn -f services/calculo-service/pom.xml test
```
Si **no** tienes Maven instalado y quieres correr la prueba en C usando solo Docker:
```bash
docker build --target native-builder -f services/calculo-service/Dockerfile .
```

### 3.4. Apagar el entorno
```bash
docker compose down
```
*(Si deseas borrar también los volúmenes de la base de datos para reiniciar desde cero: `docker compose down -v`).*

### 3.5. Consultar y editar el catálogo de insumos (HU-02)
Los endpoints requieren un JWT válido del realm `presupuesto-realm`. Los roles `presupuestador` y `revisor` pueden consultar; solo `presupuestador` puede editar. El código del insumo es inmutable. El precio usa `BigDecimal`/`NUMERIC(19,3)`: se rechazan precios negativos o con más de tres decimales. Cada cambio de precio se registra junto con el anterior en la misma transacción; editar otros campos o enviar el mismo precio no genera historial. El catálogo no administra existencias.

En **PowerShell**, obtiene un token de desarrollo, reemplaza `$id` por un UUID existente y consulta el catálogo/detalle/historial:
```powershell
$tokenResponse = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/realms/presupuesto-realm/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body @{ client_id = "presupuesto-frontend"; grant_type = "password"; username = "presupuestador1"; password = "presupuestador123" }
$token = $tokenResponse.access_token
$headers = @{ Authorization = ('Bearer' + [char]32 + $token) }
Invoke-RestMethod -Uri "http://localhost:8081/api/insumos" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:8081/api/insumos/$id" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:8081/api/insumos/$id/historial" -Headers $headers
```

La edición conserva el precio vigente y agrega el historial si el precio cambió:
```powershell
$body = '{"descripcion":"Cemento gris","unidadMedida":"BULTO","precioUnitario":34500.000,"activo":true}'
Invoke-RestMethod -Method Put -Uri "http://localhost:8081/api/insumos/$id" `
  -Headers $headers -ContentType "application/json" -Body $body | ConvertTo-Json -Depth 6
```

La respuesta del PUT/detalle incluye `precioUnitario`, `historialPrecios` (más reciente primero) y `presupuestos`. Por ejemplo:
```json
{
  "codigo": "CEM-001",
  "precioUnitario": 34500.000,
  "historialPrecios": [
    { "precioAnterior": 33000.000, "precioNuevo": 34500.000, "modificadoPor": "presupuestador1 (sub)", "fechaCambio": "2026-11-11T15:00:00Z" }
  ],
  "presupuestos": [
    { "id": "uuid", "codigo": "PRE-001", "nombre": "Ampliación", "estado": "BORRADOR" }
  ]
}
```

Sin autenticación se responde `401`; un `revisor` que intenta editar recibe `403`; un insumo inexistente responde `404`; datos no válidos responden `400`; si `presupuesto-service` no está disponible, el detalle o la respuesta del PUT devuelve `502` en lugar de omitir asociaciones. Ejecuta las pruebas de integración PostgreSQL 16 con Docker activo:
```powershell
mvn -f services/insumo-service/pom.xml clean test
mvn -f services/presupuesto-service/pom.xml clean test
```
Para desarrollo local activa `SPRING_PROFILES_ACTIVE=local` y establece `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD`. Docker Compose usa el perfil `docker`; las contraseñas se proporcionan mediante variables de entorno.

---

## 4. Accesos, Puertos y Credenciales de Desarrollo

| Servicio | URL / Puerto Local | Credenciales de Prueba (Configuradas en Código) |
| :--- | :--- | :--- |
| **Keycloak (Consola Admin)** | `http://localhost:8080` | Usuario: `admin` · Contraseña: `admin123` |
| **Keycloak Realm (`presupuesto-realm`)** | Rol `presupuestador` | Usuario: `presupuestador1` · Contraseña: `presupuestador123` |
| **Keycloak Realm (`presupuesto-realm`)** | Rol `revisor` | Usuario: `revisor1` · Contraseña: `revisor123` |
| **PostgreSQL (`insumo_db` y `presupuesto_db`)** | `localhost:5432` | Usuario: `uceva` · Contraseña: `uceva_secret_2026` |
| **`insumo-service`** *(Catálogo)* | `http://localhost:8081` | `GET /api/insumos`, `GET /api/insumos/{id}`, `GET /api/insumos/{id}/historial`, `PUT /api/insumos/{id}` |
| **`presupuesto-service`** *(Presupuestos)* | `http://localhost:8082` | Consulta interna `GET /api/v1/presupuestos?insumoId={id}` |
| **`calculo-service`** *(Motor Exacto + JNA)* | `http://localhost:8083` | Activo en Docker (`GET /api/v1/calculos/spike-jna`) |

---

## 5. Mapa de Entregables del Sprint 1 en el Repositorio

```text
├── docs/
│   ├── arquitectura/
│   │   ├── 01-drivers-y-escenarios-calidad.md             # UC-69 HU-38: Drivers + 5 Escenarios (Len Bass)
│   │   ├── 02-descomposicion-microservicios-interfaces.md # UC-72 HU-39: Corte en 3 servicios + Matriz Auth
│   │   ├── 03-representacion-dinero-y-redondeo.md         # UC-62 HU-34: BigDecimal + Protocolo RQ-01 + Aduana JNA
│   │   ├── 04-delimitacion-entregable-nativo.md           # UC-80 HU-42: Alcance, firmas C, memoria y fallback Java
│   │   ├── 05-contrato-calculo-service-y-oraculo.md       # UC-81 HU-43: Contrato REST + JNA + 15 Casos Oráculo
│   │   └── 06-bitacora-decisiones.md                      # UC-76 HU-40: Bitácora con alternativas descartadas
│   └── control/
│       ├── hoja_control_200_items.csv                     # UC-56 HU-32: Los 200 ítems de obra en 5 capítulos
│       ├── resumen_esperado_hoja_control.json             # Subtotales, Costo Directo, AIU y Precio de Venta
│       ├── generar_hoja_control.py                        # Script reproducible con Decimal (ROUND_HALF_UP)
│       └── README-hoja-control.md                         # Regla de redondeo declarada por escrito
├── native-lib/                                            # Frente Arquitectura de Computadores (C11)
│   ├── finanzas.h                                         # Firmas C congeladas (VPN, TIR, Amortización, Spike)
│   ├── finanzas.c                                         # Implementación numérica en C
│   ├── test_finanzas.c                                    # Suite aislada en C (RQ-03)
│   └── Makefile                                           # Compilación gcc -shared -fPIC -O2 y target test
├── services/
│   ├── calculo-service/                                   # Motor exacto BigDecimal + Puente JNA a libfinanzas.so
│   │   ├── Dockerfile                                     # Multietapa: gcc:13 -> maven:21 -> temurin:21-jre
│   │   ├── pom.xml
│   │   └── src/                                           # Endpoints congelados y prueba de 200 ítems (RQ-01/RQ-03)
│   ├── insumo-service/                                    # Base Maven para el microservicio de catálogo (8081)
│   └── presupuesto-service/                               # Base Maven para el microservicio de presupuestos (8082)
├── infra/
│   ├── keycloak/presupuesto-realm.json                    # Realm exportado con roles y usuarios de prueba (RA-01)
│   └── postgres/init-multiple-dbs.sql                     # Esquemas NUMERIC(19,3) y NUMERIC(19,2) para PostgreSQL
├── docker-compose.yml                                     # Orquestación con límites explícitos de CPU y RAM
└── .env.example
```
