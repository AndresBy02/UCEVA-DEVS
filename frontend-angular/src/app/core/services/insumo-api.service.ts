import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  HistorialPrecio,
  Insumo,
  InsumoRequest,
  PageResponse,
} from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class InsumoApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8081/api/v1/insumos';

  listar(page = 0, size = 100): Observable<PageResponse<Insumo>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<Insumo>>(this.baseUrl, { params });
  }

  crear(req: InsumoRequest): Observable<Insumo> {
    return this.http.post<Insumo>(this.baseUrl, req);
  }

  actualizar(id: string, req: InsumoRequest): Observable<Insumo> {
    return this.http.put<Insumo>(`${this.baseUrl}/${id}`, req);
  }

  obtenerHistorial(id: string): Observable<HistorialPrecio[]> {
    return this.http.get<HistorialPrecio[]>(`${this.baseUrl}/${id}/historial-precios`);
  }
}
