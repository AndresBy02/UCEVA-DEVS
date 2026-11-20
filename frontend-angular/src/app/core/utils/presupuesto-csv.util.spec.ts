import { PresupuestoDetalle } from '../models/api.models';
import {
  BOM_UTF8,
  descargarCsvPresupuesto,
  generarCsvPresupuesto,
  nombreArchivoCsvPresupuesto,
} from './presupuesto-csv.util';

const presupuesto: PresupuestoDetalle = {
  id: 'presupuesto-1',
  codigo: 'PRES-001',
  nombre: 'Obra de prueba',
  cliente: 'UCEVA',
  estado: 'BORRADOR',
  presupuestoBaseId: null,
  creadoPor: 'sub-juan',
  fechaCreacion: '2026-11-19T00:00:00Z',
  aprobadoPor: null,
  fechaAprobacion: null,
  porcentajeAdministracion: 8,
  porcentajeImprevistos: 3,
  porcentajeUtilidad: 5,
  costoFijo: 0,
  precioVentaUnitario: null,
  costoVariableUnitario: null,
  capitulos: [
    {
      id: 'cap-1',
      orden: 1,
      codigo: 'CAP-01',
      nombre: 'Preliminares',
      subtotalCapitulo: 160.1,
      items: [
        {
          id: 'item-1',
          orden: 1,
          insumoId: 'insumo-1',
          codigoInsumo: 'INS-01',
          descripcion: 'Descapote y limpieza, manual',
          unidadMedida: 'KG',
          cantidad: '10.000',
          precioUnitarioCopia: '12.345',
          valorParcial: 123.45,
        },
        {
          id: 'item-2',
          orden: 2,
          insumoId: 'insumo-2',
          codigoInsumo: 'INS-02',
          descripcion: 'Línea "A"\nSegunda línea',
          unidadMedida: 'M2',
          cantidad: '2.000',
          precioUnitarioCopia: '18.325',
          valorParcial: 36.65,
        },
      ],
    },
  ],
  costoDirecto: 160.1,
  valorAdministracion: 12.81,
  valorImprevistos: 4.8,
  valorUtilidad: 8.01,
  precioVenta: 185.72,
  margenSobreCostoPorcentaje: 16,
  rotuloMargenSobreCosto: 'Margen sobre el costo',
  margenSobrePrecioVentaPorcentaje: 13.79,
  rotuloMargenSobrePrecioVenta: 'Margen sobre precio de venta',
  puntoEquilibrio: null,
};

function lineasCsv(csv: string): string[] {
  return csv.slice(BOM_UTF8.length).trim().split('\r\n');
}

describe('generarCsvPresupuesto', () => {
  it('incluye BOM UTF-8 y la cabecera de columnas', () => {
    const csv = generarCsvPresupuesto(presupuesto);

    expect(csv.startsWith(BOM_UTF8)).toBeTrue();
    expect(lineasCsv(csv)[0]).toBe(
      'tipo_fila,capitulo_orden,capitulo_codigo,capitulo_nombre,item_orden,codigo_insumo,descripcion,unidad,cantidad,precio_unitario,valor',
    );
  });

  it('emite una fila por Ítem con los valores exactos devueltos por la API', () => {
    const filas = lineasCsv(generarCsvPresupuesto(presupuesto));
    const filasItem = filas.filter((f) => f.startsWith('ITEM,'));

    expect(filasItem.length).toBe(2);
    expect(filasItem[0]).toBe(
      'ITEM,1,CAP-01,Preliminares,1,INS-01,"Descapote y limpieza, manual",KG,10.000,12.345,123.45',
    );
    expect(filasItem[1]).toContain('Línea ""A""\nSegunda línea');
  });

  it('emite una fila de subtotal por capítulo con el valor de la pantalla', () => {
    const filas = lineasCsv(generarCsvPresupuesto(presupuesto));
    const subtotales = filas.filter((f) => f.startsWith('SUBTOTAL_CAPITULO,'));

    expect(subtotales.length).toBe(1);
    expect(subtotales[0].endsWith(',160.10')).toBeTrue();
  });

  it('emite los totales de la pantalla sin recalcularlos', () => {
    const filas = lineasCsv(generarCsvPresupuesto(presupuesto));
    const totales = filas.filter((f) => f.startsWith('TOTAL,'));
    const valores = totales.map((f) => Number(f.split(',')[10]));

    expect(totales.length).toBe(7);
    expect(valores).toEqual([160.1, 12.81, 4.8, 8.01, 185.72, 16, 13.79]);
    expect(valores[1] + valores[2] + valores[3] + valores[0]).toBe(valores[4]);
  });

  it('escapa comillas dobles, comas y saltos de línea', () => {
    const csv = generarCsvPresupuesto(presupuesto);

    expect(csv).toContain('"Descapote y limpieza, manual"');
    expect(csv).toContain('"Línea ""A""\nSegunda línea"');
  });

  it('no agrega separadores de miles a los valores monetarios', () => {
    const csv = generarCsvPresupuesto(presupuesto);

    expect(csv).not.toContain('1,234');
    expect(csv).toContain(',123.45');
  });
});

describe('nombreArchivoCsvPresupuesto', () => {
  it('genera un nombre de archivo seguro', () => {
    expect(nombreArchivoCsvPresupuesto(presupuesto)).toBe('presupuesto-PRES-001.csv');

    const conCaracteresRaros = { ...presupuesto, codigo: 'PRES/001: A' };
    expect(nombreArchivoCsvPresupuesto(conCaracteresRaros)).toBe('presupuesto-PRES_001_A.csv');
  });
});

describe('descargarCsvPresupuesto', () => {
  it('crea un enlace de descarga con el CSV y libera la URL del Blob', () => {
    const crear = spyOn(URL, 'createObjectURL').and.returnValue('blob:test');
    const liberar = spyOn(URL, 'revokeObjectURL');
    const enlace = document.createElement('a');
    const crearEnlace = spyOn(document, 'createElement').and.returnValue(enlace);
    const click = spyOn(enlace, 'click');
    const appendChild = spyOn(document.body, 'appendChild').and.callThrough();
    const remove = spyOn(enlace, 'remove');

    descargarCsvPresupuesto(presupuesto);

    expect(crear).toHaveBeenCalled();
    expect(crearEnlace).toHaveBeenCalledWith('a');
    expect(enlace.download).toBe('presupuesto-PRES-001.csv');
    expect(appendChild).toHaveBeenCalledWith(enlace);
    expect(click).toHaveBeenCalled();
    expect(remove).toHaveBeenCalled();
    expect(liberar).toHaveBeenCalledWith('blob:test');
  });
});
