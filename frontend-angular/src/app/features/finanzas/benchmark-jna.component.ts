import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
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
          <h2 class="page-title">Diagnóstico Nativo (JNA) y Benchmark C vs. Java</h2>
          <p class="page-subtitle">
            Medición empírica de <code>libfinanzas.so</code> frente a HotSpot JIT y el costo de cruce por frontera FFI (RQ-05).
          </p>
        </div>
        <button type="button" class="btn-outline" (click)="verificarSpike()">Probar Spike JNA</button>
      </div>

      @if (errorMsg()) {
        <div class="alert-error">{{ errorMsg() }}</div>
      }

      @if (spike(); as sp) {
        <div class="kpi-grid">
          <div class="stat-box" [class.success-box]="sp.bibliotecaCargada">
            <span>Biblioteca Nativa</span>
            <strong>{{ sp.bibliotecaCargada ? 'ACTIVA (libfinanzas.so)' : 'NO (Respaldo Java)' }}</strong>
            <small>{{ sp.nombreBiblioteca }}</small>
          </div>
          <div class="stat-box">
            <span>Arquitectura / Entorno</span>
            <strong>{{ sp.arquitecturaContenedor }}</strong>
            <small>Contenedor / Host activo</small>
          </div>
          <div class="stat-box">
            <span>Smoke Test (fin_ping_suma)</span>
            <strong>{{ sp.operandoA }} + {{ sp.operandoB }} = {{ sp.resultadoNativoC }}</strong>
            <small>{{ sp.mensaje }}</small>
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
            Corridas consecutivas
            <input type="number" formControlName="corridas" min="1" max="5000" />
          </label>
          <div class="actions-row">
            <button type="submit" class="btn-primary" [disabled]="form.invalid || cargando()">
              {{ cargando() ? 'Ejecutando benchmark...' : 'Correr Benchmark 120×1000' }}
            </button>
          </div>
        </div>
      </form>

      @if (benchmark(); as bm) {
        <div class="kpi-grid">
          <div class="stat-box">
            <span>Java Puro (HotSpot JIT)</span>
            <strong>{{ bm.tiempoJavaMsExacto | number: '1.3-3' }} ms</strong>
            <small>{{ bm.tiempoJavaMs }} ms redondeado</small>
          </div>
          <div class="stat-box highlight">
            <span>C Nativo · 1 cruce / corrida</span>
            <strong>
              {{ bm.tiempoNativoMsExacto !== null ? (bm.tiempoNativoMsExacto | number: '1.3-3') + ' ms' : 'No disponible' }}
            </strong>
            <small>Cruces JNA: {{ bm.totalLlamadasJnaPorCorrida }}</small>
          </div>
          <div class="stat-box">
            <span>C Nativo · 1 cruce / iteración</span>
            <strong>
              {{ bm.tiempoNativoPorIteracionMsExacto !== null ? (bm.tiempoNativoPorIteracionMsExacto | number: '1.3-3') + ' ms' : 'No disponible' }}
            </strong>
            <small>Cruces JNA: {{ bm.totalLlamadasJnaPorIteracion }}</small>
          </div>
          <div class="stat-box">
            <span>Sobrecosto Frontera JNA</span>
            <strong>{{ bm.factorSobrecostoJna !== null ? (bm.factorSobrecostoJna | number: '1.2-2') + '×' : 'N/A' }}</strong>
            <small>TIR: {{ bm.tirResultado | number: '1.6-6' }} ({{ bm.iteracionesPorCorrida }} iter.)</small>
          </div>
        </div>

        <div class="card">
          <h3>Comparación visual de latencia ({{ bm.periodos }} periodos × {{ bm.corridas }} corridas)</h3>
          <div class="bar-list">
            <div class="bar-item">
              <span class="bar-label">Java Puro (JIT)</span>
              <div class="bar-track">
                <div class="bar-fill" [style.width.%]="pct(bm.tiempoJavaMsExacto)"></div>
              </div>
              <span class="bar-value">{{ bm.tiempoJavaMsExacto | number: '1.2-2' }} ms</span>
            </div>
            @if (bm.tiempoNativoMsExacto !== null) {
              <div class="bar-item">
                <span class="bar-label">C Nativo (fin_tir)</span>
                <div class="bar-track">
                  <div class="bar-fill top" [style.width.%]="pct(bm.tiempoNativoMsExacto)"></div>
                </div>
                <span class="bar-value">{{ bm.tiempoNativoMsExacto | number: '1.2-2' }} ms</span>
              </div>
            }
            @if (bm.tiempoNativoPorIteracionMsExacto !== null) {
              <div class="bar-item">
                <span class="bar-label">C por iteración (fin_vpn)</span>
                <div class="bar-track">
                  <div class="bar-fill" [style.width.%]="pct(bm.tiempoNativoPorIteracionMsExacto)"></div>
                </div>
                <span class="bar-value">{{ bm.tiempoNativoPorIteracionMsExacto | number: '1.2-2' }} ms</span>
              </div>
            }
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

  readonly maxMs = computed(() => {
    const bm = this.benchmark();
    if (!bm) return 1;
    return Math.max(
      bm.tiempoJavaMsExacto || 0,
      bm.tiempoNativoMsExacto || 0,
      bm.tiempoNativoPorIteracionMsExacto || 0,
      1,
    );
  });

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

  pct(valor: number | null): number {
    if (!valor) return 0;
    return Math.max(4, Math.min(100, (valor / this.maxMs()) * 100));
  }
}
