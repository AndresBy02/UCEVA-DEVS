import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { HistorialPrecio, Insumo, InsumoRequest, PresupuestoResumen } from '../../core/models/api.models';
import { InsumoApiService } from '../../core/services/insumo-api.service';
import { PresupuestoApiService } from '../../core/services/presupuesto-api.service';
import { extraerErrorHttp } from '../../core/utils/http-error.util';

@Component({
  selector: 'app-insumos',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <section class="page-section">
      <div class="header-row">
        <div>
          <h2 class="page-title">Catálogo de Insumos</h2>
          <p class="page-subtitle">Precios unitarios exactos a 3 decimales (sin float), no existencias ni inventarios.</p>
        </div>
        @if (authService.isPresupuestador()) {
          <button type="button" class="btn-primary" (click)="abrirNuevo()">+ Nuevo insumo</button>
        }
      </div>

      @if (errorMsg()) {
        <div class="alert-error">{{ errorMsg() }}</div>
      }
      @if (successMsg()) {
        <div class="alert-success">{{ successMsg() }}</div>
      }

      @if (insumoHistorial(); as det) {
        <div class="card">
          <div class="header-row" style="margin-bottom: 14px;">
            <div>
              <h3 style="font-size: 1.15rem;">{{ det.descripcion }} · <code>{{ det.codigo }}</code></h3>
              <p class="page-subtitle">Detalle de insumo vigente, impacto en presupuestos e historial inmutable</p>
            </div>
            <div class="actions-row">
              @if (authService.isPresupuestador()) {
                <button type="button" class="btn-primary btn-sm" (click)="editar(det)">Actualizar precio</button>
              }
              <button type="button" class="btn-outline btn-sm" (click)="cerrarDetalle()">Cerrar detalle</button>
            </div>
          </div>

          <div class="split-2col" style="margin-bottom: 16px;">
            <div class="stat-box highlight">
              <span>Precio vigente ({{ det.unidadMedida }})</span>
              <strong>$ {{ det.precioUnitario }} COP</strong>
              <small>Último editor (sub): <code>{{ det.updatedBy || '-' }}</code></small>
            </div>
            <div class="stat-box">
              <span>Impacto en presupuestos</span>
              <strong>{{ presupuestosUsando().length }} presupuesto(s)</strong>
              <small>Cada presupuesto conserva su copia del precio (snapshot); actualizar exige confirmación.</small>
            </div>
          </div>

          <h4 style="margin-bottom: 6px;">Presupuestos que usan este insumo</h4>
          @if (presupuestosUsando().length === 0) {
            <p style="color: var(--muted); margin-bottom: 14px;">Ningún presupuesto utiliza este insumo actualmente.</p>
          } @else {
            <table style="margin-bottom: 18px;">
              <thead>
                <tr>
                  <th>Código</th>
                  <th>Nombre</th>
                  <th>Cliente</th>
                  <th>Estado</th>
                  <th class="num">Ítems</th>
                </tr>
              </thead>
              <tbody>
                @for (p of presupuestosUsando(); track p.id) {
                  <tr>
                    <td><a [routerLink]="['/presupuestos', p.id]"><code>{{ p.codigo }}</code></a></td>
                    <td>{{ p.nombre }}</td>
                    <td>{{ p.cliente }}</td>
                    <td>
                      <span [class]="p.estado === 'APROBADO' ? 'badge-approved' : (p.estado === 'EN_REVISION' ? 'badge-review' : 'badge-draft')">
                        {{ p.estado }}
                      </span>
                    </td>
                    <td class="num">{{ p.totalItems }}</td>
                  </tr>
                }
              </tbody>
            </table>
          }

          <h4 style="margin-bottom: 6px;">Historial inmutable de precios</h4>
          <table>
            <thead>
              <tr>
                <th>Fecha</th>
                <th class="num">Precio anterior</th>
                <th class="num">Precio nuevo</th>
                <th class="num">Variación</th>
                <th>Modificado por (sub)</th>
              </tr>
            </thead>
            <tbody>
              @for (h of historial(); track h.id) {
                <tr>
                  <td>{{ h.fechaCambio }}</td>
                  <td class="num">{{ h.precioAnterior ?? '-' }}</td>
                  <td class="num"><strong>{{ h.precioNuevo }}</strong></td>
                  <td class="num">
                    @if (variacionPct(h.precioAnterior, h.precioNuevo); as v) {
                      <span [class]="v.startsWith('+') ? 'delta-up' : 'delta-down'">{{ v }}</span>
                    } @else {
                      <span>—</span>
                    }
                  </td>
                  <td><small>{{ h.modificadoPor || '-' }}</small></td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="5" style="color: var(--muted);">Sin cambios históricos registrados.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      <div class="card">
        <div class="filter-bar" style="margin-bottom: 12px;">
          <input
            type="search"
            class="search-input"
            [ngModel]="busqueda()"
            (ngModelChange)="busqueda.set($event)"
            name="buscarInsumo"
            placeholder="Buscar por código o descripción..."
          />
          <select [ngModel]="unidadFiltro()" (ngModelChange)="unidadFiltro.set($event)" name="filtroUnidad">
            <option value="">Todas las unidades</option>
            <option value="UNIDAD">UNIDAD</option>
            <option value="M">M</option>
            <option value="M2">M2</option>
            <option value="M3">M3</option>
            <option value="KG">KG</option>
            <option value="BULTO">BULTO</option>
            <option value="DIA">DIA</option>
          </select>
          <span style="color: var(--muted); font-size: 0.84rem; margin-left: auto;">
            Mostrando {{ insumosFiltrados().length }} de {{ insumos().length }}
          </span>
        </div>

        @if (cargando() && insumos().length === 0) {
          <p style="color: var(--muted);">Cargando insumos...</p>
        }
        <table>
          <thead>
            <tr>
              <th>Código</th>
              <th>Descripción</th>
              <th>Unidad</th>
              <th class="num">Precio Unitario (COP)</th>
              <th>Último Editor (sub)</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (ins of insumosFiltrados(); track ins.id) {
              <tr>
                <td><code>{{ ins.codigo }}</code></td>
                <td>{{ ins.descripcion }}</td>
                <td><span class="badge-draft" style="background: var(--surface-subtle); color: var(--text); border: 1px solid var(--border);">{{ ins.unidadMedida }}</span></td>
                <td class="num"><strong>{{ ins.precioUnitario }}</strong></td>
                <td><small>{{ ins.updatedBy || '-' }}</small></td>
                <td class="actions-cell">
                  <button type="button" class="btn-outline btn-sm" (click)="verHistorial(ins)">Ver / Historial</button>
                  @if (authService.isPresupuestador()) {
                    <button type="button" class="btn-secondary btn-sm" (click)="editar(ins)">Editar</button>
                  }
                </td>
              </tr>
            } @empty {
              @if (!cargando()) {
                <tr>
                  <td colspan="6" style="color: var(--muted);">No hay insumos que coincidan con el filtro actual.</td>
                </tr>
              }
            }
          </tbody>
        </table>
      </div>
    </section>

    @if (modalAbierto() && authService.isPresupuestador()) {
      <div class="modal-backdrop" (click)="cancelarEdicion()">
        <div class="modal-dialog" (click)="$event.stopPropagation()">
          <div class="header-row">
            <div>
              <h3>{{ editandoId() ? 'Editar Insumo' : 'Nuevo Insumo' }}</h3>
              <p class="page-subtitle">Unidad de lista cerrada. Precio conservado exactamente hasta 3 decimales.</p>
            </div>
            <button type="button" class="btn-outline btn-sm" (click)="cancelarEdicion()">Cerrar</button>
          </div>

          <form (ngSubmit)="guardar()" class="grid-form" style="grid-template-columns: 1fr;">
            <label>
              Código único
              <input [(ngModel)]="form.codigo" name="codigo" required placeholder="CEM-001" />
            </label>
            <label>
              Descripción
              <input [(ngModel)]="form.descripcion" name="descripcion" required placeholder="Cemento gris 50 kg" />
            </label>
            <label>
              Unidad (lista cerrada)
              <select [(ngModel)]="form.unidadMedida" name="unidadMedida" required>
                <option value="UNIDAD">UNIDAD</option>
                <option value="M">M</option>
                <option value="M2">M2</option>
                <option value="M3">M3</option>
                <option value="KG">KG</option>
                <option value="BULTO">BULTO</option>
                <option value="DIA">DIA</option>
              </select>
            </label>
            <label>
              Precio unitario (hasta 3 decimales)
              <input [(ngModel)]="form.precioUnitario" name="precioUnitario" required placeholder="12.345" />
            </label>
            <div class="actions-row" style="justify-content: flex-end; margin-top: 6px;">
              <button type="button" class="btn-outline" (click)="cancelarEdicion()">Cancelar</button>
              <button type="submit" class="btn-primary">
                {{ editandoId() ? 'Guardar Cambios' : 'Guardar Insumo' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    }
  `,
})
export class InsumosComponent implements OnInit {
  readonly authService = inject(AuthService);
  private readonly insumoApi = inject(InsumoApiService);
  private readonly presupuestoApi = inject(PresupuestoApiService);

  readonly insumos = signal<Insumo[]>([]);
  readonly busqueda = signal<string>('');
  readonly unidadFiltro = signal<string>('');
  readonly modalAbierto = signal<boolean>(false);
  readonly editandoId = signal<string | null>(null);
  readonly insumoHistorial = signal<Insumo | null>(null);
  readonly historial = signal<HistorialPrecio[]>([]);
  readonly presupuestosUsando = signal<PresupuestoResumen[]>([]);
  readonly cargando = signal<boolean>(false);
  readonly errorMsg = signal<string | null>(null);
  readonly successMsg = signal<string | null>(null);

  readonly insumosFiltrados = computed(() => {
    const q = this.busqueda().trim().toLowerCase();
    const u = this.unidadFiltro();
    return this.insumos().filter((ins) => {
      const matchUnidad = !u || ins.unidadMedida === u;
      const matchQuery =
        !q ||
        ins.codigo.toLowerCase().includes(q) ||
        ins.descripcion.toLowerCase().includes(q);
      return matchUnidad && matchQuery;
    });
  });

  form: InsumoRequest = {
    codigo: '',
    descripcion: '',
    unidadMedida: 'UNIDAD',
    precioUnitario: '',
  };

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.insumoApi.listar().subscribe({
      next: (page) => {
        this.insumos.set(page.content);
        this.cargando.set(false);
      },
      error: (err) => {
        this.cargando.set(false);
        this.errorMsg.set(extraerErrorHttp(err));
      },
    });
  }

  abrirNuevo(): void {
    this.editandoId.set(null);
    this.form = { codigo: '', descripcion: '', unidadMedida: 'UNIDAD', precioUnitario: '' };
    this.modalAbierto.set(true);
  }

  guardar(): void {
    this.errorMsg.set(null);
    this.successMsg.set(null);
    const id = this.editandoId();
    const obs = id ? this.insumoApi.actualizar(id, this.form) : this.insumoApi.crear(this.form);
    obs.subscribe({
      next: (saved) => {
        this.successMsg.set(id ? 'Insumo actualizado y registrado en historial.' : 'Insumo creado.');
        this.cancelarEdicion();
        this.cargar();
        if (this.insumoHistorial()?.id === saved.id) {
          this.verHistorial(saved);
        }
      },
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  editar(insumo: Insumo): void {
    this.editandoId.set(insumo.id);
    this.form = {
      codigo: insumo.codigo,
      descripcion: insumo.descripcion,
      unidadMedida: insumo.unidadMedida,
      precioUnitario: insumo.precioUnitario,
    };
    this.modalAbierto.set(true);
  }

  cancelarEdicion(): void {
    this.editandoId.set(null);
    this.modalAbierto.set(false);
    this.form = { codigo: '', descripcion: '', unidadMedida: 'UNIDAD', precioUnitario: '' };
  }

  verHistorial(insumo: Insumo): void {
    this.errorMsg.set(null);
    this.insumoHistorial.set(insumo);
    this.historial.set([]);
    this.presupuestosUsando.set([]);
    this.insumoApi.obtenerHistorial(insumo.id).subscribe({
      next: (lista) => this.historial.set(lista),
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
    this.presupuestoApi.listarPorInsumo(insumo.id).subscribe({
      next: (lista) => this.presupuestosUsando.set(lista),
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  cerrarDetalle(): void {
    this.insumoHistorial.set(null);
    this.historial.set([]);
    this.presupuestosUsando.set([]);
  }

  variacionPct(ant: string | null, nuevo: string): string | null {
    if (!ant) return null;
    const a = Number(ant);
    const n = Number(nuevo);
    if (!Number.isFinite(a) || !Number.isFinite(n) || a <= 0) return null;
    const pct = ((n - a) / a) * 100;
    const sign = pct > 0 ? '+' : '';
    return `${sign}${pct.toFixed(2)}%`;
  }
}
