import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { PresupuestoResumen } from '../../core/models/api.models';
import { PresupuestoApiService } from '../../core/services/presupuesto-api.service';
import { extraerErrorHttp } from '../../core/utils/http-error.util';

@Component({
  selector: 'app-revision-queue',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <section class="page-section">
      <div class="header-row">
        <div>
          <h2>Bandeja de Presupuestos en Revisión</h2>
          <p style="color: var(--muted); margin: 4px 0 0;">
            Vista de solo lectura para revisión, aprobación o devolución con motivo (<code>UC-43</code>, <code>UC-44</code>, <code>UC-45</code>).
          </p>
        </div>
        <button type="button" class="btn-secondary" (click)="cargar()">Actualizar Bandeja</button>
      </div>

      @if (errorMsg()) {
        <div class="alert-error">{{ errorMsg() }}</div>
      }
      @if (successMsg()) {
        <div class="alert-success">{{ successMsg() }}</div>
      }

      <div class="card">
        @if (cargando() && pendientes().length === 0) {
          <p style="color: var(--muted);">Consultando presupuestos en revisión...</p>
        }

        <table>
          <thead>
            <tr>
              <th>Código</th>
              <th>Nombre</th>
              <th>Cliente</th>
              <th>Estado</th>
              <th>Enviado Por (sub)</th>
              <th>Capítulos / Ítems</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (p of pendientes(); track p.id) {
              <tr>
                <td><code>{{ p.codigo }}</code></td>
                <td>{{ p.nombre }}</td>
                <td>{{ p.cliente }}</td>
                <td><span class="badge-review">{{ p.estado }}</span></td>
                <td><small>{{ p.enviadoRevisionPor || p.creadoPor }}</small></td>
                <td>{{ p.totalCapitulos }} / {{ p.totalItems }}</td>
                <td class="actions-cell">
                  <a [routerLink]="['/presupuestos', p.id]" class="btn-primary">Inspeccionar (Solo Lectura)</a>
                  @if (authService.isRevisor()) {
                    <button type="button" class="btn-secondary" (click)="aprobarRapido(p.id)">Aprobar</button>
                    <button type="button" class="btn-secondary" (click)="abrirRechazo(p)">Rechazar</button>
                  }
                </td>
              </tr>
            } @empty {
              @if (!cargando()) {
                <tr>
                  <td colspan="7" style="color: var(--muted);">
                    No hay presupuestos pendientes en revisión en este momento.
                  </td>
                </tr>
              }
            }
          </tbody>
        </table>
      </div>

      @if (rechazando(); as target) {
        <div class="card">
          <h3>Rechazar Presupuesto {{ target.codigo }} · {{ target.nombre }}</h3>
          <form (ngSubmit)="confirmarRechazo()" class="grid-form">
            <label style="grid-column: 1 / -1;">
              Motivo del rechazo (obligatorio)
              <input [(ngModel)]="motivoRechazo" name="motivo" required
                     placeholder="Indique qué capítulos, ítems o porcentajes deben corregirse..." />
            </label>
            <div class="actions-row">
              <button type="submit" class="btn-primary">Devolver a Borrador</button>
              <button type="button" class="btn-secondary" (click)="rechazando.set(null)">Cancelar</button>
            </div>
          </form>
        </div>
      }
    </section>
  `,
})
export class RevisionQueueComponent implements OnInit {
  readonly authService = inject(AuthService);
  private readonly api = inject(PresupuestoApiService);

  readonly pendientes = signal<PresupuestoResumen[]>([]);
  readonly cargando = signal<boolean>(false);
  readonly errorMsg = signal<string | null>(null);
  readonly successMsg = signal<string | null>(null);
  readonly rechazando = signal<PresupuestoResumen | null>(null);

  motivoRechazo = '';

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.errorMsg.set(null);
    this.api.listarEnRevision().subscribe({
      next: (lista) => {
        this.pendientes.set(lista);
        this.cargando.set(false);
      },
      error: (err) => {
        this.cargando.set(false);
        this.errorMsg.set(extraerErrorHttp(err));
      },
    });
  }

  aprobarRapido(id: string): void {
    this.errorMsg.set(null);
    this.successMsg.set(null);
    this.api.aprobar(id).subscribe({
      next: (det) => {
        this.successMsg.set(`Presupuesto ${det.codigo} aprobado e inmovilizado.`);
        this.cargar();
      },
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  abrirRechazo(p: PresupuestoResumen): void {
    this.rechazando.set(p);
    this.motivoRechazo = '';
  }

  confirmarRechazo(): void {
    const target = this.rechazando();
    if (!target || !this.motivoRechazo.trim()) {
      this.errorMsg.set('Debes ingresar un motivo para rechazar el presupuesto.');
      return;
    }
    this.errorMsg.set(null);
    this.successMsg.set(null);
    this.api.rechazar(target.id, this.motivoRechazo.trim()).subscribe({
      next: (det) => {
        this.rechazando.set(null);
        this.motivoRechazo = '';
        this.successMsg.set(`Presupuesto ${det.codigo} devuelto a BORRADOR con motivo registrado.`);
        this.cargar();
      },
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }
}
