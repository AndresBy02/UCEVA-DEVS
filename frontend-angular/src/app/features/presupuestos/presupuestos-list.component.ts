import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { PresupuestoResumen } from '../../core/models/api.models';
import { PresupuestoApiService } from '../../core/services/presupuesto-api.service';
import { extraerErrorHttp } from '../../core/utils/http-error.util';

@Component({
  selector: 'app-presupuestos-list',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <section class="page-section">
      <div class="header-row">
        <div>
          <h2 class="page-title">Mis Presupuestos</h2>
          <p class="page-subtitle">Solo ves los presupuestos permitidos por tu rol. El estado Aprobado es de solo lectura.</p>
        </div>
        <div class="actions-row">
          <a routerLink="/escenarios/comparar" class="btn-outline">Comparar Escenarios</a>
          @if (authService.isPresupuestador()) {
            <button type="button" class="btn-primary" (click)="mostrarNuevoModal.set(true)">
              + Nuevo presupuesto
            </button>
          }
        </div>
      </div>

      @if (errorMsg()) {
        <div class="alert-error">{{ errorMsg() }}</div>
      }

      <div class="kpi-grid">
        <div class="stat-box">
          <span>Total</span>
          <strong>{{ presupuestos().length }}</strong>
          <small>Presupuestos registrados</small>
        </div>
        <div class="stat-box">
          <span>Borradores</span>
          <strong>{{ borradoresCount() }}</strong>
          <small>Editables</small>
        </div>
        <div class="stat-box highlight">
          <span>En Revisión</span>
          <strong>{{ enRevisionCount() }}</strong>
          <small>Bloqueados esperando revisor</small>
        </div>
        <div class="stat-box success-box">
          <span>Aprobados</span>
          <strong>{{ aprobadosCount() }}</strong>
          <small>Inmovilizados permanentemente</small>
        </div>
      </div>

      <div class="card">
        <div class="filter-bar" style="margin-bottom: 12px;">
          <input
            type="search"
            class="search-input"
            [ngModel]="busqueda()"
            (ngModelChange)="busqueda.set($event)"
            name="buscarPresupuesto"
            placeholder="Buscar por código, nombre o cliente..."
          />
          <select [ngModel]="estadoFiltro()" (ngModelChange)="estadoFiltro.set($event)" name="filtroEstado">
            <option value="">Todos los estados</option>
            <option value="BORRADOR">BORRADOR</option>
            <option value="EN_REVISION">EN_REVISION</option>
            <option value="APROBADO">APROBADO</option>
          </select>
          <span style="color: var(--muted); font-size: 0.84rem; margin-left: auto;">
            {{ presupuestosFiltrados().length }} de {{ presupuestos().length }}
          </span>
        </div>

        @if (cargando() && presupuestos().length === 0) {
          <p style="color: var(--muted);">Cargando presupuestos...</p>
        }

        <table>
          <thead>
            <tr>
              <th>Código</th>
              <th>Nombre</th>
              <th>Cliente</th>
              <th>Estado</th>
              <th>Capítulos / Ítems</th>
              <th>Creado Por (sub)</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (p of presupuestosFiltrados(); track p.id) {
              <tr>
                <td><code>{{ p.codigo }}</code></td>
                <td><strong>{{ p.nombre }}</strong></td>
                <td>{{ p.cliente }}</td>
                <td>
                  <span [class]="p.estado === 'APROBADO' ? 'badge-approved' : (p.estado === 'EN_REVISION' ? 'badge-review' : 'badge-draft')">
                    {{ p.estado }}
                  </span>
                  @if (p.motivoRechazo) {
                    <div style="margin-top: 4px;">
                      <span class="badge-rejected">Rechazado</span>
                      <small style="display: block; color: var(--error-text); margin-top: 2px;">{{ p.motivoRechazo }}</small>
                    </div>
                  }
                </td>
                <td>{{ p.totalCapitulos }} / {{ p.totalItems }}</td>
                <td><small>{{ p.creadoPor }}</small></td>
                <td class="actions-cell">
                  <a [routerLink]="['/presupuestos', p.id]" class="btn-primary btn-sm">Abrir</a>
                  @if (authService.isPresupuestador()) {
                    <button type="button" class="btn-outline btn-sm" (click)="abrirDuplicar(p)">
                      Duplicar B
                    </button>
                  }
                </td>
              </tr>
            } @empty {
              @if (!cargando()) {
                <tr>
                  <td colspan="7" style="color: var(--muted);">
                    No hay presupuestos que coincidan con el filtro aplicado.
                  </td>
                </tr>
              }
            }
          </tbody>
        </table>
      </div>
    </section>

    @if (mostrarNuevoModal() && authService.isPresupuestador()) {
      <div class="modal-backdrop" (click)="mostrarNuevoModal.set(false)">
        <div class="modal-dialog" (click)="$event.stopPropagation()">
          <div class="header-row">
            <div>
              <h3>Nuevo Presupuesto</h3>
              <p class="page-subtitle">El creador se asigna desde el identificador (sub) del token Keycloak.</p>
            </div>
            <button type="button" class="btn-outline btn-sm" (click)="mostrarNuevoModal.set(false)">Cerrar</button>
          </div>
          <form (ngSubmit)="crear()" class="grid-form" style="grid-template-columns: 1fr;">
            <label>
              Código del presupuesto
              <input [(ngModel)]="nuevo.codigo" name="codigo" required placeholder="PRE-2026-01" />
            </label>
            <label>
              Nombre de la obra
              <input [(ngModel)]="nuevo.nombre" name="nombre" required placeholder="Edificio Administrativo" />
            </label>
            <label>
              Cliente / Entidad
              <input [(ngModel)]="nuevo.cliente" name="cliente" required placeholder="Alcaldía Municipal" />
            </label>
            <div class="actions-row" style="justify-content: flex-end; margin-top: 6px;">
              <button type="button" class="btn-outline" (click)="mostrarNuevoModal.set(false)">Cancelar</button>
              <button type="submit" class="btn-primary">Crear Presupuesto</button>
            </div>
          </form>
        </div>
      </div>
    }

    @if (duplicandoBase(); as base) {
      <div class="modal-backdrop" (click)="duplicandoBase.set(null)">
        <div class="modal-dialog" (click)="$event.stopPropagation()">
          <div class="header-row">
            <div>
              <h3>Duplicar Escenario desde {{ base.codigo }}</h3>
              <p class="page-subtitle">Crea un escenario B independiente sin alterar ninguna cifra del presupuesto A.</p>
            </div>
            <button type="button" class="btn-outline btn-sm" (click)="duplicandoBase.set(null)">Cerrar</button>
          </div>
          <form (ngSubmit)="confirmarDuplicar()" class="grid-form" style="grid-template-columns: 1fr;">
            <label>
              Código Escenario B
              <input [(ngModel)]="dupForm.codigo" name="dupCodigo" required />
            </label>
            <label>
              Nombre Escenario B
              <input [(ngModel)]="dupForm.nombre" name="dupNombre" required />
            </label>
            <div class="actions-row" style="justify-content: flex-end; margin-top: 6px;">
              <button type="button" class="btn-outline" (click)="duplicandoBase.set(null)">Cancelar</button>
              <button type="submit" class="btn-primary">Crear Escenario B</button>
            </div>
          </form>
        </div>
      </div>
    }
  `,
})
export class PresupuestosListComponent implements OnInit {
  readonly authService = inject(AuthService);
  private readonly api = inject(PresupuestoApiService);

  readonly presupuestos = signal<PresupuestoResumen[]>([]);
  readonly busqueda = signal<string>('');
  readonly estadoFiltro = signal<string>('');
  readonly mostrarNuevoModal = signal<boolean>(false);
  readonly duplicandoBase = signal<PresupuestoResumen | null>(null);
  readonly cargando = signal<boolean>(false);
  readonly errorMsg = signal<string | null>(null);

  readonly borradoresCount = computed(() => this.presupuestos().filter((p) => p.estado === 'BORRADOR').length);
  readonly enRevisionCount = computed(() => this.presupuestos().filter((p) => p.estado === 'EN_REVISION').length);
  readonly aprobadosCount = computed(() => this.presupuestos().filter((p) => p.estado === 'APROBADO').length);

  readonly presupuestosFiltrados = computed(() => {
    const q = this.busqueda().trim().toLowerCase();
    const est = this.estadoFiltro();
    return this.presupuestos().filter((p) => {
      const matchEst = !est || p.estado === est;
      const matchQuery =
        !q ||
        p.codigo.toLowerCase().includes(q) ||
        p.nombre.toLowerCase().includes(q) ||
        p.cliente.toLowerCase().includes(q);
      return matchEst && matchQuery;
    });
  });

  nuevo = { codigo: '', nombre: '', cliente: '' };
  dupForm = { codigo: '', nombre: '' };

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
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

  crear(): void {
    this.errorMsg.set(null);
    this.api.crear(this.nuevo).subscribe({
      next: () => {
        this.nuevo = { codigo: '', nombre: '', cliente: '' };
        this.mostrarNuevoModal.set(false);
        this.cargar();
      },
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  abrirDuplicar(base: PresupuestoResumen): void {
    this.duplicandoBase.set(base);
    this.dupForm = {
      codigo: `${base.codigo}-ESC-B`,
      nombre: `${base.nombre} (Escenario B)`,
    };
  }

  confirmarDuplicar(): void {
    const base = this.duplicandoBase();
    if (!base) return;
    this.api.duplicarEscenario(base.id, this.dupForm).subscribe({
      next: () => {
        this.duplicandoBase.set(null);
        this.cargar();
      },
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }
}
