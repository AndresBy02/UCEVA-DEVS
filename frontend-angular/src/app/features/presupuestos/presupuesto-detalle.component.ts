import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import {
  Insumo,
  InsumosCriticos,
  ItemPresupuestoDetalle,
  PresupuestoDetalle,
  PrevisualizacionPrecios,
  SimulacionSensibilidad,
  VersionPresupuesto,
} from '../../core/models/api.models';
import { InsumoApiService } from '../../core/services/insumo-api.service';
import { PresupuestoApiService } from '../../core/services/presupuesto-api.service';
import { descargarCsvPresupuesto } from '../../core/utils/presupuesto-csv.util';
import { extraerErrorHttp } from '../../core/utils/http-error.util';

@Component({
  selector: 'app-presupuesto-detalle',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, RouterLink],
  template: `
    <section class="page-section">
      <div class="header-row">
        <a routerLink="/presupuestos" class="btn-secondary">Volver al listado</a>
        @if (presupuesto(); as p) {
          <div class="header-meta">
            <h2>{{ p.codigo }} · {{ p.nombre }}</h2>
            <span [class]="p.estado === 'APROBADO' ? 'badge-approved' : 'badge-draft'">
              {{ p.estado }}
            </span>
          </div>
          @if (authService.isPresupuestador()) {
            <button type="button" class="btn-secondary" (click)="enviarARevision()"
                    [disabled]="enviandoRevision()">
              {{ enviandoRevision() ? 'Enviando...' : 'Enviar a revisión' }}
            </button>
          }
          <button type="button" class="btn-secondary" (click)="exportarCsv()">Exportar CSV</button>
          @if (authService.isRevisor() && p.estado === 'BORRADOR') {
            <button type="button" class="btn-primary" (click)="aprobar()">Aprobar Presupuesto</button>
          }
        }
      </div>

      @if (errorMsg()) {
        <div class="alert-error">{{ errorMsg() }}</div>
      }
      @if (successMsg()) {
        <div class="alert-success">{{ successMsg() }}</div>
      }
      @if (cargando() && !presupuesto()) {
        <div class="card"><p style="color: var(--muted);">Cargando detalle del presupuesto...</p></div>
      }

      @if (presupuesto(); as p) {
        <div class="card">
          <div class="meta-grid">
            <div><strong>Cliente:</strong> {{ p.cliente }}</div>
            <div><strong>Creado por (sub):</strong> <code>{{ p.creadoPor }}</code></div>
            <div><strong>Aprobado por (sub):</strong> <code>{{ p.aprobadoPor || '-' }}</code></div>
            @if (p.presupuestoBaseId) {
              <div>
                <strong>Escenario derivado de:</strong>
                <a [routerLink]="['/escenarios/comparar']">Comparar escenarios</a>
              </div>
            }
          </div>
        </div>

        <div class="card">
          <div class="header-row">
            <h3>Versiones enviadas a revisión</h3>
            <span class="badge-draft">{{ versiones().length }} versión(es) congelada(s)</span>
          </div>
          <p style="color: var(--muted);">
            Cada envío congela una copia exacta del presupuesto calculado. Las versiones no se modifican.
          </p>
          <table>
            <thead>
              <tr>
                <th>Versión</th>
                <th>Fecha de envío</th>
                <th>Enviada por</th>
                <th>Revisor notificado</th>
                <th>Mensaje</th>
                <th>Estado</th>
              </tr>
            </thead>
            <tbody>
              @for (version of versiones(); track version.id) {
                <tr>
                  <td><strong>#{{ version.numero }}</strong><br /><code>{{ version.codigoPresupuesto }}</code></td>
                  <td>{{ version.fechaCreacion | date: 'yyyy-MM-dd HH:mm' }}</td>
                  <td><code>{{ version.creadoPor }}</code></td>
                  <td><code>{{ version.destinatarioSub }}</code></td>
                  <td>{{ version.mensaje || '-' }}</td>
                  <td><span class="badge-approved">{{ version.estado }}</span></td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="6" style="color: var(--muted);">
                    Aún no se ha enviado ninguna versión a revisión.
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <div class="card">
          <h3>Parámetros AIU y Punto de Equilibrio (Recálculo en Servidor)</h3>
          <form (ngSubmit)="guardarParametros()" class="grid-form">
            <label>
              % Administración (A)
              <input type="number" step="0.01" [(ngModel)]="paramForm.porcentajeAdministracion" name="porcA"
                     [disabled]="p.estado === 'APROBADO'" />
            </label>
            <label>
              % Imprevistos (I)
              <input type="number" step="0.01" [(ngModel)]="paramForm.porcentajeImprevistos" name="porcI"
                     [disabled]="p.estado === 'APROBADO'" />
            </label>
            <label>
              % Utilidad (U)
              <input type="number" step="0.01" [(ngModel)]="paramForm.porcentajeUtilidad" name="porcU"
                     [disabled]="p.estado === 'APROBADO'" />
            </label>
            <label>
              Costo Fijo (COP)
              <input type="number" step="0.01" [(ngModel)]="paramForm.costoFijo" name="costoFijo"
                     [disabled]="p.estado === 'APROBADO'" />
            </label>
            <label>
              Precio Venta Unitario (PE)
              <input type="number" step="0.01" [(ngModel)]="paramForm.precioVentaUnitario" name="pvu"
                     [disabled]="p.estado === 'APROBADO'" />
            </label>
            <label>
              Costo Variable Unitario (PE)
              <input type="number" step="0.01" [(ngModel)]="paramForm.costoVariableUnitario" name="cvu"
                     [disabled]="p.estado === 'APROBADO'" />
            </label>
            @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
              <div class="actions-row">
                <button type="submit" class="btn-primary">Recalcular y Guardar Parámetros</button>
              </div>
            }
          </form>

          <div class="summary-grid">
            <div class="stat-box">
              <span>Costo Directo (CD)</span>
              <strong>{{ p.costoDirecto | number: '1.2-2' }}</strong>
            </div>
            <div class="stat-box">
              <span>Administración ({{ p.porcentajeAdministracion | number: '1.2-2' }}%)</span>
              <strong>{{ p.valorAdministracion | number: '1.2-2' }}</strong>
            </div>
            <div class="stat-box">
              <span>Imprevistos ({{ p.porcentajeImprevistos | number: '1.2-2' }}%)</span>
              <strong>{{ p.valorImprevistos | number: '1.2-2' }}</strong>
            </div>
            <div class="stat-box">
              <span>Utilidad ({{ p.porcentajeUtilidad | number: '1.2-2' }}%)</span>
              <strong>{{ p.valorUtilidad | number: '1.2-2' }}</strong>
            </div>
            <div class="stat-box highlight">
              <span>Precio de Venta (CD + A + I + U)</span>
              <strong>{{ p.precioVenta | number: '1.2-2' }}</strong>
            </div>
            <div class="stat-box">
              <span>{{ p.rotuloMargenSobreCosto }}</span>
              <strong>{{ p.margenSobreCostoPorcentaje | number: '1.2-2' }}%</strong>
            </div>
            <div class="stat-box">
              <span>{{ p.rotuloMargenSobrePrecioVenta }}</span>
              <strong>{{ p.margenSobrePrecioVentaPorcentaje | number: '1.2-2' }}%</strong>
            </div>
            <div class="stat-box">
              <span>Punto de Equilibrio</span>
              @if (p.puntoEquilibrio; as pe) {
                <strong>{{ pe.viable ? (pe.cantidadEquilibrio | number: '1.2-2') + ' u' : pe.mensaje }}</strong>
                <small>{{ pe.mensaje }}</small>
              } @else {
                <strong>Sin configurar</strong>
              }
            </div>
          </div>
        </div>

        @if (authService.isPresupuestador()) {
          <div class="card">
            <div class="header-row">
              <h3>Actualización Explícita de Precios desde Catálogo</h3>
              <button type="button" class="btn-secondary" (click)="previsualizarPrecios()"
                      [disabled]="p.estado === 'APROBADO'">
                Previsualizar Cambios de Catálogo
              </button>
            </div>
            @if (previewPrecios(); as prev) {
              <p>
                Ítems revisados: {{ prev.totalItemsRevisados }} ·
                Ítems con diferencia de precio: <strong>{{ prev.cantidadItemsConCambio }}</strong>
              </p>
              @if (prev.cambios.length > 0) {
                <table>
                  <thead>
                    <tr>
                      <th>Capítulo</th>
                      <th>Código</th>
                      <th>Descripción</th>
                      <th>Precio en Presupuesto</th>
                      <th>Precio Catálogo Vigente</th>
                      <th>Diferencia</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (c of prev.cambios; track c.itemId) {
                      <tr>
                        <td>{{ c.capituloCodigo }}</td>
                        <td><code>{{ c.codigoInsumo }}</code></td>
                        <td>{{ c.descripcion }}</td>
                        <td class="num">{{ c.precioAnteriorPresupuesto }}</td>
                        <td class="num">{{ c.precioNuevoCatalogo }}</td>
                        <td class="num">{{ c.diferenciaUnitaria }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
                <div class="actions-row" style="margin-top: 12px;">
                  <button type="button" class="btn-primary" (click)="confirmarActualizacionPrecios()">
                    Confirmar y Aplicar Precios Vigentes
                  </button>
                  <button type="button" class="btn-secondary" (click)="previewPrecios.set(null)">
                    Cancelar
                  </button>
                </div>
              }
            }
          </div>
        }

        <div class="card">
          <div class="header-row">
            <h3>Capítulos e Ítems</h3>
            @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
              <form (ngSubmit)="agregarCapitulo()" class="inline-form">
                <input [(ngModel)]="nuevoCap.codigo" name="capCod" placeholder="CAP-01" required />
                <input [(ngModel)]="nuevoCap.nombre" name="capNom" placeholder="Nombre del capítulo" required />
                <button type="submit" class="btn-primary">Agregar Capítulo</button>
              </form>
            }
          </div>

          @for (cap of p.capitulos; track cap.id) {
            <div class="chapter-block">
              <div class="header-row">
                <h4>{{ cap.orden }}. {{ cap.codigo }} - {{ cap.nombre }}</h4>
                <div class="chapter-actions">
                  <strong>Subtotal Capítulo: {{ cap.subtotalCapitulo | number: '1.2-2' }}</strong>
                  @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
                    <button type="button" class="btn-secondary" (click)="eliminarCapitulo(cap.id)">
                      Eliminar Capítulo
                    </button>
                  }
                </div>
              </div>

              @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
                <form (ngSubmit)="agregarItem(cap.id)" class="inline-form" style="margin-bottom: 8px;">
                  <select [(ngModel)]="nuevoItemPorCap[cap.id].insumoId" [name]="'ins_' + cap.id" required>
                    <option value="">Seleccione insumo...</option>
                    @for (ins of catalogoInsumos(); track ins.id) {
                      <option [value]="ins.id">
                        {{ ins.codigo }} - {{ ins.descripcion }} ({{ ins.precioUnitario }})
                      </option>
                    }
                  </select>
                  <input [(ngModel)]="nuevoItemPorCap[cap.id].cantidad" [name]="'cant_' + cap.id"
                         placeholder="Cantidad (ej. 12.500)" required />
                  <button type="submit" class="btn-primary">Agregar Ítem</button>
                </form>
              }

              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Código</th>
                    <th>Descripción</th>
                    <th>Unidad</th>
                    <th>Cantidad</th>
                    <th>Precio Unitario (Copia)</th>
                    <th>Valor Parcial (COP)</th>
                    @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
                      <th>Acciones</th>
                    }
                  </tr>
                </thead>
                <tbody>
                  @for (item of cap.items; track item.id) {
                    <tr>
                      <td>{{ item.orden }}</td>
                      <td><code>{{ item.codigoInsumo }}</code></td>
                      <td>{{ item.descripcion }}</td>
                      <td>{{ item.unidadMedida }}</td>
                      <td class="num">{{ item.cantidad }}</td>
                      <td class="num">{{ item.precioUnitarioCopia }}</td>
                      <td class="num"><strong>{{ item.valorParcial | number: '1.2-2' }}</strong></td>
                      @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
                        <td class="actions-cell">
                          <button type="button" class="btn-secondary" (click)="abrirEditarItem(cap.id, item)">
                            Editar
                          </button>
                          <button type="button" class="btn-secondary" (click)="eliminarItem(cap.id, item.id)">
                            Quitar
                          </button>
                        </td>
                      }
                    </tr>
                  } @empty {
                    <tr>
                      <td [attr.colspan]="authService.isPresupuestador() && p.estado === 'BORRADOR' ? 8 : 7"
                          style="color: var(--muted);">
                        Este capítulo aún no tiene ítems.
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          } @empty {
            <p style="color: var(--muted); margin-top: 12px;">Este presupuesto aún no tiene capítulos.</p>
          }

          @if (editandoItem(); as ei) {
            <div class="card" style="margin-top: 12px;">
              <h4>Editar Ítem {{ ei.item.codigoInsumo }}</h4>
              <form (ngSubmit)="guardarItemEditado()" class="grid-form">
                <label>
                  Cantidad (hasta 3 decimales)
                  <input [(ngModel)]="editItemForm.cantidad" name="editCant" required />
                </label>
                <label>
                  Precio Unitario en Escenario (hasta 3 decimales)
                  <input [(ngModel)]="editItemForm.precioUnitarioCopia" name="editPrecio" required />
                </label>
                <div class="actions-row">
                  <button type="submit" class="btn-primary">Guardar Ítem</button>
                  <button type="button" class="btn-secondary" (click)="editandoItem.set(null)">Cancelar</button>
                </div>
              </form>
            </div>
          }
        </div>

        <div class="card">
          <h3>Análisis de Sensibilidad e Insumos Críticos (Sin Modificar Presupuesto)</h3>
          <div class="inline-form" style="margin-bottom: 12px;">
            <input [(ngModel)]="simForm.codigoInsumo" name="simCod" placeholder="Código Insumo (ej. CEM-001)" />
            <input type="number" step="0.01" [(ngModel)]="simForm.porcentajeVariacion" name="simPct"
                   placeholder="% Variación (ej. 10)" />
            <button type="button" class="btn-primary" (click)="simularSensibilidad()">Simular Variación</button>
            <button type="button" class="btn-secondary" (click)="analizarCriticos()">Identificar Insumos Críticos</button>
          </div>

          @if (simulacion(); as sim) {
            <div class="summary-grid" style="margin-bottom: 12px;">
              <div class="stat-box">
                <span>Costo Directo Base -> Simulado</span>
                <strong>{{ sim.costoDirectoOriginal | number: '1.2-2' }} -> {{ sim.costoDirectoSimulado | number: '1.2-2' }}</strong>
                <small>Delta: {{ sim.diferenciaCostoDirecto | number: '1.2-2' }} ({{ sim.variacionCostoDirectoPorcentaje | number: '1.2-2' }}%)</small>
              </div>
              <div class="stat-box">
                <span>Precio Venta Base -> Simulado</span>
                <strong>{{ sim.precioVentaOriginal | number: '1.2-2' }} -> {{ sim.precioVentaSimulado | number: '1.2-2' }}</strong>
                <small>Delta: {{ sim.diferenciaPrecioVenta | number: '1.2-2' }} ({{ sim.variacionPrecioVentaPorcentaje | number: '1.2-2' }}%)</small>
              </div>
              <div class="stat-box">
                <span>Punto de Equilibrio Base -> Simulado</span>
                @if (sim.puntoEquilibrioOriginal && sim.puntoEquilibrioSimulado) {
                  <strong>
                    {{ sim.puntoEquilibrioOriginal.viable ? (sim.puntoEquilibrioOriginal.cantidadEquilibrio | number: '1.2-2') + ' u' : 'No viable' }}
                    ->
                    {{ sim.puntoEquilibrioSimulado.viable ? (sim.puntoEquilibrioSimulado.cantidadEquilibrio | number: '1.2-2') + ' u' : 'No viable' }}
                  </strong>
                  <small>{{ sim.puntoEquilibrioSimulado.mensaje }}</small>
                } @else {
                  <strong>Sin configurar</strong>
                  <small>Configure precio y costo variable unitario en parámetros para simular el punto de equilibrio.</small>
                }
              </div>
            </div>
          }

          @if (criticos(); as crit) {
            <table>
              <thead>
                <tr>
                  <th>Insumo</th>
                  <th>Descripción</th>
                  <th>Aporte CD (COP)</th>
                  <th>Participación (%)</th>
                  <th>Impacto PV (+{{ crit.porcentajeVariacionReferencia }}%)</th>
                </tr>
              </thead>
              <tbody>
                @for (ic of crit.insumosCriticos; track ic.codigoInsumo) {
                  <tr>
                    <td><code>{{ ic.codigoInsumo }}</code></td>
                    <td>{{ ic.descripcion }}</td>
                    <td class="num">{{ ic.aporteCostoDirecto | number: '1.2-2' }}</td>
                    <td class="num">{{ ic.participacionPorcentaje | number: '1.2-2' }}%</td>
                    <td class="num"><strong>{{ ic.impactoPrecioVenta | number: '1.2-2' }}</strong></td>
                  </tr>
                }
              </tbody>
            </table>
          }
        </div>
      }
    </section>
  `,
})
export class PresupuestoDetalleComponent implements OnInit {
  readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(PresupuestoApiService);
  private readonly insumoApi = inject(InsumoApiService);

  readonly presupuesto = signal<PresupuestoDetalle | null>(null);
  readonly catalogoInsumos = signal<Insumo[]>([]);
  readonly previewPrecios = signal<PrevisualizacionPrecios | null>(null);
  readonly simulacion = signal<SimulacionSensibilidad | null>(null);
  readonly criticos = signal<InsumosCriticos | null>(null);
  readonly versiones = signal<VersionPresupuesto[]>([]);
  readonly enviandoRevision = signal<boolean>(false);
  readonly editandoItem = signal<{ capituloId: string; item: ItemPresupuestoDetalle } | null>(null);
  readonly cargando = signal<boolean>(false);
  readonly errorMsg = signal<string | null>(null);
  readonly successMsg = signal<string | null>(null);

  nuevoCap = { codigo: '', nombre: '' };
  nuevoItemPorCap: Record<string, { insumoId: string; cantidad: string }> = {};
  editItemForm = { cantidad: '', precioUnitarioCopia: '' };
  simForm = { codigoInsumo: '', porcentajeVariacion: 10 };

  paramForm = {
    porcentajeAdministracion: 8,
    porcentajeImprevistos: 3,
    porcentajeUtilidad: 5,
    costoFijo: 0,
    precioVentaUnitario: null as number | null,
    costoVariableUnitario: null as number | null,
  };

  private presupuestoId = '';

  ngOnInit(): void {
    this.presupuestoId = this.route.snapshot.paramMap.get('id') ?? '';
    this.cargarDetalle();
    this.insumoApi.listar().subscribe({
      next: (page) => this.catalogoInsumos.set(page.content),
    });
  }

  cargarDetalle(): void {
    this.cargando.set(true);
    this.api.obtenerDetalle(this.presupuestoId).subscribe({
      next: (detalle) => {
        this.aplicarDetalle(detalle);
        this.cargarVersiones();
        this.cargando.set(false);
      },
      error: (err) => {
        this.cargando.set(false);
        this.errorMsg.set(extraerErrorHttp(err));
      },
    });
  }

  exportarCsv(): void {
    const p = this.presupuesto();
    if (!p) return;
    descargarCsvPresupuesto(p);
    this.successMsg.set('Presupuesto exportado a CSV.');
  }

  guardarParametros(): void {
    const p = this.presupuesto();
    if (!p) return;
    this.errorMsg.set(null);
    this.api
      .actualizarParametros(p.id, {
        nombre: p.nombre,
        cliente: p.cliente,
        ...this.paramForm,
      })
      .subscribe({
        next: (detalle) => {
          this.aplicarDetalle(detalle);
          this.successMsg.set('Parámetros recalculados desde el servidor.');
        },
        error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
      });
  }

  agregarCapitulo(): void {
    this.errorMsg.set(null);
    this.api.agregarCapitulo(this.presupuestoId, this.nuevoCap).subscribe({
      next: (detalle) => {
        this.nuevoCap = { codigo: '', nombre: '' };
        this.aplicarDetalle(detalle);
      },
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  eliminarCapitulo(capId: string): void {
    this.errorMsg.set(null);
    this.api.eliminarCapitulo(this.presupuestoId, capId).subscribe({
      next: (detalle) => this.aplicarDetalle(detalle),
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  agregarItem(capId: string): void {
    const form = this.nuevoItemPorCap[capId];
    if (!form?.insumoId || !form?.cantidad) return;
    this.errorMsg.set(null);
    this.api.agregarItem(this.presupuestoId, capId, form).subscribe({
      next: (detalle) => {
        this.nuevoItemPorCap[capId] = { insumoId: '', cantidad: '' };
        this.aplicarDetalle(detalle);
      },
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  abrirEditarItem(capituloId: string, item: ItemPresupuestoDetalle): void {
    this.editandoItem.set({ capituloId, item });
    this.editItemForm = {
      cantidad: item.cantidad,
      precioUnitarioCopia: item.precioUnitarioCopia,
    };
  }

  guardarItemEditado(): void {
    const target = this.editandoItem();
    if (!target) return;
    this.errorMsg.set(null);
    this.api
      .actualizarItem(this.presupuestoId, target.capituloId, target.item.id, this.editItemForm)
      .subscribe({
        next: (detalle) => {
          this.editandoItem.set(null);
          this.aplicarDetalle(detalle);
        },
        error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
      });
  }

  eliminarItem(capId: string, itemId: string): void {
    this.errorMsg.set(null);
    this.api.eliminarItem(this.presupuestoId, capId, itemId).subscribe({
      next: (detalle) => this.aplicarDetalle(detalle),
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  previsualizarPrecios(): void {
    this.errorMsg.set(null);
    this.api.previsualizarActualizacionPrecios(this.presupuestoId).subscribe({
      next: (prev) => this.previewPrecios.set(prev),
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  confirmarActualizacionPrecios(): void {
    this.errorMsg.set(null);
    this.api.aplicarActualizacionPrecios(this.presupuestoId).subscribe({
      next: (detalle) => {
        this.previewPrecios.set(null);
        this.aplicarDetalle(detalle);
        this.successMsg.set('Precios actualizados explícitamente contra el catálogo.');
      },
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  simularSensibilidad(): void {
    if (!this.simForm.codigoInsumo) return;
    this.errorMsg.set(null);
    this.api
      .simularSensibilidad(this.presupuestoId, this.simForm.codigoInsumo, this.simForm.porcentajeVariacion)
      .subscribe({
        next: (sim) => this.simulacion.set(sim),
        error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
      });
  }

  analizarCriticos(): void {
    this.errorMsg.set(null);
    this.api.obtenerInsumosCriticos(this.presupuestoId, this.simForm.porcentajeVariacion || 10).subscribe({
      next: (res) => this.criticos.set(res),
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  cargarVersiones(): void {
    this.api.listarVersiones(this.presupuestoId).subscribe({
      next: (versiones) => this.versiones.set(versiones),
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  enviarARevision(): void {
    this.errorMsg.set(null);
    this.enviandoRevision.set(true);
    this.api.enviarARevision(this.presupuestoId, { mensaje: 'Envío a revisión desde el presupuestador.' }).subscribe({
      next: (version) => {
        this.versiones.update((actuales) => [version, ...actuales]);
        this.enviandoRevision.set(false);
        this.successMsg.set(
          `Versión #${version.numero} congelada. Notificación registrada para ${version.destinatarioSub}.`,
        );
      },
      error: (err) => {
        this.enviandoRevision.set(false);
        this.errorMsg.set(extraerErrorHttp(err));
      },
    });
  }

  aprobar(): void {
    this.errorMsg.set(null);
    this.api.aprobar(this.presupuestoId).subscribe({
      next: (detalle) => {
        this.aplicarDetalle(detalle);
        this.successMsg.set('Presupuesto aprobado e inmovilizado.');
      },
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  private aplicarDetalle(detalle: PresupuestoDetalle): void {
    this.presupuesto.set(detalle);
    this.paramForm = {
      porcentajeAdministracion: detalle.porcentajeAdministracion,
      porcentajeImprevistos: detalle.porcentajeImprevistos,
      porcentajeUtilidad: detalle.porcentajeUtilidad,
      costoFijo: detalle.costoFijo,
      precioVentaUnitario: detalle.precioVentaUnitario,
      costoVariableUnitario: detalle.costoVariableUnitario,
    };
    for (const cap of detalle.capitulos) {
      if (!this.nuevoItemPorCap[cap.id]) {
        this.nuevoItemPorCap[cap.id] = { insumoId: '', cantidad: '' };
      }
    }
  }
}
