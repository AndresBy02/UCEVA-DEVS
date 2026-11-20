export interface Insumo {
  id: string;
  codigo: string;
  descripcion: string;
  unidadMedida: string;
  precioUnitario: string;
  activo: boolean;
  updatedBy?: string;
  updatedAt: string;
}

export interface InsumoRequest {
  codigo: string;
  descripcion: string;
  unidadMedida: string;
  precioUnitario: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface HistorialPrecio {
  id: string;
  insumoId: string;
  precioAnterior: string | null;
  precioNuevo: string;
  modificadoPor: string | null;
  fechaCambio: string;
}

export interface PuntoEquilibrio {
  viable: boolean;
  cantidadEquilibrio: number | null;
  margenContribucionUnitario: number;
  mensaje: string;
}

export interface ItemPresupuestoDetalle {
  id: string;
  orden: number;
  insumoId: string;
  codigoInsumo: string;
  descripcion: string;
  unidadMedida: string;
  cantidad: string;
  precioUnitarioCopia: string;
  valorParcial: number;
}

export interface CapituloDetalle {
  id: string;
  orden: number;
  codigo: string;
  nombre: string;
  subtotalCapitulo: number;
  items: ItemPresupuestoDetalle[];
}

export interface PresupuestoDetalle {
  id: string;
  codigo: string;
  nombre: string;
  cliente: string;
  estado: 'BORRADOR' | 'APROBADO';
  presupuestoBaseId: string | null;
  creadoPor: string;
  fechaCreacion: string;
  aprobadoPor: string | null;
  fechaAprobacion: string | null;
  porcentajeAdministracion: number;
  porcentajeImprevistos: number;
  porcentajeUtilidad: number;
  costoFijo: number;
  precioVentaUnitario: number | null;
  costoVariableUnitario: number | null;
  capitulos: CapituloDetalle[];
  costoDirecto: number;
  valorAdministracion: number;
  valorImprevistos: number;
  valorUtilidad: number;
  precioVenta: number;
  margenSobreCostoPorcentaje: number;
  rotuloMargenSobreCosto: string;
  margenSobrePrecioVentaPorcentaje: number;
  rotuloMargenSobrePrecioVenta: string;
  puntoEquilibrio: PuntoEquilibrio | null;
}

export interface PresupuestoResumen {
  id: string;
  codigo: string;
  nombre: string;
  cliente: string;
  estado: 'BORRADOR' | 'APROBADO';
  presupuestoBaseId: string | null;
  porcentajeAdministracion: number;
  porcentajeImprevistos: number;
  porcentajeUtilidad: number;
  creadoPor: string;
  fechaCreacion: string;
  aprobadoPor: string | null;
  fechaAprobacion: string | null;
  totalCapitulos: number;
  totalItems: number;
  totalVersiones: number;
}

/** Versión congelada de un presupuesto enviado a revisión (RF-10 / UC-38). */
export interface VersionPresupuesto {
  id: string;
  presupuestoId: string;
  numero: number;
  codigoPresupuesto: string;
  nombrePresupuesto: string;
  estado: string;
  creadoPor: string;
  fechaCreacion: string;
  destinatarioSub: string;
  fechaNotificacion: string;
  mensaje: string | null;
  snapshotJson: string;
}

export interface EnviarRevisionRequest {
  destinatarioSub?: string;
  mensaje?: string;
}

export interface CambioPrecioItem {
  itemId: string;
  capituloCodigo: string;
  insumoId: string;
  codigoInsumo: string;
  descripcion: string;
  cantidad: string;
  precioAnteriorPresupuesto: string;
  precioNuevoCatalogo: string;
  diferenciaUnitaria: string;
}

export interface PrevisualizacionPrecios {
  presupuestoId: string;
  codigoPresupuesto: string;
  totalItemsRevisados: number;
  cantidadItemsConCambio: number;
  cambios: CambioPrecioItem[];
}

export interface ComparacionEscenarios {
  escenarioA: {
    id: string;
    codigo: string;
    nombre: string;
    costoDirecto: number;
    valorAdministracion: number;
    valorImprevistos: number;
    valorUtilidad: number;
    precioVenta: number;
  };
  escenarioB: {
    id: string;
    codigo: string;
    nombre: string;
    costoDirecto: number;
    valorAdministracion: number;
    valorImprevistos: number;
    valorUtilidad: number;
    precioVenta: number;
  };
  capitulos: Array<{
    codigoCapitulo: string;
    nombreCapitulo: string;
    subtotalEscenarioA: number;
    subtotalEscenarioB: number;
    diferenciaAbsoluta: number;
    diferenciaPorcentual: number;
  }>;
  comparacionTotal: {
    diferenciaCostoDirectoAbsoluta: number;
    diferenciaCostoDirectoPorcentual: number;
    diferenciaPrecioVentaAbsoluta: number;
    diferenciaPrecioVentaPorcentual: number;
  };
}

export interface SimulacionSensibilidad {
  codigoInsumo: string;
  porcentajeVariacion: number;
  costoDirectoOriginal: number;
  costoDirectoSimulado: number;
  diferenciaCostoDirecto: number;
  variacionCostoDirectoPorcentaje: number;
  precioVentaOriginal: number;
  precioVentaSimulado: number;
  diferenciaPrecioVenta: number;
  variacionPrecioVentaPorcentaje: number;
  puntoEquilibrioOriginal: PuntoEquilibrio | null;
  puntoEquilibrioSimulado: PuntoEquilibrio | null;
}

export interface InsumosCriticos {
  costoDirectoBase: number;
  precioVentaBase: number;
  porcentajeVariacionReferencia: number;
  insumosCriticos: Array<{
    codigoInsumo: string;
    descripcion: string;
    unidad: string;
    cantidadTotal: number;
    aporteCostoDirecto: number;
    participacionPorcentaje: number;
    impactoCostoDirecto: number;
    impactoPrecioVenta: number;
  }>;
}
