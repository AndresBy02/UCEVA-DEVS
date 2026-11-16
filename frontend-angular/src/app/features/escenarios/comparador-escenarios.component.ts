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
      <h2>Comparar Dos Escenarios Lado a Lado</h2>

      @if (errorMsg()) {
        <div class="alert-error">{{ errorMsg() }}</div>
      }

      <div class="card">
        <div class="grid-form">
          <label>
            Escenario A
            <select [(ngModel)]="idA" name="idA">
              <option value="">Seleccione escenario A</option>
              @for (p of presupuestos(); track p.id) {
                <option [value]="p.id">{{ p.codigo }} - {{ p.nombre }}</option>
              }
            </select>
          </label>
          <label>
            Escenario B
            <select [(ngModel)]="idB" name="idB">
              <option value="">Seleccione escenario B</option>
              @for (p of presupuestos(); track p.id) {
                <option [value]="p.id">{{ p.codigo }} - {{ p.nombre }}</option>
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

      @if (resultado()) {
        <div class="card">
          <h3>Resumen Comparativo</h3>
          <table>
            <thead>
              <tr>
                <th>Concepto</th>
                <th>{{ resultado()!.escenarioA.codigo }} (A)</th>
                <th>{{ resultado()!.escenarioB.codigo }} (B)</th>
                <th>Diferencia Absoluta (COP)</th>
                <th>Diferencia (%)</th>
              </tr>
            </thead>
            <tbody>
              @for (cap of resultado()!.capitulos; track cap.codigoCapitulo) {
                <tr>
                  <td>{{ cap.codigoCapitulo }} - {{ cap.nombreCapitulo }}</td>
                  <td class="num">{{ cap.subtotalEscenarioA | number: '1.2-2' }}</td>
                  <td class="num">{{ cap.subtotalEscenarioB | number: '1.2-2' }}</td>
                  <td class="num">{{ cap.diferenciaAbsoluta | number: '1.2-2' }}</td>
                  <td class="num">{{ cap.diferenciaPorcentual | number: '1.2-2' }}%</td>
                </tr>
              }
              <tr class="total-row">
                <td><strong>Costo Directo Total</strong></td>
                <td class="num"><strong>{{ resultado()!.escenarioA.costoDirecto | number: '1.2-2' }}</strong></td>
                <td class="num"><strong>{{ resultado()!.escenarioB.costoDirecto | number: '1.2-2' }}</strong></td>
                <td class="num"><strong>{{ resultado()!.comparacionTotal.diferenciaCostoDirectoAbsoluta | number: '1.2-2' }}</strong></td>
                <td class="num"><strong>{{ resultado()!.comparacionTotal.diferenciaCostoDirectoPorcentual | number: '1.2-2' }}%</strong></td>
              </tr>
              <tr class="total-row">
                <td><strong>Precio de Venta Total (CD + AIU)</strong></td>
                <td class="num"><strong>{{ resultado()!.escenarioA.precioVenta | number: '1.2-2' }}</strong></td>
                <td class="num"><strong>{{ resultado()!.escenarioB.precioVenta | number: '1.2-2' }}</strong></td>
                <td class="num"><strong>{{ resultado()!.comparacionTotal.diferenciaPrecioVentaAbsoluta | number: '1.2-2' }}</strong></td>
                <td class="num"><strong>{{ resultado()!.comparacionTotal.diferenciaPrecioVentaPorcentual | number: '1.2-2' }}%</strong></td>
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
