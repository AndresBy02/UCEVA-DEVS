import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ComparacionEscenarios,
  ComparacionVersiones,
  DesembolsoConfigRequest,
  DesembolsosResponse,
  InsumosCriticos,
  PresupuestoDetalle,
  PresupuestoResumen,
  PrevisualizacionPrecios,
  SimulacionSensibilidad,
  VersionPresupuestoDetalle,
  VersionPresupuestoResumen,
} from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class PresupuestoApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8082/api/v1/presupuestos';

  listar(): Observable<PresupuestoResumen[]> {
    return this.http.get<PresupuestoResumen[]>(this.baseUrl);
  }

  listarEnRevision(): Observable<PresupuestoResumen[]> {
    return this.http.get<PresupuestoResumen[]>(`${this.baseUrl}/en-revision`);
  }

  crear(req: { codigo: string; nombre: string; cliente: string }): Observable<PresupuestoDetalle> {
    return this.http.post<PresupuestoDetalle>(this.baseUrl, req);
  }

  obtenerDetalle(id: string): Observable<PresupuestoDetalle> {
    return this.http.get<PresupuestoDetalle>(`${this.baseUrl}/${id}`);
  }

  actualizarParametros(
    id: string,
    req: {
      nombre: string;
      cliente: string;
      porcentajeAdministracion: number;
      porcentajeImprevistos: number;
      porcentajeUtilidad: number;
      costoFijo: number;
      precioVentaUnitario: number | null;
      costoVariableUnitario: number | null;
    }
  ): Observable<PresupuestoDetalle> {
    return this.http.put<PresupuestoDetalle>(`${this.baseUrl}/${id}`, req);
  }

  agregarCapitulo(id: string, req: { codigo: string; nombre: string }): Observable<PresupuestoDetalle> {
    return this.http.post<PresupuestoDetalle>(`${this.baseUrl}/${id}/capitulos`, req);
  }

  eliminarCapitulo(id: string, capId: string): Observable<PresupuestoDetalle> {
    return this.http.delete<PresupuestoDetalle>(`${this.baseUrl}/${id}/capitulos/${capId}`);
  }

  agregarItem(
    id: string,
    capId: string,
    req: { insumoId: string; cantidad: string }
  ): Observable<PresupuestoDetalle> {
    return this.http.post<PresupuestoDetalle>(`${this.baseUrl}/${id}/capitulos/${capId}/items`, req);
  }

  actualizarItem(
    id: string,
    capId: string,
    itemId: string,
    req: { cantidad: string; precioUnitarioCopia?: string }
  ): Observable<PresupuestoDetalle> {
    return this.http.put<PresupuestoDetalle>(
      `${this.baseUrl}/${id}/capitulos/${capId}/items/${itemId}`,
      req
    );
  }

  eliminarItem(id: string, capId: string, itemId: string): Observable<PresupuestoDetalle> {
    return this.http.delete<PresupuestoDetalle>(
      `${this.baseUrl}/${id}/capitulos/${capId}/items/${itemId}`
    );
  }

  previsualizarActualizacionPrecios(id: string): Observable<PrevisualizacionPrecios> {
    return this.http.get<PrevisualizacionPrecios>(`${this.baseUrl}/${id}/actualizar-precios/preview`);
  }

  aplicarActualizacionPrecios(id: string): Observable<PresupuestoDetalle> {
    return this.http.post<PresupuestoDetalle>(`${this.baseUrl}/${id}/actualizar-precios`, {});
  }

  duplicarEscenario(
    id: string,
    req: { codigo: string; nombre: string; cliente?: string }
  ): Observable<PresupuestoDetalle> {
    return this.http.post<PresupuestoDetalle>(`${this.baseUrl}/${id}/duplicar`, req);
  }

  compararEscenarios(idA: string, idB: string): Observable<ComparacionEscenarios> {
    return this.http.get<ComparacionEscenarios>(`${this.baseUrl}/${idA}/comparar/${idB}`);
  }

  simularSensibilidad(
    id: string,
    codigoInsumo: string,
    porcentajeVariacion: number
  ): Observable<SimulacionSensibilidad> {
    return this.http.post<SimulacionSensibilidad>(`${this.baseUrl}/${id}/sensibilidad`, {
      codigoInsumo,
      porcentajeVariacion,
    });
  }

  obtenerInsumosCriticos(id: string, porcentaje = 10): Observable<InsumosCriticos> {
    const params = new HttpParams().set('porcentaje', porcentaje.toString());
    return this.http.get<InsumosCriticos>(`${this.baseUrl}/${id}/insumos-criticos`, { params });
  }

  listarPorInsumo(insumoId: string): Observable<PresupuestoResumen[]> {
    return this.http.get<PresupuestoResumen[]>(`${this.baseUrl}/por-insumo/${insumoId}`);
  }

  aprobar(id: string): Observable<PresupuestoDetalle> {
    return this.http.post<PresupuestoDetalle>(`${this.baseUrl}/${id}/aprobar`, {});
  }

  enviarARevision(id: string, req?: { etiqueta?: string; comentario?: string }): Observable<PresupuestoDetalle> {
    return this.http.post<PresupuestoDetalle>(`${this.baseUrl}/${id}/enviar-revision`, req ?? {});
  }

  rechazar(id: string, motivo: string): Observable<PresupuestoDetalle> {
    return this.http.post<PresupuestoDetalle>(`${this.baseUrl}/${id}/rechazar`, { motivo });
  }

  crearVersion(id: string, req: { etiqueta: string; comentario?: string }): Observable<VersionPresupuestoResumen> {
    return this.http.post<VersionPresupuestoResumen>(`${this.baseUrl}/${id}/versiones`, req);
  }

  listarVersiones(id: string): Observable<VersionPresupuestoResumen[]> {
    return this.http.get<VersionPresupuestoResumen[]>(`${this.baseUrl}/${id}/versiones`);
  }

  obtenerVersion(id: string, versionId: string): Observable<VersionPresupuestoDetalle> {
    return this.http.get<VersionPresupuestoDetalle>(`${this.baseUrl}/${id}/versiones/${versionId}`);
  }

  compararVersiones(id: string, versionA: string, versionB: string): Observable<ComparacionVersiones> {
    const params = new HttpParams().set('versionA', versionA).set('versionB', versionB);
    return this.http.get<ComparacionVersiones>(`${this.baseUrl}/${id}/versiones/comparar`, { params });
  }

  exportarCsv(id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${id}/exportar-csv`, { responseType: 'blob' });
  }

  obtenerDesembolsos(id: string): Observable<DesembolsosResponse> {
    return this.http.get<DesembolsosResponse>(`${this.baseUrl}/${id}/desembolsos`);
  }

  guardarDesembolsos(id: string, req: DesembolsoConfigRequest): Observable<DesembolsosResponse> {
    return this.http.put<DesembolsosResponse>(`${this.baseUrl}/${id}/desembolsos`, req);
  }
}
