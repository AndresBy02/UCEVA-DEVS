import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  BenchmarkResponse,
  EvaluacionFinancieraRequest,
  EvaluacionFinancieraResponse,
  SpikeJnaResponse,
} from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class CalculoApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8083/api/v1/calculos';

  spikeJna(a = 25.5, b = 14.5): Observable<SpikeJnaResponse> {
    const params = new HttpParams().set('a', a.toString()).set('b', b.toString());
    return this.http.get<SpikeJnaResponse>(`${this.baseUrl}/spike-jna`, { params });
  }

  evaluarProyecto(req: EvaluacionFinancieraRequest): Observable<EvaluacionFinancieraResponse> {
    return this.http.post<EvaluacionFinancieraResponse>(`${this.baseUrl}/evaluacion-financiera`, req);
  }

  ejecutarBenchmark(periodos = 120, corridas = 1000): Observable<BenchmarkResponse> {
    const params = new HttpParams()
      .set('periodos', periodos.toString())
      .set('corridas', corridas.toString());
    return this.http.get<BenchmarkResponse>(`${this.baseUrl}/benchmark`, { params });
  }
}
