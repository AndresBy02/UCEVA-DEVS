import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ComparacionEscenarios, PresupuestoResumen } from '../../core/models/api.models';
import { PresupuestoApiService } from '../../core/services/presupuesto-api.service';
import { extraerErrorHttp } from '../../core/utils/http-error.util';

@Component({
  selector: 'app-comparador-escenarios',
  standalone: true,
  imports: [FormsModule, DecimalPipe],
  template: `
    <section class="page-section">
      <div>
        <h2 class="page-title">Comparar Escenarios A / B</h2>
        <p class="page-subtitle">Contraste exacto por capítulo, costo directo y precio de venta entre alternativas.</p>
      </div>

      <div class="alert-info">
        El escenario B es una copia independiente del A. Modificar cantidades, precios o AIU en B no altera ninguna cifra de A.
      </div>

      @if (errorMsg()) {
        <div class="alert-error">{{ errorMsg() }}</div>
      }

      <div class="card">
        <div class="grid-form">
          <label>
            Escenario A (Base)
            <select [(ngModel)]="idA" name="idA">
              <option value="">Seleccione escenario A</option>
              @for (p of presupuestos(); track p.id) {
                <option [value]="p.id">{{ p.codigo }} · {{ p.nombre }}</option>
              }
            </select>
          </label>
          <label>
            Escenario B (Alternativo)
            <select [(ngModel)]="idB" name="idB">
              <option value="">Seleccione escenario B</option>
              @for (p of presupuestos(); track p.id) {
                <option [value]="p.id">{{ p.codigo }} · {{ p.nombre }}</option>
              }
            </select>
          </label>
          <div class="actions-row">
            <button type="button" class="btn-primary" (click)="comparar()" [disabled]="!idA || !idB || cargando()">
              {{ cargando() ? 'Comparando...' : 'Comparar A vs B' }}
            </button>
          </div>
        </div>
        @if (cargando() && presupuestos().length === 0) {
          <p style="color: var(--muted); margin-top: 10px;">Cargando presupuestos disponibles...</p>
        } @else if (!cargando() && presupuestos().length < 2) {
          <p style="color: var(--muted); margin-top: 10px;">Se requieren al menos dos presupuestos o escenarios para comparar.</p>
        }
      </div>

      @if (resultado(); as res) {
        <div class="kpi-grid">
          <div class="stat-box">
            <span>Costo Directo (A -> B)</span>
            <strong>{{ res.escenarioA.costoDirecto | number: '1.2-2' }} -> {{ res.escenarioB.costoDirecto | number: '1.2-2' }}</strong>
            <small>Dif.: {{ res.comparacionTotal.diferenciaCostoDirectoAbsoluta | number: '1.2-2' }} COP ({{ res.comparacionTotal.diferenciaCostoDirectoPorcentual | number: '1.2-2' }}%)</small>
          </div>
          <div class="stat-box highlight">
            <span>Precio de Venta Total (A -> B)</span>
            <strong>{{ res.escenarioA.precioVenta | number: '1.2-2' }} -> {{ res.escenarioB.precioVenta | number: '1.2-2' }}</strong>
            <small>Dif.: {{ res.comparacionTotal.diferenciaPrecioVentaAbsoluta | number: '1.2-2' }} COP</small>
          </div>
          <div class="stat-box">
            <span>Variación Precio Venta</span>
            <strong [class]="res.comparacionTotal.diferenciaPrecioVentaPorcentual > 0 ? 'delta-up' : 'delta-down'">
              {{ res.comparacionTotal.diferenciaPrecioVentaPorcentual > 0 ? '+' : '' }}{{ res.comparacionTotal.diferenciaPrecioVentaPorcentual | number: '1.2-2' }}%
            </strong>
            <small>{{ res.escenarioA.codigo }} frente a {{ res.escenarioB.codigo }}</small>
          </div>
        </div>

        <div class="card">
          <h3 style="margin-bottom: 10px;">Desglose comparativo por capítulo</h3>
          <table>
            <thead>
              <tr>
                <th>Capítulo</th>
                <th class="num">{{ res.escenarioA.codigo }} (A)</th>
                <th class="num">{{ res.escenarioB.codigo }} (B)</th>
                <th class="num">Dif. Absoluta (COP)</th>
                <th class="num">Dif. (%)</th>
              </tr>
            </thead>
            <tbody>
              @for (cap of res.capitulos; track cap.codigoCapitulo) {
                <tr>
                  <td><strong>{{ cap.codigoCapitulo }}</strong> · {{ cap.nombreCapitulo }}</td>
                  <td class="num">{{ cap.subtotalEscenarioA | number: '1.2-2' }}</td>
                  <td class="num">{{ cap.subtotalEscenarioB | number: '1.2-2' }}</td>
                  <td class="num" [class]="cap.diferenciaAbsoluta > 0 ? 'delta-up' : (cap.diferenciaAbsoluta < 0 ? 'delta-down' : '')">
                    {{ cap.diferenciaAbsoluta > 0 ? '+' : '' }}{{ cap.diferenciaAbsoluta | number: '1.2-2' }}
                  </td>
                  <td class="num" [class]="cap.diferenciaPorcentual > 0 ? 'delta-up' : (cap.diferenciaPorcentual < 0 ? 'delta-down' : '')">
                    {{ cap.diferenciaPorcentual > 0 ? '+' : '' }}{{ cap.diferenciaPorcentual | number: '1.2-2' }}%
                  </td>
                </tr>
              }
              <tr class="total-row">
                <td><strong>Costo Directo Total</strong></td>
                <td class="num"><strong>{{ res.escenarioA.costoDirecto | number: '1.2-2' }}</strong></td>
                <td class="num"><strong>{{ res.escenarioB.costoDirecto | number: '1.2-2' }}</strong></td>
                <td class="num"><strong>{{ res.comparacionTotal.diferenciaCostoDirectoAbsoluta | number: '1.2-2' }}</strong></td>
                <td class="num"><strong>{{ res.comparacionTotal.diferenciaCostoDirectoPorcentual | number: '1.2-2' }}%</strong></td>
              </tr>
              <tr class="total-row">
                <td><strong>Precio de Venta Total (CD + AIU)</strong></td>
                <td class="num"><strong>{{ res.escenarioA.precioVenta | number: '1.2-2' }}</strong></td>
                <td class="num"><strong>{{ res.escenarioB.precioVenta | number: '1.2-2' }}</strong></td>
                <td class="num"><strong>{{ res.comparacionTotal.diferenciaPrecioVentaAbsoluta | number: '1.2-2' }}</strong></td>
                <td class="num"><strong>{{ res.comparacionTotal.diferenciaPrecioVentaPorcentual | number: '1.2-2' }}%</strong></td>
              </tr>
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
})
export class ComparadorEscenariosComponent implements OnInit {
  private readonly api = inject(PresupuestoApiService);

  readonly presupuestos = signal<PresupuestoResumen[]>([]);
  readonly resultado = signal<ComparacionEscenarios | null>(null);
  readonly cargando = signal<boolean>(false);
  readonly errorMsg = signal<string | null>(null);

  idA = '';
  idB = '';

  ngOnInit(): void {
    this.cargando.set(true);
    this.api.listar().subscribe({
      next: (lista) => {
        this.presupuestos.set(lista);
        this.cargando.set(false);
      },
      error: (err) => {
        this.cargando.set(false);
        this.errorMsg.set(extraerErrorHttp(err, 'Error cargando escenarios'));
      },
    });
  }

  comparar(): void {
    if (!this.idA || !this.idB) return;
    this.errorMsg.set(null);
    this.cargando.set(true);
    this.api.compararEscenarios(this.idA, this.idB).subscribe({
      next: (res) => {
        this.resultado.set(res);
        this.cargando.set(false);
      },
      error: (err) => {
        this.cargando.set(false);
        this.errorMsg.set(extraerErrorHttp(err, 'Error comparando escenarios'));
      },
    });
  }
}
