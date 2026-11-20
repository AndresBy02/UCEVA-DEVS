import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { EvaluacionFinancieraResponse } from '../../core/models/api.models';
import { CalculoApiService } from '../../core/services/calculo-api.service';
import { extraerErrorHttp } from '../../core/utils/http-error.util';

@Component({
  selector: 'app-evaluacion-financiera',
  standalone: true,
  imports: [ReactiveFormsModule, DecimalPipe],
  template: `
    <section class="page-section">
      <div class="header-row">
        <div>
          <h2>Evaluación Financiera de Obra (VPN, TIR y Amortización)</h2>
          <p style="color: var(--muted); margin: 4px 0 0;">
            Calculado íntegramente en <code>calculo-service</code> mediante <code>libfinanzas.so</code> (C / JNA) o respaldo Java configurado.
          </p>
        </div>
        <div class="actions-row">
          <button type="button" class="btn-secondary" (click)="cargarCasoObra90Dias()">Caso Obra 90d (CR-11)</button>
          <button type="button" class="btn-secondary" (click)="cargarCasoSinCambioSigno()">Sin cambio signo (CR-12)</button>
          <button type="button" class="btn-secondary" (click)="cargarCasoNoConvergente()">No convergente (CR-13)</button>
          <button type="button" class="btn-secondary" (click)="cargarCasoMultiTir()">Múltiple TIR</button>
        </div>
      </div>

      @if (errorMsg()) {
        <div class="alert-error">{{ errorMsg() }}</div>
      }

      <form [formGroup]="form" (ngSubmit)="evaluar()" class="card">
        <h3>Parámetros del Flujo de Caja y Financiación</h3>
        <div class="grid-form">
          <label>
            Tasa de descuento por periodo (ej. 0.015 = 1.5%)
            <input type="number" step="0.0001" formControlName="tasaDescuento" />
          </label>
          <label>
            Tolerancia TIR
            <input type="number" step="0.000000001" formControlName="toleranciaTir" />
          </label>
          <label>
            Máximo de iteraciones TIR
            <input type="number" step="1" formControlName="maxIteracionesTir" />
          </label>
          <label style="flex-direction: row; align-items: center; gap: 8px; padding-top: 20px;">
            <input type="checkbox" formControlName="incluirFinanciacion" />
            Incluir tabla de amortización (financiación)
          </label>
        </div>

        <div style="margin-top: 16px;">
          <div class="header-row">
            <h4>Flujos de Caja (t = 0 .. {{ flujosArray.length - 1 }})</h4>
            <button type="button" class="btn-secondary" (click)="agregarFlujo(0)">+ Agregar Periodo</button>
          </div>
          <div class="grid-form">
            @for (ctrl of flujosArray.controls; track $index) {
              <div class="stat-box">
                <div class="header-row">
                  <span>Periodo t={{ $index }} {{ $index === 0 ? '(Inversión / Anticipo)' : '' }}</span>
                  @if (flujosArray.length > 2) {
                    <button type="button" class="btn-secondary" style="padding: 2px 8px;" (click)="quitarFlujo($index)">x</button>
                  }
                </div>
                <input type="number" step="0.01" [formControl]="ctrl" />
              </div>
            }
          </div>
        </div>

        @if (form.controls.incluirFinanciacion.value) {
          <div class="chapter-block" formGroupName="financiacion" style="margin-top: 16px;">
            <h4>Datos de Financiación (Sistema Francés)</h4>
            <div class="grid-form">
              <label>
                Capital financiado (COP)
                <input type="number" step="0.01" formControlName="capital" />
              </label>
              <label>
                Tasa por periodo (ej. 0.02 = 2%)
                <input type="number" step="0.0001" formControlName="tasaPeriodo" />
              </label>
              <label>
                Número de periodos
                <input type="number" step="1" formControlName="periodos" />
              </label>
            </div>
          </div>
        }

        <div class="actions-row" style="margin-top: 16px;">
          <button type="submit" class="btn-primary" [disabled]="form.invalid || cargando()">
            {{ cargando() ? 'Evaluando en servidor...' : 'Ejecutar Evaluación Financiera' }}
          </button>
        </div>
      </form>

      @if (resultado(); as res) {
        <div class="card">
          <div class="header-row">
            <h3>Resultado de Evaluación Financiera</h3>
            <span class="badge-approved">Motor: {{ res.motorUtilizado }}</span>
          </div>

          <div class="summary-grid" style="margin-top: 12px;">
            <div class="stat-box highlight">
              <span>Valor Presente Neto (VPN)</span>
              <strong>{{ res.vpn !== null ? (res.vpn | number: '1.2-2') + ' COP' : 'No calculable' }}</strong>
            </div>
            <div class="stat-box">
              <span>Estado TIR (Código C: {{ res.tir.codigoRetornoC }})</span>
              <strong>{{ res.tir.estado }}</strong>
              <small>Iteraciones ejecutadas: {{ res.tir.iteraciones }}</small>
            </div>
            @if (res.tir.convergio) {
              <div class="stat-box highlight">
                <span>Tasa Interna de Retorno (TIR)</span>
                <strong>{{ res.tir.tasaInternaRetornoPorcentaje | number: '1.4-4' }}%</strong>
                <small>Factor decimal: {{ res.tir.tasaInternaRetorno | number: '1.6-6' }}</small>
              </div>
            }
          </div>

          @if (!res.tir.convergio) {
            <div class="alert-warning" style="margin-top: 14px;">
              <strong>Resultado TIR: {{ res.tir.estado }}</strong> — {{ res.tir.mensaje }}
              (Código retorno C: <code>{{ res.tir.codigoRetornoC }}</code>, iteraciones: {{ res.tir.iteraciones }}).
              <em>El sistema nunca reporta 0% como resultado falso cuando no existe convergencia.</em>
            </div>
          }
        </div>

        @if (res.amortizacion; as amort) {
          <div class="card">
            <div class="header-row">
              <h3>Tabla de Amortización (Sistema Francés — Llamada Única JNA)</h3>
              <span>
                Capital: <strong>{{ amort.capital | number: '1.2-2' }} COP</strong> ·
                Cuota Fija Ref.: <strong>{{ amort.cuotaFija | number: '1.2-2' }} COP</strong> ·
                Periodos: <strong>{{ amort.periodos }}</strong>
              </span>
            </div>
            <table>
              <thead>
                <tr>
                  <th>Periodo</th>
                  <th class="num">Cuota (COP)</th>
                  <th class="num">Interés (COP)</th>
                  <th class="num">Abono a Capital (COP)</th>
                  <th class="num">Saldo Final (COP)</th>
                </tr>
              </thead>
              <tbody>
                @for (fila of amort.filas; track fila.periodo) {
                  <tr>
                    <td>{{ fila.periodo }}</td>
                    <td class="num">{{ fila.cuota | number: '1.2-2' }}</td>
                    <td class="num">{{ fila.interes | number: '1.2-2' }}</td>
                    <td class="num">{{ fila.abonoCapital | number: '1.2-2' }}</td>
                    <td class="num"><strong>{{ fila.saldo | number: '1.2-2' }}</strong></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      }
    </section>
  `,
})
export class EvaluacionFinancieraComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly calculoApi = inject(CalculoApiService);

  readonly cargando = signal<boolean>(false);
  readonly errorMsg = signal<string | null>(null);
  readonly resultado = signal<EvaluacionFinancieraResponse | null>(null);

  readonly form = this.fb.group({
    tasaDescuento: this.fb.nonNullable.control(0.015, [Validators.required, Validators.min(-0.99)]),
    toleranciaTir: this.fb.nonNullable.control(1e-9, [Validators.required, Validators.min(1e-14)]),
    maxIteracionesTir: this.fb.nonNullable.control(1000, [Validators.required, Validators.min(1)]),
    incluirFinanciacion: this.fb.nonNullable.control(true),
    flujos: this.fb.array<FormControl<number>>([]),
    financiacion: this.fb.group({
      capital: this.fb.nonNullable.control(10000, [Validators.required, Validators.min(1)]),
      tasaPeriodo: this.fb.nonNullable.control(0.02, [Validators.required, Validators.min(0)]),
      periodos: this.fb.nonNullable.control(3, [Validators.required, Validators.min(1), Validators.max(360)]),
    }),
  });

  get flujosArray(): FormArray<FormControl<number>> {
    return this.form.controls.flujos;
  }

  ngOnInit(): void {
    const inversionParam = Number(this.route.snapshot.queryParamMap.get('inversion'));
    if (!Number.isNaN(inversionParam) && inversionParam > 0) {
      const inv = Math.round(inversionParam * 100) / 100;
      const anticipo = -inv;
      const a1 = Math.round(inv * 0.35 * 100) / 100;
      const a2 = Math.round(inv * 0.40 * 100) / 100;
      const a3 = Math.round(inv * 0.45 * 100) / 100;
      this.reemplazarFlujos([anticipo, a1, a2, a3]);
      this.form.controls.financiacion.patchValue({ capital: inv, tasaPeriodo: 0.018, periodos: 3 });
    } else {
      this.cargarCasoObra90Dias();
    }
  }

  agregarFlujo(valor = 0): void {
    this.flujosArray.push(this.fb.nonNullable.control(valor, [Validators.required]));
  }

  quitarFlujo(idx: number): void {
    if (this.flujosArray.length > 2) {
      this.flujosArray.removeAt(idx);
    }
  }

  cargarCasoObra90Dias(): void {
    this.form.patchValue({
      tasaDescuento: 0.015,
      toleranciaTir: 1e-9,
      maxIteracionesTir: 1000,
      incluirFinanciacion: true,
    });
    this.reemplazarFlujos([-10000, 3500, 4000, 4500]);
    this.form.controls.financiacion.patchValue({ capital: 10000, tasaPeriodo: 0.02, periodos: 3 });
  }

  cargarCasoSinCambioSigno(): void {
    this.form.patchValue({
      tasaDescuento: 0.01,
      toleranciaTir: 1e-9,
      maxIteracionesTir: 1000,
      incluirFinanciacion: false,
    });
    this.reemplazarFlujos([-1000, -500, -200]);
  }

  cargarCasoNoConvergente(): void {
    this.form.patchValue({
      tasaDescuento: 0.01,
      toleranciaTir: 1e-12,
      maxIteracionesTir: 50,
      incluirFinanciacion: false,
    });
    this.reemplazarFlujos([-1000, 500, -500]);
  }

  cargarCasoMultiTir(): void {
    this.form.patchValue({
      tasaDescuento: 0.10,
      toleranciaTir: 1e-9,
      maxIteracionesTir: 1000,
      incluirFinanciacion: false,
    });
    this.reemplazarFlujos([-1000, 2500, -1550]);
  }

  evaluar(): void {
    if (this.form.invalid) return;
    this.errorMsg.set(null);
    this.cargando.set(true);

    const raw = this.form.getRawValue();
    this.calculoApi
      .evaluarProyecto({
        tasaDescuento: raw.tasaDescuento,
        flujosCaja: raw.flujos,
        toleranciaTir: raw.toleranciaTir,
        maxIteracionesTir: raw.maxIteracionesTir,
        financiacion: raw.incluirFinanciacion ? raw.financiacion : null,
      })
      .subscribe({
        next: (res) => {
          this.resultado.set(res);
          this.cargando.set(false);
        },
        error: (err) => {
          this.cargando.set(false);
          this.errorMsg.set(extraerErrorHttp(err));
        },
      });
  }

  private reemplazarFlujos(valores: number[]): void {
    this.flujosArray.clear();
    for (const v of valores) {
      this.agregarFlujo(v);
    }
  }
}
