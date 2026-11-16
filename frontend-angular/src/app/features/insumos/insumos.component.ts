import { Component, OnInit, inject, signal } from '@angular/core';
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
        <h2>Catálogo Maestro de Insumos</h2>
      </div>

      @if (errorMsg()) {
        <div class="alert-error">{{ errorMsg() }}</div>
      }
      @if (successMsg()) {
        <div class="alert-success">{{ successMsg() }}</div>
      }

      @if (authService.isPresupuestador()) {
        <div class="card">
          <h3>{{ editandoId() ? 'Editar Insumo' : 'Registrar Nuevo Insumo' }}</h3>
          <form (ngSubmit)="guardar()" class="grid-form">
            <label>
              Código
              <input [(ngModel)]="form.codigo" name="codigo" required placeholder="CEM-001" />
            </label>
            <label>
              Descripción
              <input [(ngModel)]="form.descripcion" name="descripcion" required placeholder="Cemento gris 50kg" />
            </label>
            <label>
              Unidad
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
              Precio Unitario (hasta 3 decimales)
              <input [(ngModel)]="form.precioUnitario" name="precioUnitario" required placeholder="12.345" />
            </label>
            <div class="actions-row">
              <button type="submit" class="btn-primary">
                {{ editandoId() ? 'Guardar Cambios' : 'Crear Insumo' }}
              </button>
              @if (editandoId()) {
                <button type="button" class="btn-secondary" (click)="cancelarEdicion()">Cancelar</button>
              }
            </div>
          </form>
        </div>
      }

      <div class="card">
        @if (cargando() && insumos().length === 0) {
          <p style="color: var(--muted);">Cargando insumos...</p>
        }
        <table>
          <thead>
            <tr>
              <th>Código</th>
              <th>Descripción</th>
              <th>Unidad</th>
              <th>Precio Unitario (COP)</th>
              <th>Último Editor (sub)</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (ins of insumos(); track ins.id) {
              <tr>
                <td><code>{{ ins.codigo }}</code></td>
                <td>{{ ins.descripcion }}</td>
                <td>{{ ins.unidadMedida }}</td>
                <td class="num">{{ ins.precioUnitario }}</td>
                <td><small>{{ ins.updatedBy || '-' }}</small></td>
                <td class="actions-cell">
                  <button type="button" class="btn-secondary" (click)="verHistorial(ins)">Detalle / Historial</button>
                  @if (authService.isPresupuestador()) {
                    <button type="button" class="btn-secondary" (click)="editar(ins)">Editar</button>
                  }
                </td>
              </tr>
            } @empty {
              @if (!cargando()) {
                <tr>
                  <td colspan="6" style="color: var(--muted);">No hay insumos registrados en el catálogo.</td>
                </tr>
              }
            }
          </tbody>
        </table>
      </div>

      @if (insumoHistorial(); as det) {
        <div class="card">
          <div class="header-row">
            <h3>Detalle e Historial: {{ det.codigo }} - {{ det.descripcion }}</h3>
            <button type="button" class="btn-secondary" (click)="cerrarDetalle()">Cerrar</button>
          </div>
          <p>
            Unidad: <strong>{{ det.unidadMedida }}</strong> ·
            Precio vigente: <strong>{{ det.precioUnitario }} COP</strong> ·
            Último editor: <small>{{ det.updatedBy || '-' }}</small>
          </p>

          <h4>Presupuestos que usan este insumo</h4>
          @if (presupuestosUsando().length === 0) {
            <p style="color: var(--muted);">Ningún presupuesto utiliza este insumo actualmente.</p>
          } @else {
            <table style="margin-bottom: 16px;">
              <thead>
                <tr>
                  <th>Código</th>
                  <th>Nombre</th>
                  <th>Cliente</th>
                  <th>Estado</th>
                  <th>Ítems</th>
                </tr>
              </thead>
              <tbody>
                @for (p of presupuestosUsando(); track p.id) {
                  <tr>
                    <td><a [routerLink]="['/presupuestos', p.id]"><code>{{ p.codigo }}</code></a></td>
                    <td>{{ p.nombre }}</td>
                    <td>{{ p.cliente }}</td>
                    <td><span [class]="p.estado === 'APROBADO' ? 'badge-approved' : 'badge-draft'">{{ p.estado }}</span></td>
                    <td class="num">{{ p.totalItems }}</td>
                  </tr>
                }
              </tbody>
            </table>
          }

          <h4>Historial de Precios</h4>
          <table>
            <thead>
              <tr>
                <th>Precio Anterior</th>
                <th>Precio Nuevo</th>
                <th>Modificado Por (sub)</th>
                <th>Fecha</th>
              </tr>
            </thead>
            <tbody>
              @for (h of historial(); track h.id) {
                <tr>
                  <td class="num">{{ h.precioAnterior ?? '-' }}</td>
                  <td class="num">{{ h.precioNuevo }}</td>
                  <td><small>{{ h.modificadoPor || '-' }}</small></td>
                  <td>{{ h.fechaCambio }}</td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="4" style="color: var(--muted);">Sin cambios históricos registrados.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
})
export class InsumosComponent implements OnInit {
  readonly authService = inject(AuthService);
  private readonly insumoApi = inject(InsumoApiService);
  private readonly presupuestoApi = inject(PresupuestoApiService);

  readonly insumos = signal<Insumo[]>([]);
  readonly editandoId = signal<string | null>(null);
  readonly insumoHistorial = signal<Insumo | null>(null);
  readonly historial = signal<HistorialPrecio[]>([]);
  readonly presupuestosUsando = signal<PresupuestoResumen[]>([]);
  readonly cargando = signal<boolean>(false);
  readonly errorMsg = signal<string | null>(null);
  readonly successMsg = signal<string | null>(null);

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

  guardar(): void {
    this.errorMsg.set(null);
    this.successMsg.set(null);
    const id = this.editandoId();
    const obs = id ? this.insumoApi.actualizar(id, this.form) : this.insumoApi.crear(this.form);
    obs.subscribe({
      next: () => {
        this.successMsg.set(id ? 'Insumo actualizado y registrado en historial.' : 'Insumo creado.');
        this.cancelarEdicion();
        this.cargar();
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
  }

  cancelarEdicion(): void {
    this.editandoId.set(null);
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
}
