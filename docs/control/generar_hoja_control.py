import csv
from decimal import Decimal, ROUND_HALF_UP
import os
import json

os.makedirs("docs/control", exist_ok=True)

capitulos = [
    ("CAP-01", "01. PRELIMINARES Y LOCALIZACION"),
    ("CAP-02", "02. CIMENTACION Y ESTRUCTURA EN CONCRETO"),
    ("CAP-03", "03. MAMPOSTERIA - PANETES Y ACABADOS"),
    ("CAP-04", "04. INSTALACIONES HIDROSANITARIAS Y ELECTRICAS"),
    ("CAP-05", "05. CUBIERTA - CARPINTERIA Y OBRAS EXTERIORES"),
]

unidades = ["M", "M2", "M3", "KG", "BULTO", "UNIDAD", "DIA"]

descripciones_base = {
    "CAP-01": [
        "Descapote y limpieza manual de terreno",
        "Localizacion y replanteo topografico con equipo",
        "Campamento provisional en madera y teja de zinc",
        "Cerramiento perimetral en tela verde h=2.00m",
        "Demolicion de andenes existentes en concreto",
        "Excavacion manual en material comun h<1.50m",
        "Retiro de sobrantes de excavacion en volqueta",
        "Nivelacion y compactacion de subrasante con rana",
    ],
    "CAP-02": [
        "Solado de limpieza en concreto pobre 2000 PSI e=5cm",
        "Concreto ciclipeo 60% concreto 3000 PSI 40% piedra",
        "Zapatas aisladas en concreto 3500 PSI premezclado",
        "Viga de cimentacion antisismica 30x35cm 3500 PSI",
        "Acero de refuerzo figurado fy=420 MPa (60000 PSI)",
        "Malla electrosoldada XX-131 para placa de contrapiso",
        "Columna en concreto a la vista 35x35cm 4000 PSI",
        "Placa aligerada de entrepiso h=25cm con caseton",
    ],
    "CAP-03": [
        "Muro en ladrillo tolete recocido e=15cm mortero 1:4",
        "Muro en bloque de arcilla No. 5 e=12cm divisorio",
        "Pañete liso interior en mortero 1:4 impermeabilizado",
        "Estuco plástico y dos manos de pintura vinilo tipo 1",
        "Piso en porcelanato rectificado 60x60cm trafico pesado",
        "Enchape ceramico para zonas humedas 30x60cm",
        "Guardaescoba en porcelanato h=8cm biselado",
        "Cielo raso en placa de yeso drywall RH junta invisible",
    ],
    "CAP-04": [
        "Punto sanitario en tuberia PVC sanitaria 4 pulgadas",
        "Punto hidraulico agua fria en PVC presion RDE 21 1/2",
        "Caja de inspeccion en mamposteria 60x60x60cm con tapa",
        "Tanque elevado de almacenamiento en polietileno 1000L",
        "Salida electrica tomacorriente doble con polo a tierra",
        "Salida de iluminacion en tuberia EMT 3/4 y cable THHN",
        "Tablero trifasico de 24 circuitos con totalizador",
        "Luminaria panel LED sobreponer 40W luz blanca 6500K",
    ],
    "CAP-05": [
        "Estructura metalica de cubierta en perfileria PHR",
        "Cubierta termoacustica tipo sandwich UPVC calibre 2.5mm",
        "Canal en lamina galvanizada calibre 22 desarrollo 50cm",
        "Puerta entamborada en madera cedro con marco y chapa",
        "Ventana corrediza en aluminio anodizado y vidrio 5mm",
        "Baranda de proteccion en acero inoxidable h=1.00m",
        "Anden perimetral en concreto escobillado 3000 PSI e=10cm",
        "Aseo general y lavado de fachadas para entrega de obra",
    ],
}

items = []
subtotales_cap = {cap_id: Decimal("0.00") for cap_id, _ in capitulos}

for idx in range(1, 201):
    cap_idx = (idx - 1) // 40  # 40 items por capitulo = 200 items exactos
    cap_id, cap_nombre = capitulos[cap_idx]
    local_idx = (idx - 1) % 40
    desc_base = descripciones_base[cap_id][local_idx % len(descripciones_base[cap_id])]
    tramo = (local_idx // len(descripciones_base[cap_id])) + 1
    
    item_id = f"ITEM-{idx:03d}"
    codigo_insumo = f"INS-{cap_idx+1:02d}-{local_idx+1:03d}"
    descripcion = f"{desc_base} - Sector {tramo}"
    unidad = unidades[(idx * 3 + cap_idx) % len(unidades)]
    
    # Generar cantidades y precios con 3 decimales exactos (ej. 12.345 en el item 1)
    if idx == 1:
        cantidad = Decimal("10.000")
        precio_unitario = Decimal("12.345")
    else:
        entero_q = (idx * 7) % 120 + 1
        frac_q = ((idx * 137) % 999) + 1  # 001 a 999
        cantidad = Decimal(f"{entero_q}.{frac_q:03d}")
        
        entero_p = ((idx * 29) % 850) + 15
        frac_p = ((idx * 313) % 999) + 1  # 001 a 999
        precio_unitario = Decimal(f"{entero_p}.{frac_p:03d}")
    
    producto_exacto_6dec = cantidad * precio_unitario
    valor_parcial = producto_exacto_6dec.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    subtotales_cap[cap_id] += valor_parcial
    
    items.append({
        "item_num": idx,
        "item_id": item_id,
        "capitulo_id": cap_id,
        "capitulo_nombre": cap_nombre,
        "codigo_insumo": codigo_insumo,
        "descripcion": descripcion,
        "unidad": unidad,
        "cantidad": f"{cantidad:.3f}",
        "precio_unitario": f"{precio_unitario:.3f}",
        "producto_sin_redondear": f"{producto_exacto_6dec:.6f}",
        "valor_parcial": f"{valor_parcial:.2f}",
    })

costo_directo = sum(subtotales_cap.values(), Decimal("0.00"))
porc_a = Decimal("0.0800")  # 8%
porc_i = Decimal("0.0300")  # 3%
porc_u = Decimal("0.0500")  # 5%

valor_a = (costo_directo * porc_a).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
valor_i = (costo_directo * porc_i).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
valor_u = (costo_directo * porc_u).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
precio_venta = costo_directo + valor_a + valor_i + valor_u

margen_sobre_costo = (((precio_venta - costo_directo) / costo_directo) * Decimal("100")).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
margen_sobre_venta = (((precio_venta - costo_directo) / precio_venta) * Decimal("100")).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)

csv_path = "docs/control/hoja_control_200_items.csv"
with open(csv_path, mode="w", newline="", encoding="utf-8") as f:
    writer = csv.DictWriter(f, fieldnames=[
        "item_num", "item_id", "capitulo_id", "capitulo_nombre",
        "codigo_insumo", "descripcion", "unidad",
        "cantidad", "precio_unitario", "producto_sin_redondear", "valor_parcial"
    ])
    writer.writeheader()
    writer.writerows(items)

resumen = {
    "total_items": len(items),
    "regla_redondeo": "RoundingMode.HALF_UP a 2 decimales en cada valor_parcial = ROUND(cantidad * precio_unitario, 2)",
    "subtotales_capitulo": {k: f"{v:.2f}" for k, v in subtotales_cap.items()},
    "costo_directo": f"{costo_directo:.2f}",
    "porcentaje_administracion": "8.00",
    "valor_administracion": f"{valor_a:.2f}",
    "porcentaje_imprevistos": "3.00",
    "valor_imprevistos": f"{valor_i:.2f}",
    "porcentaje_utilidad": "5.00",
    "valor_utilidad": f"{valor_u:.2f}",
    "precio_venta": f"{precio_venta:.2f}",
    "margen_sobre_costo_porcentaje": f"{margen_sobre_costo:.2f}",
    "margen_sobre_precio_venta_porcentaje": f"{margen_sobre_venta:.2f}",
}

with open("docs/control/resumen_esperado_hoja_control.json", "w", encoding="utf-8") as f:
    json.dump(resumen, f, indent=2, ensure_ascii=False)

print(json.dumps(resumen, indent=2))
