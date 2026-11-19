import { PresupuestoDetalle } from '../models/api.models';

/** Marca de orden de bytes UTF-8 para que Excel/LibreOffice respete acentos y ñ. */
export const BOM_UTF8 = '\uFEFF';

const COLUMNAS = [
  'tipo_fila',
  'capitulo_orden',
  'capitulo_codigo',
  'capitulo_nombre',
  'item_orden',
  'codigo_insumo',
  'descripcion',
  'unidad',
  'cantidad',
  'precio_unitario',
  'valor',
] as const;

function escapar(valor: string | number | null | undefined): string {
  if (valor === null || valor === undefined) {
    return '';
  }
  const texto = String(valor);
  return /[",\r\n]/.test(texto) ? `"${texto.replace(/"/g, '""')}"` : texto;
}

function fila(celdas: ReadonlyArray<string | number | null | undefined>): string {
  return celdas.map(escapar).join(',');
}

/**
 * Formatea un valor monetario sin separadores de miles para que la hoja de
 * cálculo lo interprete como numérico. No redondea: el backend ya entrega los
 * valores con escala 2 según el protocolo RQ-01.
 */
function dinero(valor: number): string {
  return valor.toFixed(2);
}

/**
 * Genera el CSV completo del presupuesto: una fila por Ítem, una fila de
 * subtotal por capítulo y las filas de totales mostradas en pantalla.
 */
export function generarCsvPresupuesto(presupuesto: PresupuestoDetalle): string {
  const lineas: string[] = [fila(COLUMNAS)];

  for (const cap of presupuesto.capitulos) {
    for (const item of cap.items) {
      lineas.push(
        fila([
          'ITEM',
          cap.orden,
          cap.codigo,
          cap.nombre,
          item.orden,
          item.codigoInsumo,
          item.descripcion,
          item.unidadMedida,
          item.cantidad,
          item.precioUnitarioCopia,
          dinero(item.valorParcial),
        ]),
      );
    }

    lineas.push(
      fila([
        'SUBTOTAL_CAPITULO',
        cap.orden,
        cap.codigo,
        cap.nombre,
        '',
        '',
        `Subtotal capítulo ${cap.orden} - ${cap.codigo}`,
        '',
        '',
        '',
        dinero(cap.subtotalCapitulo),
      ]),
    );
  }

  const totales: Array<readonly [string, number]> = [
    ['Costo Directo (CD)', presupuesto.costoDirecto],
    [`Administración (${dinero(presupuesto.porcentajeAdministracion)}%)`, presupuesto.valorAdministracion],
    [`Imprevistos (${dinero(presupuesto.porcentajeImprevistos)}%)`, presupuesto.valorImprevistos],
    [`Utilidad (${dinero(presupuesto.porcentajeUtilidad)}%)`, presupuesto.valorUtilidad],
    ['Precio de Venta (CD + A + I + U)', presupuesto.precioVenta],
    [presupuesto.rotuloMargenSobreCosto, presupuesto.margenSobreCostoPorcentaje],
    [presupuesto.rotuloMargenSobrePrecioVenta, presupuesto.margenSobrePrecioVentaPorcentaje],
  ];

  for (const [concepto, valor] of totales) {
    lineas.push(fila(['TOTAL', '', '', '', '', '', concepto, '', '', '', dinero(valor)]));
  }

  return `${BOM_UTF8}${lineas.join('\r\n')}\r\n`;
}

export function nombreArchivoCsvPresupuesto(presupuesto: PresupuestoDetalle): string {
  return `presupuesto-${presupuesto.codigo.replace(/[^\w.-]+/g, '_')}.csv`;
}

export function descargarCsvPresupuesto(
  presupuesto: PresupuestoDetalle,
  doc: Document = document,
): void {
  const blob = new Blob([generarCsvPresupuesto(presupuesto)], {
    type: 'text/csv;charset=utf-8;',
  });
  const url = URL.createObjectURL(blob);
  const enlace = doc.createElement('a');
  enlace.href = url;
  enlace.download = nombreArchivoCsvPresupuesto(presupuesto);
  enlace.style.display = 'none';
  doc.body.appendChild(enlace);
  enlace.click();
  enlace.remove();
  URL.revokeObjectURL(url);
}
