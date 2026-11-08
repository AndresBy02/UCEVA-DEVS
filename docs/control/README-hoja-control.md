# Especificación de la Hoja de Control de 200 Ítems (Oráculo RQ-01)

**Proyecto:** Presupuestador de Obra — UCEVA Devs  
**Referencia:** UC-56 / HU-32 · RQ-01 Testabilidad

---

## 1. Archivos que Componen la Hoja de Control

1. **`docs/control/hoja_control_200_items.csv`**: Contiene los **200 ítems de obra** distribuidos en **5 capítulos** (`40 ítems` por capítulo), cada uno con cantidad fraccionaria de **3 decimales** y precio unitario de **3 decimales** (comenzando por el ítem 1 con precio `12.345` exigido en **RF-01**).
2. **`docs/control/resumen_esperado_hoja_control.json`**: Contiene los subtotales de control por capítulo, el costo directo total, el AIU discriminado ($A=8\%$, $I=3\%$, $U=5\%$), el precio de venta y los dos márgenes.
3. **`docs/control/generar_hoja_control.py`**: Script reproducible con aritmética `decimal.Decimal` (`ROUND_HALF_UP`) que genera y audita la hoja sin contaminación de punto flotante binario.

---

## 2. Regla de Redondeo Declarada por Escrito

Para que la comparación entre la hoja de cálculo (Excel / LibreOffice Calc / Google Sheets) y el sistema (`calculo-service`) tenga **desviación de $\$0.00\text{ COP}$** en los 200 ítems:

- **Fórmula en cada fila $i$ (Valor Parcial):**
  $$\text{valor\_parcial}_i = \text{REDONDEAR}(\text{cantidad}_i \times \text{precio\_unitario}_i;\; 2)$$
  En Java: `cantidad.multiply(precioUnitario).setScale(2, RoundingMode.HALF_UP)`.
- **Fórmula en cada capítulo $c$ (Subtotal de Capítulo):**
  $$\text{subtotal}_c = \sum_{i \in c} \text{valor\_parcial}_i$$
- **Fórmula del Costo Directo ($CD$):**
  $$CD = \sum_{c=1}^{5} \text{subtotal}_c = \sum_{i=1}^{200} \text{valor\_parcial}_i$$
- **Fórmula del AIU Discriminado y Precio de Venta ($PV$):**
  $$A = \text{REDONDEAR}(CD \times 0.08;\; 2)$$
  $$I = \text{REDONDEAR}(CD \times 0.03;\; 2)$$
  $$U = \text{REDONDEAR}(CD \times 0.05;\; 2)$$
  $$PV = CD + A + I + U$$

> **Nota sobre la trampa del punto flotante sin redondear por fila:**  
> Si se sumaran los productos crudos de 6 decimales (`producto_sin_redondear`) sin aplicar `REDONDEAR(..., 2)` en cada ítem, la suma cruda de los 200 ítems sería `5211433.635244` (`5211433.64`), mientras que la suma de los 200 valores parciales impresos en la cotización es **`5211433.71`** (una discrepancia de `$0.07` centavos que confunde a quien revisa con calculadora). Al redondear en el punto único del valor parcial del ítem, los 200 renglones suman exactamente **`5211433.71`** tanto por capítulo como en el total general.

---

## 3. Valores Oficiales de Control (Oráculo para la Prueba Parametrizada)

| Concepto | Código / Capítulo | Valor Esperado Exacto (`COP`) |
| :--- | :--- | ---: |
| **Subtotal Capítulo 1** | `CAP-01` · 01. PRELIMINARES Y LOCALIZACION (Ítems 1–40) | **`891086.71`** |
| **Subtotal Capítulo 2** | `CAP-02` · 02. CIMENTACION Y ESTRUCTURA EN CONCRETO (Ítems 41–80) | **`1055433.36`** |
| **Subtotal Capítulo 3** | `CAP-03` · 03. MAMPOSTERIA, PAÑETES Y ACABADOS (Ítems 81–120) | **`1236102.03`** |
| **Subtotal Capítulo 4** | `CAP-04` · 04. INSTALACIONES HIDROSANITARIAS Y ELECTRICAS (Ítems 121–160) | **`879509.60`** |
| **Subtotal Capítulo 5** | `CAP-05` · 05. CUBIERTA, CARPINTERIA Y OBRAS EXTERIORES (Ítems 161–200) | **`1149302.01`** |
| **COSTO DIRECTO TOTAL ($CD$)** | Suma de los 5 capítulos / 200 ítems | **`5211433.71`** |
| **Administración ($A = 8.00\%$)** | `ROUND(5211433.71 * 0.08, 2)` | **`416914.70`** |
| **Imprevistos ($I = 3.00\%$)** | `ROUND(5211433.71 * 0.03, 2)` | **`156343.01`** |
| **Utilidad ($U = 5.00\%$)** | `ROUND(5211433.71 * 0.05, 2)` | **`260571.69`** |
| **PRECIO DE VENTA TOTAL ($PV$)** | `CD + A + I + U` | **`6045263.11`** |
| **Margen sobre el Costo** | `(PV - CD) / CD * 100` | **`16.00%`** |
| **Margen sobre el Precio de Venta** | `(PV - CD) / PV * 100` | **`13.79%`** |
