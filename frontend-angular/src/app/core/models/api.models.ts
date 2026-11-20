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
  estado: 'BORRADOR' | 'EN_REVISION' | 'APROBADO';
  presupuestoBaseId: string | null;
  creadoPor: string;
  fechaCreacion: string;
  enviadoRevisionPor: string | null;
  fechaEnvioRevision: string | null;
  aprobadoPor: string | null;
  fechaAprobacion: string | null;
  motivoRechazo: string | null;
  rechazadoPor: string | null;
  fechaRechazo: string | null;
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
  estado: 'BORRADOR' | 'EN_REVISION' | 'APROBADO';
  presupuestoBaseId: string | null;
  porcentajeAdministracion: number;
  porcentajeImprevistos: number;
  porcentajeUtilidad: number;
  creadoPor: string;
  fechaCreacion: string;
  enviadoRevisionPor: string | null;
  fechaEnvioRevision: string | null;
  aprobadoPor: string | null;
  fechaAprobacion: string | null;
  motivoRechazo: string | null;
  rechazadoPor: string | null;
  fechaRechazo: string | null;
  totalCapitulos: number;
  totalItems: number;
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

export interface VersionPresupuestoResumen {
  id: string;
  presupuestoId: string;
  numeroVersion: number;
  etiqueta: string;
  comentarioEnvio: string | null;
  estadoEnMomento: string;
  creadoPor: string;
  fechaCreacion: string;
  costoDirecto: number;
  valorAdministracion: number;
  valorImprevistos: number;
  valorUtilidad: number;
  precioVenta: number;
}

export interface VersionPresupuestoDetalle extends VersionPresupuestoResumen {
  snapshot: PresupuestoDetalle;
}

export interface ItemVersionCambio {
  itemId: string;
  capituloCodigo: string;
  capituloNombre: string;
  codigoInsumo: string;
  descripcion: string;
  unidadMedida: string;
  cantidad: string;
  precioUnitarioCopia: string;
  valorParcial: number;
}

export interface ItemVersionModificado {
  itemId: string;
  capituloCodigo: string;
  capituloNombre: string;
  codigoInsumo: string;
  descripcion: string;
  unidadMedida: string;
  cantidadAntes: string;
  cantidadDespues: string;
  precioAntes: string;
  precioDespues: string;
  valorParcialAntes: number;
  valorParcialDespues: number;
  diferenciaParcial: number;
}

export interface ComparacionVersiones {
  presupuestoId: string;
  versionA: VersionPresupuestoResumen;
  versionB: VersionPresupuestoResumen;
  diferenciaCostoDirecto: number;
  diferenciaPrecioVenta: number;
  itemsAgregados: ItemVersionCambio[];
  itemsEliminados: ItemVersionCambio[];
  itemsModificados: ItemVersionModificado[];
}

export interface CapituloDesembolso {
  capituloId: string;
  codigo: string;
  nombre: string;
  subtotalCapitulo: number;
  totalCapituloDesembolsado: number;
  porcentajesPorSemana: number[];
  montosPorSemana: number[];
}

export interface DesembolsosResponse {
  semanas: number;
  costoDirectoTotal: number;
  totalDesembolsado: number;
  diferenciaValidacion: number;
  capitulos: CapituloDesembolso[];
  totalesPorSemana: number[];
  acumuladosPorSemana: number[];
}

export interface DesembolsoConfigRequest {
  semanas: number;
  capitulos: Array<{
    capituloId: string;
    porcentajesPorSemana: number[];
  }>;
}

export interface EvaluacionFinancieraRequest {
  tasaDescuento: number;
  flujosCaja: number[];
  toleranciaTir: number;
  maxIteracionesTir: number;
  financiacion: {
    capital: number;
    tasaPeriodo: number;
    periodos: number;
  } | null;
}

export interface FilaAmortizacion {
  periodo: number;
  cuota: number;
  interes: number;
  abonoCapital: number;
  saldo: number;
}

export interface EvaluacionFinancieraResponse {
  motorUtilizado: string;
  vpn: number | null;
  tir: {
    convergio: boolean;
    estado: string;
    codigoRetornoC: number;
    tasaInternaRetorno: number | null;
    tasaInternaRetornoPorcentaje: number | null;
    iteraciones: number;
    mensaje: string;
  };
  amortizacion: {
    capital: number;
    tasaPeriodo: number;
    periodos: number;
    cuotaFija: number;
    filas: FilaAmortizacion[];
  } | null;
}

export interface SpikeJnaResponse {
  bibliotecaCargada: boolean;
  nombreBiblioteca: string;
  operandoA: number;
  operandoB: number;
  resultadoNativoC: number;
  arquitecturaContenedor: string;
  mensaje: string;
}

export interface BenchmarkResponse {
  periodos: number;
  corridas: number;
  motorActivo: string;
  tiempoJavaMs: number;
  tiempoNativoMs: number | null;
  tiempoNativoPorIteracionMs: number | null;
  tiempoJavaMsExacto: number;
  tiempoNativoMsExacto: number | null;
  tiempoNativoPorIteracionMsExacto: number | null;
  totalLlamadasJnaPorCorrida: number;
  totalLlamadasJnaPorIteracion: number;
  factorSobrecostoJna: number | null;
  tirResultado: number | null;
  iteracionesPorCorrida: number;
}
