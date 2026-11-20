import { Component, OnInit, inject, signal } from '@angular/core';
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
        <h2>Presupuestos de Obra</h2>
        <a routerLink="/escenarios/comparar" class="btn-secondary">Comparar Escenarios</a>
      </div>

      @if (errorMsg()) {
        <div class="alert-error">{{ errorMsg() }}</div>
      }

      @if (authService.isPresupuestador()) {
        <div class="card">
          <h3>Nuevo Presupuesto</h3>
          <form (ngSubmit)="crear()" class="grid-form">
            <label>
              Código
              <input [(ngModel)]="nuevo.codigo" name="codigo" required placeholder="PRE-2026-01" />
            </label>
            <label>
              Nombre
              <input [(ngModel)]="nuevo.nombre" name="nombre" required placeholder="Edificio Administrativo" />
            </label>
            <label>
              Cliente
              <input [(ngModel)]="nuevo.cliente" name="cliente" required placeholder="Alcaldía Municipal" />
            </label>
            <div class="actions-row">
              <button type="submit" class="btn-primary">Crear Presupuesto</button>
            </div>
          </form>
        </div>
      }

      <div class="card">
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
              <th>Versiones</th>
              <th>Creado Por (sub)</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (p of presupuestos(); track p.id) {
              <tr>
                <td><code>{{ p.codigo }}</code></td>
                <td>{{ p.nombre }}</td>
                <td>{{ p.cliente }}</td>
                <td>
                  <span [class]="p.estado === 'APROBADO' ? 'badge-approved' : (p.estado === 'EN_REVISION' ? 'badge-review' : 'badge-draft')">
                    {{ p.estado }}
                  </span>
                  @if (p.motivoRechazo) {
                    <div><small style="color: var(--error-text);">Rechazado: {{ p.motivoRechazo }}</small></div>
                  }
                </td>
                <td>{{ p.totalCapitulos }} / {{ p.totalItems }}</td>
                <td>
                  <a [routerLink]="['/presupuestos', p.id]" class="btn-secondary">
                    {{ p.totalVersiones }} congelada(s)
                  </a>
                </td>
                <td><small>{{ p.creadoPor }}</small></td>
                <td class="actions-cell">
                  <a [routerLink]="['/presupuestos', p.id]" class="btn-primary">Abrir</a>
                  @if (authService.isPresupuestador()) {
                    <button type="button" class="btn-secondary" (click)="abrirDuplicar(p)">
                      Duplicar Escenario
                    </button>
                  }
                </td>
              </tr>
            } @empty {
              @if (!cargando()) {
                <tr>
                  <td colspan="8" style="color: var(--muted);">No hay presupuestos registrados todavía.</td>
                </tr>
              }
            }
          </tbody>
        </table>
      </div>

      @if (duplicandoBase()) {
        <div class="card">
          <h3>Duplicar Escenario desde {{ duplicandoBase()!.codigo }}</h3>
          <form (ngSubmit)="confirmarDuplicar()" class="grid-form">
            <label>
              Código Escenario B
              <input [(ngModel)]="dupForm.codigo" name="dupCodigo" required />
            </label>
            <label>
              Nombre Escenario B
              <input [(ngModel)]="dupForm.nombre" name="dupNombre" required />
            </label>
            <div class="actions-row">
              <button type="submit" class="btn-primary">Crear Escenario B</button>
              <button type="button" class="btn-secondary" (click)="duplicandoBase.set(null)">Cancelar</button>
            </div>
          </form>
        </div>
      }
    </section>
  `,
})
export class PresupuestosListComponent implements OnInit {
  readonly authService = inject(AuthService);
  private readonly api = inject(PresupuestoApiService);

  readonly presupuestos = signal<PresupuestoResumen[]>([]);
  readonly duplicandoBase = signal<PresupuestoResumen | null>(null);
  readonly cargando = signal<boolean>(false);
  readonly errorMsg = signal<string | null>(null);

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
