import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { PresupuestoResumen } from '../../core/models/api.models';
import { PresupuestoApiService } from '../../core/services/presupuesto-api.service';
import { extraerErrorHttp } from '../../core/utils/http-error.util';

@Component({
  selector: 'app-inicio',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="page-section">
      <div class="header-row">
        <div>
          <h2 class="page-title">Inicio · Panel de Control</h2>
          <p class="page-subtitle">Resumen de presupuestos, revisiones pendientes y accesos rápidos</p>
        </div>
        <div class="actions-row">
          @if (authService.isPresupuestador()) {
            <a routerLink="/presupuestos" class="btn-primary">+ Nuevo o ver presupuestos</a>
          } @else {
            <a routerLink="/revision" class="btn-primary">Ir a cola de revisión</a>
          }
        </div>
      </div>

      @if (errorMsg()) {
        <div class="alert-error">{{ errorMsg() }}</div>
      }

      <div class="kpi-grid">
        <div class="stat-box">
          <span>Borradores</span>
          <strong>{{ borradores().length }}</strong>
          <small>Presupuestos editables</small>
        </div>
        <div class="stat-box highlight">
          <span>En Revisión</span>
          <strong>{{ enRevision().length }}</strong>
          <small>Bloqueados esperando decisión</small>
        </div>
        <div class="stat-box success-box">
          <span>Aprobados</span>
          <strong>{{ aprobados().length }}</strong>
          <small>Inmovilizados permanentemente</small>
        </div>
        <div class="stat-box" [class.danger-box]="devueltos().length > 0">
          <span>Devueltos con Motivo</span>
          <strong>{{ devueltos().length }}</strong>
          <small>Requieren ajuste en borrador</small>
        </div>
      </div>

      <div class="split-2col">
        <div class="card">
          <div class="header-row" style="margin-bottom: 10px;">
            <h3>Requiere tu atención</h3>
            <span class="badge-review">{{ pendientesAtencion().length }} aviso(s)</span>
          </div>

          @if (cargando() && presupuestos().length === 0) {
            <p style="color: var(--muted);">Consultando actividad en servidor...</p>
          } @else if (pendientesAtencion().length === 0) {
            <p style="color: var(--muted);">Todo al día: no hay presupuestos devueltos ni en revisión pendientes.</p>
          } @else {
            <div style="display: flex; flex-direction: column; gap: 10px;">
              @for (item of pendientesAtencion(); track item.id) {
                <div class="detail-kv" style="align-items: center;">
                  <div>
                    @if (item.estado === 'EN_REVISION') {
                      <span class="badge-review" style="margin-right: 8px;">En revisión</span>
                    } @else {
                      <span class="badge-rejected" style="margin-right: 8px;">Rechazado</span>
                    }
                    <strong>{{ item.codigo }}</strong> · {{ item.nombre }}
                    @if (item.motivoRechazo) {
                      <div><small style="color: var(--error-text);">Motivo: "{{ item.motivoRechazo }}"</small></div>
                    }
                  </div>
                  <a [routerLink]="['/presupuestos', item.id]" class="btn-outline btn-sm">Abrir</a>
                </div>
              }
            </div>
          }
        </div>

        <div class="card">
          <h3 style="margin-bottom: 10px;">Actividad reciente</h3>
          <table>
            <thead>
              <tr>
                <th>Código</th>
                <th>Nombre</th>
                <th>Estado</th>
                <th>Cap. / Ítems</th>
              </tr>
            </thead>
            <tbody>
              @for (p of presupuestos().slice(0, 6); track p.id) {
                <tr>
                  <td><a [routerLink]="['/presupuestos', p.id]"><code>{{ p.codigo }}</code></a></td>
                  <td>{{ p.nombre }}</td>
                  <td>
                    <span [class]="p.estado === 'APROBADO' ? 'badge-approved' : (p.estado === 'EN_REVISION' ? 'badge-review' : 'badge-draft')">
                      {{ p.estado }}
                    </span>
                  </td>
                  <td>{{ p.totalCapitulos }} / {{ p.totalItems }}</td>
                </tr>
              } @empty {
                @if (!cargando()) {
                  <tr>
                    <td colspan="4" style="color: var(--muted);">Sin presupuestos registrados aún.</td>
                  </tr>
                }
              }
            </tbody>
          </table>
        </div>
      </div>

      <div class="card">
        <h3 style="margin-bottom: 12px;">Accesos rápidos</h3>
        <div class="actions-row">
          <a routerLink="/presupuestos" class="btn-outline">Mis presupuestos</a>
          <a routerLink="/insumos" class="btn-outline">Catálogo de insumos</a>
          <a routerLink="/escenarios/comparar" class="btn-outline">Comparador A vs B</a>
          <a routerLink="/evaluacion-financiera" class="btn-outline">Evaluación VPN / TIR</a>
          <a routerLink="/revision" class="btn-outline">Bandeja en revisión</a>
          <a routerLink="/benchmark-jna" class="btn-outline">Benchmark JNA</a>
        </div>
      </div>
    </section>
  `,
})
export class InicioComponent implements OnInit {
  readonly authService = inject(AuthService);
  private readonly api = inject(PresupuestoApiService);

  readonly presupuestos = signal<PresupuestoResumen[]>([]);
  readonly cargando = signal<boolean>(false);
  readonly errorMsg = signal<string | null>(null);

  readonly borradores = computed(() => this.presupuestos().filter((p) => p.estado === 'BORRADOR'));
  readonly enRevision = computed(() => this.presupuestos().filter((p) => p.estado === 'EN_REVISION'));
  readonly aprobados = computed(() => this.presupuestos().filter((p) => p.estado === 'APROBADO'));
  readonly devueltos = computed(() => this.presupuestos().filter((p) => !!p.motivoRechazo && p.estado === 'BORRADOR'));
  readonly pendientesAtencion = computed(() =>
    this.presupuestos().filter((p) => p.estado === 'EN_REVISION' || (!!p.motivoRechazo && p.estado === 'BORRADOR')),
  );

  ngOnInit(): void {
    this.cargando.set(true);
    this.api.listar().subscribe({
      next: (lista) => {
        this.presupuestos.set(lista);
        this.cargando.set(false);
      },
      error: (err) => {
        this.cargando.set(false);
        this.errorMsg.set(extraerErrorHttp(err));
      },
    });
  }
}
