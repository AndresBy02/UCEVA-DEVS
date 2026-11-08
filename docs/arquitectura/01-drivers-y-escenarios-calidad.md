# 1. Drivers Arquitectónicos y Escenarios de Atributos de Calidad (ADD Paso 1 y 2)

**Proyecto:** Presupuestador de Obra — UCEVA Devs  
**Cursos:** Programación en Ambiente Web 2 · Arquitectura de Computadores (Core Systems)  
**Referencia:** UC-69 / HU-38

---

## 1. Propósito Primario e Impulsores de Negocio (Business Drivers)

El sistema resuelve la elaboración, versionado y evaluación financiera de presupuestos de obra civil, reemplazando hojas de cálculo propensas a errores de referencia, pérdida de historial de precios y redondeos silenciosos.

1. **Exactitud monetaria absoluta ("Ni un peso de diferencia"):** El presupuesto compite contra la revisión con calculadora del cliente y una hoja de control. Una desviación de `$1 COP` constituye un defecto funcional e invalida la cotización.
2. **Inmutabilidad histórica de precios cotizados:** Actualizar el precio de un insumo en el catálogo general jamás debe alterar retroactivamente un presupuesto ya elaborado o enviado al cliente.
3. **Evaluación financiera inmediata en código nativo:** Responder en menos de un segundo si un proyecto financiado con pago a 90 días sigue siendo rentable mediante el cálculo de Valor Presente Neto (VPN), Tasa Interna de Retorno (TIR) y tabla de amortización ejecutados en una biblioteca nativa en C (`libfinanzas.so`) vía JNA.
4. **Desarrollo paralelo desacoplado entre frentes:** El frente de Arquitectura de Computadores (`libfinanzas.so` y despliegue) y el frente de Web 2 (Angular + 3 microservicios Spring Boot + Keycloak) deben avanzar sin bloquearse mutuamente gracias a contratos congelados y mecanismos de respaldo (*fallback* en Java).

---

## 2. Restricciones Arquitectónicas (Constraints)

| ID | Restricción | Motivo / Impacto Arquitectónico |
| :--- | :--- | :--- |
| **RES-01** | **Frontend Angular (Standalone + Signals)** | El frontend actúa exclusivamente como capa de presentación y captura. **No calcula ningún valor del presupuesto** para evitar doble implementación y desincronización de redondeos. |
| **RES-02** | **Tres Microservicios en Spring Boot** | Separación mínima en `insumo-service`, `presupuesto-service` y `calculo-service`, cada uno con responsabilidad única y persistencia aislada donde aplique. |
| **RES-03** | **Componente Nativo Obligatorio (`libfinanzas.so`)** | Escrito en C (compilado con `gcc -shared -fPIC -O2`) e invocado desde `calculo-service` usando **JNA** (Java Native Access) dentro de un contenedor Linux. |
| **RES-04** | **Representación Exacta del Dinero** | Prohibido el uso de `double` o `float` para almacenar o sumar dinero en el dominio de presupuestos. Uso obligatorio de `BigDecimal` con escala explícita y `RoundingMode.HALF_UP`. |
| **RES-05** | **Identidad Delegada en Keycloak (OAuth 2.0 / OIDC)** | Ningún endpoint queda abierto. Flujo Authorization Code + PKCE en Angular y validación autónoma de JWT (Resource Server) en cada microservicio con roles `presupuestador` y `revisor`. |
| **RES-06** | **Orquestación en un Solo Comando** | Todo el sistema (bases de datos, Keycloak con importación de realm, 3 microservicios y frontend) debe levantarse desde cero en una máquina limpia mediante Docker Compose / Docker Swarm con límites de CPU y memoria declarados. |
| **RES-07** | **Moneda y Alcance Cerrado** | Peso colombiano único (`COP`), sin inventario de bodega, sin facturación electrónica DIAN, sin contabilidad ni cronogramas Gantt. |

---

## 3. Escenarios de Atributos de Calidad (Taxonomía de Len Bass)

Los escenarios siguen estrictamente la estructura de seis partes de *Software Architecture in Practice* (Len Bass et al.) y usan los nombres cerrados de atributos de calidad: **Testabilidad**, **Modificabilidad**, **Seguridad** y **Desempeño**.

---

### RQ-01 · Testabilidad (El Valor de Control / Oráculo Monetario)

> **Propósito:** Demostrar contra un oráculo externo (hoja de control de 200 ítems) que la cadena completa de cálculo no tiene desviaciones de redondeo ni de precisión.

| Parte | Descripción |
| :--- | :--- |
| **Fuente** | Una persona del área de costos (o suite automatizada de integración en su nombre) que arma la cotización de un trabajo real. |
| **Estímulo** | Cierra un presupuesto de **200 ítems** repartidos en **5 capítulos**, con precios unitarios de **3 decimales** y cantidades fraccionarias de **3 decimales**, aplicando porcentajes de Administración ($A=8\%$), Imprevistos ($I=3\%$) y Utilidad ($U=5\%$), y solicita el costo directo, el AIU discriminado y el precio de venta. |
| **Artefacto** | La cadena completa de cálculo: `presupuesto-service`, `calculo-service` y `libfinanzas.so`, más el formateo de presentación en Angular. |
| **Entorno** | Operación normal, catálogo cargado, sin fallas de red ni de servicios. Los mismos 200 ítems existen en la hoja de control (`docs/control/hoja_control_200_items.csv`) preparada antes de escribir el código, con su regla de redondeo declarada por escrito. |
| **Respuesta** | El sistema devuelve el valor parcial de cada ítem, el subtotal de cada capítulo, el costo directo total, los tres componentes del AIU y el precio de venta, aplicando la regla de redondeo definida (`RoundingMode.HALF_UP`) en los puntos exactos establecidos en el protocolo y no en cada capa por su cuenta. |
| **Medida** | **Diferencia de $\$0\text{ COP}$ (`0.00`)** frente a la hoja de control en el total, en cada uno de los 5 subtotales de capítulo y en los **200 de 200 valores parciales** (desviación cero por ítem y cero pesos de desviación acumulada). Comprobado mediante una prueba parametrizada que lee el CSV de la hoja de control en cada construcción (`mvn test`). |

---

### RQ-02 · Modificabilidad (Aislamiento de la Frontera Nativa)

> **Propósito:** Garantizar que el componente nativo en C evolucione de forma independiente sin propagar cambios al contrato REST ni al frontend Angular.

| Parte | Descripción |
| :--- | :--- |
| **Fuente** | El frente de trabajo de la biblioteca nativa (Arquitectura de Computadores), con acceso únicamente al código en C y su adaptador interno. |
| **Estímulo** | Se agrega una cuarta función financiera a `libfinanzas.so` (por ejemplo, Tasa Interna de Retorno Modificada — TIRM) y se cambia el criterio de parada / método numérico de la TIR existente. |
| **Artefacto** | `libfinanzas.so`, su interfaz JNA (`FinanzasLibrary.java`) y `calculo-service`. |
| **Entorno** | Tiempo de desarrollo, con el contrato REST de `calculo-service` ya congelado y publicado en el repositorio. |
| **Respuesta** | El cambio se resuelve recompilando la biblioteca compartida y reconstruyendo la imagen del contenedor de `calculo-service`. El contrato REST existente no cambia y el proyecto Angular no se toca. |
| **Medida** | Como máximo **2 módulos tocados** (código C de `libfinanzas.so` e interfaz/servicio en `calculo-service`), **0 cambios rompientes** en el contrato publicado de la API, **0 archivos `.ts` o `.html` modificados** en Angular (verificado con `git diff --stat`), y el contenedor vuelve a estar arriba en **menos de 10 minutos** desde el commit. |

---

### RQ-03 · Testabilidad (El Arnés / Suite Aislada en Menos de 60 Segundos)

> **Propósito:** Verificar a demanda tanto la biblioteca en C como los cálculos de negocio en Java sin necesidad de levantar Docker Swarm/Kubernetes ni abrir el navegador.

| Parte | Descripción |
| :--- | :--- |
| **Fuente** | Cualquier integrante del equipo de desarrollo, el pipeline de CI o el docente durante la sustentación. |
| **Estímulo** | Ejecuta un único comando de verificación tras clonar el repositorio o realizar un cambio en las fórmulas financieras o de presupuesto. |
| **Artefacto** | La suite de pruebas aisladas: el ejecutable de prueba en C compilado contra `libfinanzas.so` y la suite de pruebas unitarias/parametrizadas de `calculo-service`. |
| **Entorno** | Equipo de desarrollo con el repositorio recién clonado, sin el sistema completo desplegado, en dos máquinas distintas (o entornos con distinta configuración regional `es_CO` / `en_US`). |
| **Respuesta** | Un solo comando ejecuta todos los casos de referencia —incluidos los flujos que no convergen, flujos con múltiples cambios de signo, margen de contribución cero/negativo y presupuesto de 200 ítems— y reporta cuáles pasaron y cuáles no con el valor esperado y el obtenido. |
| **Medida** | La suite completa corre en **menos de 60 segundos**, cubre como mínimo **12 casos de referencia** documentados (incluyendo al menos **3 casos límite**: flujo sin cambio de signo, margen de contribución $\le 0$, presupuesto vacío) y produce exactamente el **mismo resultado en 2 máquinas/entornos distintos** (0 fallos por `Locale` de coma vs. punto decimal). |

---

### RQ-04 · Seguridad (Autenticación y Autorización Autónoma sin Estado)

> **Propósito:** Asegurar que ningún servicio confíe ciegamente en la red interna ni en el frontend, validando criptográficamente la identidad y el rol en cada petición.

| Parte | Descripción |
| :--- | :--- |
| **Fuente** | Un cliente HTTP externo o un usuario autenticado con rol `presupuestador` o `revisor` operando directamente contra los endpoints de los microservicios (saltándose la interfaz de Angular). |
| **Estímulo** | Envía peticiones directas a los endpoints de `insumo-service`, `presupuesto-service` o `calculo-service` en cuatro condiciones: (1) sin cabecera `Authorization`, (2) con un JWT cuya firma fue alterada en un carácter, (3) con un JWT válido pero del rol opuesto al requerido por la matriz de autorización, y (4) con un JWT válido del rol autorizado. |
| **Artefacto** | Filtro de seguridad OAuth 2.0 Resource Server (`SecurityFilterChain` y convertidor de `realm_access.roles`) en cada uno de los tres microservicios. |
| **Entorno** | Operación normal con Keycloak activo y el realm `presupuesto-realm` importado automáticamente. |
| **Respuesta** | Cada microservicio verifica de forma autónoma la firma del JWT contra el endpoint JWKS del realm sin consultar sesiones compartidas, extrae el identificador `sub` y los roles de `realm_access.roles`, y aplica la matriz de control de acceso. |
| **Medida** | **100% de concordancia** en las 4 pruebas reproducibles por servicio: **HTTP 401** sin token, **HTTP 401** con firma alterada, **HTTP 403** con rol equivocado (probado en ambos sentidos: `revisor` intentando editar presupuesto y `presupuestador` intentando aprobar su propio presupuesto), y **HTTP 200/201** con el rol correcto. **0 segundos** de consulta de estado de sesión entre microservicios. |

---

### RQ-05 · Desempeño (Evaluación Financiera Iterativa en C vs. Java)

> **Propósito:** Cuantificar la ganancia de velocidad y el costo de cruce de frontera JNA al ejecutar métodos numéricos iterativos (TIR) en C frente a Java puro, respondiendo al usuario en menos de un segundo.

| Parte | Descripción |
| :--- | :--- |
| **Fuente** | Un presupuestador o servicio de evaluación que solicita el análisis financiero (VPN, TIR y amortización) de un proyecto financiado a largo plazo. |
| **Estímulo** | Solicita el cálculo de la TIR sobre un flujo de caja de **120 periodos** con tolerancia $10^{-9}$ (`1e-9`), ejecutando un lote de **1.000 corridas consecutivas** para evaluación de estrés y benchmarking. |
| **Artefacto** | `calculo-service` invocando a `libfinanzas.so` vía JNA frente a la implementación de respaldo en Java puro (`FinanzasJavaFallback`). |
| **Entorno** | Contenedor Docker de `calculo-service` ejecutándose bajo los límites de recursos declarados en el manifiesto (`0.50 CPU`, `512 MB RAM`). |
| **Respuesta** | El sistema converge al valor de la TIR con precisión de al menos 6 decimales (mínimo exigido: 4 decimales), devuelve el conteo exacto de iteraciones y registra las métricas de tiempo e impacto en el contenedor. |
| **Medida** | 1) Tiempo de respuesta de una evaluación individual **$< 100\text{ ms}$** (muy por debajo del límite de $1\text{ s}$ del problema). 2) Reporte cuantitativo de las **4 cifras obligatorias**: tiempo de 1.000 corridas de 120 periodos en C nativo vs. Java puro, tiempo cruzando la frontera JNA 1 vez por corrida vs. 1 vez por iteración, delta de tamaño de la imagen Docker (MB antes y después de `libfinanzas.so`) y delta en tiempo de arranque del contenedor (ms). |
