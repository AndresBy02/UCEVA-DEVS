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
          <h2 class="page-title">Evaluación Financiera (VPN, TIR y Amortización)</h2>
          <p class="page-subtitle">
            Ejecutado íntegramente en <code>calculo-service</code> mediante <code>libfinanzas.so</code> (C11 / JNA) o respaldo Java configurable.
          </p>
        </div>
        <div class="actions-row">
          <button type="button" class="btn-outline btn-sm" (click)="cargarCasoObra90Dias()">Obra 90d (CR-11)</button>
          <button type="button" class="btn-outline btn-sm" (click)="cargarCasoSinCambioSigno()">Sin cambio signo (CR-12)</button>
          <button type="button" class="btn-outline btn-sm" (click)="cargarCasoNoConvergente()">No convergente (CR-13)</button>
          <button type="button" class="btn-outline btn-sm" (click)="cargarCasoMultiTir()">Múltiple TIR</button>
        </div>
      </div>

      @if (errorMsg()) {
        <div class="alert-error">{{ errorMsg() }}</div>
      }

      <div class="split-wide-left">
        <form [formGroup]="form" (ngSubmit)="evaluar()" class="card">
          <div class="header-row" style="margin-bottom: 10px;">
            <div>
              <h3>Flujo de caja del trabajo</h3>
              <p class="page-subtitle">Anticipo, actas parciales y pago final ({{ flujosArray.length }} periodos)</p>
            </div>
            <button type="button" class="btn-outline btn-sm" (click)="agregarFlujo(0)">+ Agregar periodo</button>
          </div>

          <table>
            <thead>
              <tr>
                <th>Periodo</th>
                <th>Concepto</th>
                <th class="num">Flujo neto (COP)</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (ctrl of flujosArray.controls; track $index) {
                <tr>
                  <td><strong>t = {{ $index }}</strong></td>
                  <td><small style="color: var(--muted);">{{ $index === 0 ? 'Inversión / Anticipo' : 'Flujo periodo ' + $index }}</small></td>
                  <td class="num">
                    <input type="number" step="0.01" [formControl]="ctrl" style="width: 150px; text-align: right;" />
                  </td>
                  <td class="num">
                    @if (flujosArray.length > 2) {
                      <button type="button" class="btn-outline btn-sm" (click)="quitarFlujo($index)">×</button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>

          <div class="grid-form" style="margin-top: 14px;">
            <label>
              Tasa de descuento (ej. 0.015 = 1.5%)
              <input type="number" step="0.0001" formControlName="tasaDescuento" />
            </label>
            <label>
              Tolerancia TIR
              <input type="number" step="0.000000001" formControlName="toleranciaTir" />
            </label>
            <label>
              Máximo iteraciones TIR
              <input type="number" step="1" formControlName="maxIteracionesTir" />
            </label>
          </div>

          <div style="margin-top: 12px;">
            <label style="display: inline-flex; align-items: center; gap: 8px; font-size: 0.9rem; font-weight: 500;">
              <input type="checkbox" formControlName="incluirFinanciacion" />
              Incluir tabla de amortización de financiación (Sistema Francés)
            </label>
          </div>

          @if (form.controls.incluirFinanciacion.value) {
            <div class="chapter-block" formGroupName="financiacion" style="margin-top: 12px;">
              <h4>Parámetros de financiación (C nativo)</h4>
              <div class="grid-form">
                <label>
                  Capital financiado (COP)
                  <input type="number" step="0.01" formControlName="capital" />
                </label>
                <label>
                  Tasa por periodo
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

        <div style="display: flex; flex-direction: column; gap: 16px;">
          @if (resultado(); as res) {
            <div class="split-2col">
              <div class="stat-box" [class.highlight]="(res.vpn ?? 0) >= 0" [class.danger-box]="(res.vpn ?? 0) < 0">
                <span>Valor Presente Neto (VPN)</span>
                <strong>{{ res.vpn !== null ? '$ ' + (res.vpn | number: '1.2-2') : 'No calculable' }}</strong>
                <small>COP descontado a tasa indicada</small>
              </div>

              @if (res.tir.convergio) {
                <div class="stat-box success-box">
                  <span>TIR por periodo</span>
                  <strong>{{ res.tir.tasaInternaRetornoPorcentaje | number: '1.4-4' }}%</strong>
                  <small>Iteraciones: {{ res.tir.iteraciones }} · Factor: {{ res.tir.tasaInternaRetorno | number: '1.6-6' }}</small>
                </div>
              } @else {
                <div class="stat-box danger-box">
                  <span>TIR (Código C: {{ res.tir.codigoRetornoC }})</span>
                  <strong>{{ res.tir.estado }}</strong>
                  <small>Iteraciones: {{ res.tir.iteraciones }}</small>
                </div>
              }
            </div>

            <div class="card">
              <div class="header-row" style="margin-bottom: 6px;">
                <h4>Ruta de cálculo</h4>
                <span class="badge-approved">{{ res.motorUtilizado }}</span>
              </div>
              <small style="color: var(--muted);">
                Toda iteración de TIR se encapsula en la frontera nativa (o respaldo Java equivalente).
              </small>

              @if (!res.tir.convergio) {
                <div class="alert-warning" style="margin-top: 12px;">
                  <div>
                    <strong>Resultado TIR: {{ res.tir.estado }}</strong> — {{ res.tir.mensaje }}
                    (Código retorno C: <code>{{ res.tir.codigoRetornoC }}</code>, iteraciones: {{ res.tir.iteraciones }}).
                    <em>El sistema nunca reporta 0% como resultado falso cuando no existe convergencia.</em>
                  </div>
                </div>
              }
            </div>
          } @else {
            <div class="card">
              <h4>Resultados financieros</h4>
              <p style="color: var(--muted); margin-top: 6px;">
                Ajusta los flujos o selecciona un caso de referencia arriba y pulsa
                <strong>Ejecutar Evaluación Financiera</strong> para calcular VPN, TIR y la tabla de amortización en el servidor.
              </p>
            </div>
          }
        </div>
      </div>

      @if (resultado()?.amortizacion; as amort) {
        <div class="card">
          <div class="header-row" style="margin-bottom: 10px;">
            <div>
              <h3>Amortización de la financiación (Sistema Francés · Llamada Única JNA)</h3>
              <p class="page-subtitle">
                Capital: <strong>{{ amort.capital | number: '1.2-2' }} COP</strong> ·
                Cuota fija ref.: <strong>{{ amort.cuotaFija | number: '1.2-2' }} COP</strong> ·
                Periodos: <strong>{{ amort.periodos }}</strong>
              </p>
            </div>
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
