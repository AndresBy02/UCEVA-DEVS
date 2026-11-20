import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BenchmarkResponse, SpikeJnaResponse } from '../../core/models/api.models';
import { CalculoApiService } from '../../core/services/calculo-api.service';
import { extraerErrorHttp } from '../../core/utils/http-error.util';

@Component({
  selector: 'app-benchmark-jna',
  standalone: true,
  imports: [ReactiveFormsModule, DecimalPipe],
  template: `
    <section class="page-section">
      <div class="header-row">
        <div>
          <h2>Diagnóstico Nativo (JNA) y Benchmark C vs. Java (RQ-05 / UC-86)</h2>
          <p style="color: var(--muted); margin: 4px 0 0;">
            Medición real de la biblioteca compartida <code>libfinanzas.so</code> frente a la línea base Java y el costo de cruce por frontera JNA.
          </p>
        </div>
        <button type="button" class="btn-secondary" (click)="verificarSpike()">Probar Spike JNA</button>
      </div>

      @if (errorMsg()) {
        <div class="alert-error">{{ errorMsg() }}</div>
      }

      @if (spike(); as sp) {
        <div class="card">
          <h3>Estado de Biblioteca Nativa (<code>fin_ping_suma</code>)</h3>
          <div class="summary-grid" style="margin-top: 12px;">
            <div class="stat-box" [class.highlight]="sp.bibliotecaCargada">
              <span>Biblioteca Cargada</span>
              <strong>{{ sp.bibliotecaCargada ? 'SÍ (C Nativo)' : 'NO (Respaldo Java)' }}</strong>
              <small>{{ sp.nombreBiblioteca }}</small>
            </div>
            <div class="stat-box">
              <span>Arquitectura / Entorno</span>
              <strong>{{ sp.arquitecturaContenedor }}</strong>
            </div>
            <div class="stat-box">
              <span>Resultado Smoke Test ({{ sp.operandoA }} + {{ sp.operandoB }})</span>
              <strong>{{ sp.resultadoNativoC }}</strong>
              <small>{{ sp.mensaje }}</small>
            </div>
          </div>
        </div>
      }

      <form [formGroup]="form" (ngSubmit)="ejecutar()" class="card">
        <h3>Ejecutar Benchmark Comparativo (TIR · Tolerancia 1e-9)</h3>
        <div class="grid-form">
          <label>
            Periodos del flujo de caja
            <input type="number" formControlName="periodos" min="2" max="600" />
          </label>
          <label>
            Número de corridas consecutivas
            <input type="number" formControlName="corridas" min="1" max="5000" />
          </label>
          <div class="actions-row">
            <button type="submit" class="btn-primary" [disabled]="form.invalid || cargando()">
              {{ cargando() ? 'Ejecutando benchmark...' : 'Correr Benchmark 120x1000' }}
            </button>
          </div>
        </div>
      </form>

      @if (benchmark(); as bm) {
        <div class="card">
          <h3>Resultados Medidos ({{ bm.periodos }} periodos × {{ bm.corridas }} corridas)</h3>
          <div class="summary-grid" style="margin-top: 12px;">
            <div class="stat-box">
              <span>Java Puro (Línea Base)</span>
              <strong>{{ bm.tiempoJavaMsExacto | number: '1.3-3' }} ms</strong>
              <small>{{ bm.tiempoJavaMs }} ms enteros</small>
            </div>
            <div class="stat-box highlight">
              <span>C Nativo — 1 llamada JNA / corrida</span>
              <strong>
                {{ bm.tiempoNativoMsExacto !== null ? (bm.tiempoNativoMsExacto | number: '1.3-3') + ' ms' : 'No disponible' }}
              </strong>
              <small>Cruces JNA: {{ bm.totalLlamadasJnaPorCorrida }}</small>
            </div>
            <div class="stat-box">
              <span>C Nativo — 1 llamada JNA / iteración</span>
              <strong>
                {{ bm.tiempoNativoPorIteracionMsExacto !== null ? (bm.tiempoNativoPorIteracionMsExacto | number: '1.3-3') + ' ms' : 'No disponible' }}
              </strong>
              <small>Cruces JNA: {{ bm.totalLlamadasJnaPorIteracion }}</small>
            </div>
            <div class="stat-box">
              <span>Overhead Frontera JNA (Iteración / Corrida)</span>
              <strong>{{ bm.factorSobrecostoJna !== null ? (bm.factorSobrecostoJna | number: '1.2-2') + '×' : 'N/A' }}</strong>
              <small>TIR: {{ bm.tirResultado | number: '1.6-6' }} ({{ bm.iteracionesPorCorrida }} iter.)</small>
            </div>
          </div>
        </div>
      }
    </section>
  `,
})
export class BenchmarkJnaComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly calculoApi = inject(CalculoApiService);

  readonly spike = signal<SpikeJnaResponse | null>(null);
  readonly benchmark = signal<BenchmarkResponse | null>(null);
  readonly cargando = signal<boolean>(false);
  readonly errorMsg = signal<string | null>(null);

  readonly form = this.fb.group({
    periodos: this.fb.nonNullable.control(120, [Validators.required, Validators.min(2), Validators.max(600)]),
    corridas: this.fb.nonNullable.control(1000, [Validators.required, Validators.min(1), Validators.max(5000)]),
  });

  ngOnInit(): void {
    this.verificarSpike();
  }

  verificarSpike(): void {
    this.errorMsg.set(null);
    this.calculoApi.spikeJna().subscribe({
      next: (res) => this.spike.set(res),
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  ejecutar(): void {
    if (this.form.invalid) return;
    this.errorMsg.set(null);
    this.cargando.set(true);
    const { periodos, corridas } = this.form.getRawValue();
    this.calculoApi.ejecutarBenchmark(periodos, corridas).subscribe({
      next: (res) => {
        this.benchmark.set(res);
        this.cargando.set(false);
      },
      error: (err) => {
        this.cargando.set(false);
        this.errorMsg.set(extraerErrorHttp(err));
      },
    });
  }
}
