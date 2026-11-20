import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import {
  ComparacionVersiones,
  DesembolsosResponse,
  Insumo,
  InsumosCriticos,
  ItemPresupuestoDetalle,
  PresupuestoDetalle,
  PrevisualizacionPrecios,
  SimulacionSensibilidad,
  VersionPresupuestoDetalle,
  VersionPresupuestoResumen,
} from '../../core/models/api.models';
import { InsumoApiService } from '../../core/services/insumo-api.service';
import { PresupuestoApiService } from '../../core/services/presupuesto-api.service';
import { extraerErrorHttp } from '../../core/utils/http-error.util';

type DetalleTab = 'presupuesto' | 'aiu' | 'precios' | 'sensibilidad' | 'versiones' | 'desembolsos';

@Component({
  selector: 'app-presupuesto-detalle',
  standalone: true,
  imports: [FormsModule, DecimalPipe, RouterLink],
  template: `
    <section class="page-section">
      <!-- HEADER CONTEXTUAL (Figma P03-P08) -->
      <div class="header-row">
        <div style="display: flex; flex-direction: column; gap: 4px;">
          <div class="header-meta">
            <a routerLink="/presupuestos" class="btn-outline btn-sm">← Mis presupuestos</a>
            @if (presupuesto(); as p) {
              <h2 class="page-title">{{ p.codigo }} · {{ p.nombre }}</h2>
              <span [class]="estadoBadgeClass(p.estado)">{{ p.estado }}</span>
            }
          </div>
          @if (presupuesto(); as p) {
            <p class="page-subtitle">
              Cliente: <strong>{{ p.cliente }}</strong> ·
              Creador: <code>{{ p.creadoPor }}</code>
              @if (p.enviadoRevisionPor) { · Enviado: <code>{{ p.enviadoRevisionPor }}</code> }
              @if (p.aprobadoPor) { · Aprobado: <code>{{ p.aprobadoPor }}</code> }
              @if (p.presupuestoBaseId) {
                · <a [routerLink]="['/escenarios/comparar']">Comparar escenario base</a>
              }
            </p>
          }
        </div>

        @if (presupuesto(); as p) {
          <div class="actions-row">
            <button type="button" class="btn-outline" (click)="exportarCsv()">Exportar CSV</button>
            <a
              [routerLink]="['/evaluacion-financiera']"
              [queryParams]="{ inversion: p.precioVenta, codigo: p.codigo }"
              class="btn-outline"
            >
              Evaluar VPN / TIR
            </a>
            @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
              <button type="button" class="btn-primary" (click)="mostrarEnvioRevision.set(true)">
                Enviar a Revisión
              </button>
            }
            @if (authService.isRevisor() && p.estado !== 'APROBADO') {
              <button type="button" class="btn-success" (click)="aprobar()">Aprobar</button>
            }
            @if (authService.isRevisor() && p.estado === 'EN_REVISION') {
              <button type="button" class="btn-danger" (click)="mostrarRechazo.set(true)">
                Rechazar con Motivo
              </button>
            }
          </div>
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
        @if (p.motivoRechazo) {
          <div class="alert-warning">
            <div>
              <strong>Presupuesto devuelto por el revisor (<code>{{ p.rechazadoPor || '-' }}</code>):</strong>
              {{ p.motivoRechazo }}
            </div>
          </div>
        }
        @if (p.estado === 'EN_REVISION') {
          <div class="alert-warning">
            <div>
              Este presupuesto está <strong>EN_REVISION</strong> (enviado por <code>{{ p.enviadoRevisionPor || '-' }}</code>).
              Edición bloqueada (HTTP 409) hasta decisión del revisor.
            </div>
          </div>
        }
        @if (p.estado === 'APROBADO') {
          <div class="alert-info">
            <div>
              <strong>Presupuesto Aprobado (Solo lectura):</strong> quedó inmovilizado por <code>{{ p.aprobadoPor || '-' }}</code> y no admite modificaciones.
            </div>
          </div>
        }

        <!-- SUB-TABS HORIZONTALES (Figma P03..P08) -->
        <nav class="subtabs-bar" aria-label="Secciones del presupuesto">
          <button type="button" class="subtab-btn" [class.active]="activeTab() === 'presupuesto'" (click)="activeTab.set('presupuesto')">
            Capítulos e Ítems <span class="subtab-count">{{ p.capitulos.length }}</span>
          </button>
          <button type="button" class="subtab-btn" [class.active]="activeTab() === 'aiu'" (click)="activeTab.set('aiu')">
            Resumen AIU y Equilibrio
          </button>
          <button type="button" class="subtab-btn" [class.active]="activeTab() === 'precios'" (click)="seleccionarPrecios()">
            Precios Catálogo
            @if (previewPrecios()?.cantidadItemsConCambio) {
              <span class="nav-pill" style="margin-left: 5px;">{{ previewPrecios()!.cantidadItemsConCambio }}</span>
            }
          </button>
          <button type="button" class="subtab-btn" [class.active]="activeTab() === 'sensibilidad'" (click)="activeTab.set('sensibilidad')">
            Sensibilidad y Críticos
          </button>
          <button type="button" class="subtab-btn" [class.active]="activeTab() === 'versiones'" (click)="activeTab.set('versiones')">
            Versiones <span class="subtab-count">{{ versiones().length }}</span>
          </button>
          <button type="button" class="subtab-btn" [class.active]="activeTab() === 'desembolsos'" (click)="activeTab.set('desembolsos')">
            Desembolsos
          </button>
        </nav>

        <!-- =================================================================
             TAB 1: CAPÍTULOS E ÍTEMS (P03 + P03b + P03c Drawer)
             ================================================================= -->
        @if (activeTab() === 'presupuesto') {
          <div class="kpi-grid">
            <div class="stat-box hero-dark">
              <span>Costo Directo (CD)</span>
              <strong>$ {{ p.costoDirecto | number: '1.2-2' }}</strong>
              <small>Calculado por el servidor (BigDecimal HALF_UP)</small>
            </div>
            <div class="stat-box highlight">
              <span>Precio de Venta (CD + AIU)</span>
              <strong>$ {{ p.precioVenta | number: '1.2-2' }}</strong>
              <small>AIU: {{ p.porcentajeAdministracion }}% / {{ p.porcentajeImprevistos }}% / {{ p.porcentajeUtilidad }}%</small>
            </div>
            <div class="stat-box">
              <span>Estructura actual</span>
              <strong>{{ p.capitulos.length }} cap. · {{ totalItemsCount() }} ítems</strong>
              <small>Cada ítem guarda copia congelada de su precio</small>
            </div>
          </div>

          @if (previewPrecios(); as prev) {
            @if (prev.cantidadItemsConCambio > 0 && authService.isPresupuestador() && p.estado === 'BORRADOR') {
              <div class="alert-warning">
                <div>
                  <strong>{{ prev.cantidadItemsConCambio }} insumo(s)</strong> tienen precio distinto en el catálogo maestro. El presupuesto conserva su precio copiado.
                </div>
                <button type="button" class="btn-outline btn-sm" (click)="activeTab.set('precios')">Revisar y actualizar</button>
              </div>
            }
          }

          <div class="card">
            <div class="header-row" style="margin-bottom: 12px;">
              <div>
                <h3>Editor de Capítulos e Ítems</h3>
                <p class="page-subtitle">Haz clic en cualquier ítem (🔍 Trazabilidad) para inspeccionar fórmula y peso sobre el costo directo.</p>
              </div>
              @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
                <button type="button" class="btn-primary" (click)="mostrarNuevoCapModal.set(true)">
                  + Agregar capítulo
                </button>
              }
            </div>

            @for (cap of p.capitulos; track cap.id) {
              <div class="chapter-block">
                <div class="chapter-header" (click)="toggleCapitulo(cap.id)">
                  <div class="chapter-title-group">
                    <span class="chapter-toggle-pill">{{ estaColapsado(cap.id) ? '▶' : '▼' }}</span>
                    <div>
                      <h4>{{ cap.orden }}. {{ cap.codigo }} · {{ cap.nombre }}</h4>
                      <small style="color: var(--muted);">{{ cap.items.length }} ítem(s)</small>
                    </div>
                  </div>
                  <div class="chapter-actions" (click)="$event.stopPropagation()">
                    <strong>Subtotal: $ {{ cap.subtotalCapitulo | number: '1.2-2' }}</strong>
                    @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
                      <button type="button" class="btn-outline btn-sm" (click)="eliminarCapitulo(cap.id)">
                        Eliminar capítulo
                      </button>
                    }
                  </div>
                </div>

                @if (!estaColapsado(cap.id)) {
                  <div style="margin-top: 14px;">
                    @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
                      <form (ngSubmit)="agregarItem(cap.id)" class="inline-form" style="margin-bottom: 10px;">
                        <select [(ngModel)]="nuevoItemPorCap[cap.id].insumoId" [name]="'ins_' + cap.id" required style="flex: 1; min-width: 240px;">
                          <option value="">Seleccione insumo del catálogo...</option>
                          @for (ins of catalogoInsumos(); track ins.id) {
                            <option [value]="ins.id">
                              {{ ins.codigo }} - {{ ins.descripcion }} ({{ ins.precioUnitario }} / {{ ins.unidadMedida }})
                            </option>
                          }
                        </select>
                        <input
                          [(ngModel)]="nuevoItemPorCap[cap.id].cantidad"
                          [name]="'cant_' + cap.id"
                          placeholder="Cantidad (ej. 12.500)"
                          required
                          style="width: 170px;"
                        />
                        <button type="submit" class="btn-primary btn-sm">+ Agregar ítem</button>
                      </form>
                    }

                    <table>
                      <thead>
                        <tr>
                          <th>#</th>
                          <th>Código</th>
                          <th>Insumo</th>
                          <th>Und.</th>
                          <th class="num">Cant.</th>
                          <th class="num">Precio Unit.</th>
                          <th class="num">Parcial (COP)</th>
                          <th>Acciones</th>
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
                            <td class="num"><strong>$ {{ item.valorParcial | number: '1.2-2' }}</strong></td>
                            <td class="actions-cell" style="white-space: nowrap;">
                              <button type="button" class="btn-outline btn-sm" title="¿Cómo se calcula?" (click)="abrirTrazabilidad(cap, item)">
                                ?
                              </button>
                              @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
                                <button type="button" class="btn-secondary btn-sm" (click)="abrirEditarItem(cap.id, item)">
                                  Editar
                                </button>
                                <button type="button" class="btn-outline btn-sm" (click)="eliminarItem(cap.id, item.id)">
                                  Quitar
                                </button>
                              }
                            </td>
                          </tr>
                        } @empty {
                          <tr>
                            <td colspan="8" style="color: var(--muted);">Este capítulo aún no tiene ítems.</td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                }
              </div>
            } @empty {
              <div class="stat-box" style="text-align: center; padding: 28px;">
                <span>Sin capítulos</span>
                <p style="color: var(--muted); margin: 6px 0 0;">Agrega un capítulo para comenzar a cotizar ítems desde el catálogo maestro.</p>
              </div>
            }
          </div>
        }

        <!-- =================================================================
             TAB 2: RESUMEN AIU Y PUNTO DE EQUILIBRIO (Figma P04)
             ================================================================= -->
        @if (activeTab() === 'aiu') {
          <div class="split-2col">
            <div class="card">
              <h3>Porcentajes sobre Costo Directo (AIU)</h3>
              <p class="page-subtitle">El recálculo exacto se ejecuta en el servidor tras guardar.</p>

              <form (ngSubmit)="guardarParametros()" class="grid-form">
                <label>
                  % Administración (A)
                  <input
                    type="number"
                    step="0.01"
                    [(ngModel)]="paramForm.porcentajeAdministracion"
                    name="porcA"
                    [disabled]="p.estado !== 'BORRADOR' || !authService.isPresupuestador()"
                  />
                </label>
                <label>
                  % Imprevistos (I)
                  <input
                    type="number"
                    step="0.01"
                    [(ngModel)]="paramForm.porcentajeImprevistos"
                    name="porcI"
                    [disabled]="p.estado !== 'BORRADOR' || !authService.isPresupuestador()"
                  />
                </label>
                <label>
                  % Utilidad (U)
                  <input
                    type="number"
                    step="0.01"
                    [(ngModel)]="paramForm.porcentajeUtilidad"
                    name="porcU"
                    [disabled]="p.estado !== 'BORRADOR' || !authService.isPresupuestador()"
                  />
                </label>
                @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
                  <div class="actions-row" style="grid-column: 1 / -1;">
                    <button type="submit" class="btn-primary">Recalcular y Guardar AIU / Equilibrio</button>
                  </div>
                }
              </form>

              <div style="margin-top: 16px;">
                <div class="detail-kv">
                  <span>Costo Directo (CD)</span>
                  <strong>$ {{ p.costoDirecto | number: '1.2-2' }}</strong>
                </div>
                <div class="detail-kv">
                  <span>Administración ({{ p.porcentajeAdministracion | number: '1.2-2' }}%)</span>
                  <strong>$ {{ p.valorAdministracion | number: '1.2-2' }}</strong>
                </div>
                <div class="detail-kv">
                  <span>Imprevistos ({{ p.porcentajeImprevistos | number: '1.2-2' }}%)</span>
                  <strong>$ {{ p.valorImprevistos | number: '1.2-2' }}</strong>
                </div>
                <div class="detail-kv" style="border-bottom: none;">
                  <span>Utilidad ({{ p.porcentajeUtilidad | number: '1.2-2' }}%)</span>
                  <strong>$ {{ p.valorUtilidad | number: '1.2-2' }}</strong>
                </div>
              </div>
            </div>

            <div style="display: flex; flex-direction: column; gap: 16px;">
              <div class="stat-box hero-dark">
                <span>Precio de Venta (CD + A + I + U)</span>
                <strong>$ {{ p.precioVenta | number: '1.2-2' }} COP</strong>
                <small>
                  = {{ p.costoDirecto | number: '1.2-2' }} + {{ p.valorAdministracion | number: '1.2-2' }}
                  + {{ p.valorImprevistos | number: '1.2-2' }} + {{ p.valorUtilidad | number: '1.2-2' }}
                </small>
              </div>

              <div class="split-2col">
                <div class="stat-box highlight">
                  <span>{{ p.rotuloMargenSobreCosto }}</span>
                  <strong>{{ p.margenSobreCostoPorcentaje | number: '1.2-2' }}%</strong>
                  <small>(venta - costo) / costo</small>
                </div>
                <div class="stat-box">
                  <span>{{ p.rotuloMargenSobrePrecioVenta }}</span>
                  <strong>{{ p.margenSobrePrecioVentaPorcentaje | number: '1.2-2' }}%</strong>
                  <small>(venta - costo) / venta</small>
                </div>
              </div>
              <small style="color: var(--muted);">
                Referencia comercial: si costo = 100 y venta = 125 -> 25.00% sobre costo (Markup) y 20.00% sobre precio de venta (Gross Margin).
              </small>
            </div>
          </div>

          <div class="card">
            <h3>Punto de Equilibrio Operativo</h3>
            <p class="page-subtitle">Costo fijo / margen de contribución unitario (si contribución &lt;= 0, el backend advierte explícitamente sin dividir por cero).</p>
            <div class="split-wide-left" style="margin-top: 12px;">
              <form (ngSubmit)="guardarParametros()" class="grid-form" style="margin-top: 0;">
                <label>
                  Costo Fijo (COP)
                  <input
                    type="number"
                    step="0.01"
                    [(ngModel)]="paramForm.costoFijo"
                    name="costoFijo"
                    [disabled]="p.estado !== 'BORRADOR' || !authService.isPresupuestador()"
                  />
                </label>
                <label>
                  Precio Venta Unitario (PE)
                  <input
                    type="number"
                    step="0.01"
                    [(ngModel)]="paramForm.precioVentaUnitario"
                    name="pvu"
                    [disabled]="p.estado !== 'BORRADOR' || !authService.isPresupuestador()"
                  />
                </label>
                <label>
                  Costo Variable Unitario (PE)
                  <input
                    type="number"
                    step="0.01"
                    [(ngModel)]="paramForm.costoVariableUnitario"
                    name="cvu"
                    [disabled]="p.estado !== 'BORRADOR' || !authService.isPresupuestador()"
                  />
                </label>
                @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
                  <div class="actions-row">
                    <button type="submit" class="btn-primary">Calcular Equilibrio</button>
                  </div>
                }
              </form>

              @if (p.puntoEquilibrio; as pe) {
                <div class="stat-box" [class.success-box]="pe.viable" [class.danger-box]="!pe.viable">
                  <span>Equilibrio calculado</span>
                  <strong>{{ pe.viable ? (pe.cantidadEquilibrio | number: '1.2-2') + ' unidades' : pe.mensaje }}</strong>
                  <small>{{ pe.mensaje }}</small>
                </div>
              } @else {
                <div class="stat-box">
                  <span>Equilibrio</span>
                  <strong>Sin configurar</strong>
                  <small>Ingresa costo fijo, precio venta y costo variable unitario.</small>
                </div>
              }
            </div>
          </div>
        }

        <!-- =================================================================
             TAB 3: PRECIOS VS CATÁLOGO (Figma P11)
             ================================================================= -->
        @if (activeTab() === 'precios') {
          <div class="card">
            <div class="header-row" style="margin-bottom: 12px;">
              <div>
                <h3>Actualización Explícita de Precios desde Catálogo</h3>
                <p class="page-subtitle">Ningún presupuesto cambia retroactivamente al editar un insumo: aquí previsualizas diferencias antes de confirmar.</p>
              </div>
              <button
                type="button"
                class="btn-primary"
                (click)="previsualizarPrecios()"
                [disabled]="p.estado !== 'BORRADOR' || !authService.isPresupuestador()"
              >
                Previsualizar Cambios de Catálogo
              </button>
            </div>

            @if (previewPrecios(); as prev) {
              <div class="kpi-grid" style="margin-bottom: 14px;">
                <div class="stat-box">
                  <span>Ítems revisados</span>
                  <strong>{{ prev.totalItemsRevisados }}</strong>
                </div>
                <div class="stat-box" [class.highlight]="prev.cantidadItemsConCambio > 0">
                  <span>Ítems con cambio vigente</span>
                  <strong>{{ prev.cantidadItemsConCambio }}</strong>
                </div>
              </div>

              @if (prev.cambios.length > 0) {
                <table>
                  <thead>
                    <tr>
                      <th>Capítulo</th>
                      <th>Código</th>
                      <th>Descripción</th>
                      <th class="num">Precio Copiado (Presupuesto)</th>
                      <th class="num">Precio Catálogo Vigente</th>
                      <th class="num">Diferencia Unitaria</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (c of prev.cambios; track c.itemId) {
                      <tr>
                        <td>{{ c.capituloCodigo }}</td>
                        <td><code>{{ c.codigoInsumo }}</code></td>
                        <td>{{ c.descripcion }}</td>
                        <td class="num">{{ c.precioAnteriorPresupuesto }}</td>
                        <td class="num"><strong>{{ c.precioNuevoCatalogo }}</strong></td>
                        <td class="num" [class]="Number(c.diferenciaUnitaria) > 0 ? 'delta-up' : 'delta-down'">
                          {{ Number(c.diferenciaUnitaria) > 0 ? '+' : '' }}{{ c.diferenciaUnitaria }}
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
                @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
                  <div class="actions-row" style="margin-top: 14px;">
                    <button type="button" class="btn-primary" (click)="confirmarActualizacionPrecios()">
                      Confirmar y Aplicar Precios Vigentes
                    </button>
                    <button type="button" class="btn-outline" (click)="previewPrecios.set(null)">
                      Cancelar vista previa
                    </button>
                  </div>
                }
              } @else {
                <div class="alert-success">Todos los ítems coinciden exactamente con el precio vigente del catálogo.</div>
              }
            } @else {
              <p style="color: var(--muted);">Pulsa <strong>Previsualizar Cambios de Catálogo</strong> para verificar si existen insumos desactualizados.</p>
            }
          </div>
        }

        <!-- =================================================================
             TAB 4: SENSIBILIDAD E INSUMOS CRÍTICOS (Figma P12)
             ================================================================= -->
        @if (activeTab() === 'sensibilidad') {
          <div class="split-2col">
            <div class="card">
              <h3>Simular variación de un insumo</h3>
              <p class="page-subtitle">Simulación en servidor que no modifica el presupuesto guardado.</p>

              <div class="grid-form" style="margin-bottom: 14px;">
                <label>
                  Código de insumo
                  <input
                    [(ngModel)]="simForm.codigoInsumo"
                    name="simCod"
                    list="insumosEnPresupuesto"
                    placeholder="Ej. CEM-001"
                  />
                  <datalist id="insumosEnPresupuesto">
                    @for (cod of codigosInsumoEnPresupuesto(); track cod) {
                      <option [value]="cod"></option>
                    }
                  </datalist>
                </label>
                <label>
                  Variación (%)
                  <input type="number" step="0.01" [(ngModel)]="simForm.porcentajeVariacion" name="simPct" placeholder="10" />
                </label>
                <div class="actions-row" style="grid-column: 1 / -1;">
                  <button type="button" class="btn-primary" (click)="simularSensibilidad()">Simular variación</button>
                </div>
              </div>

              @if (simulacion(); as sim) {
                <table>
                  <thead>
                    <tr>
                      <th>Indicador</th>
                      <th class="num">Actual</th>
                      <th class="num">Simulado</th>
                      <th class="num">Dif. (%)</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td>Costo Directo</td>
                      <td class="num">{{ sim.costoDirectoOriginal | number: '1.2-2' }}</td>
                      <td class="num"><strong>{{ sim.costoDirectoSimulado | number: '1.2-2' }}</strong></td>
                      <td class="num" [class]="sim.variacionCostoDirectoPorcentaje > 0 ? 'delta-up' : 'delta-down'">
                        {{ sim.variacionCostoDirectoPorcentaje > 0 ? '+' : '' }}{{ sim.variacionCostoDirectoPorcentaje | number: '1.2-2' }}%
                      </td>
                    </tr>
                    <tr>
                      <td>Precio Venta (CD + AIU)</td>
                      <td class="num">{{ sim.precioVentaOriginal | number: '1.2-2' }}</td>
                      <td class="num"><strong>{{ sim.precioVentaSimulado | number: '1.2-2' }}</strong></td>
                      <td class="num" [class]="sim.variacionPrecioVentaPorcentaje > 0 ? 'delta-up' : 'delta-down'">
                        {{ sim.variacionPrecioVentaPorcentaje > 0 ? '+' : '' }}{{ sim.variacionPrecioVentaPorcentaje | number: '1.2-2' }}%
                      </td>
                    </tr>
                    <tr>
                      <td>Punto de Equilibrio</td>
                      @if (sim.puntoEquilibrioOriginal && sim.puntoEquilibrioSimulado) {
                        <td class="num">{{ sim.puntoEquilibrioOriginal.viable ? (sim.puntoEquilibrioOriginal.cantidadEquilibrio | number: '1.2-2') + ' u' : 'No viable' }}</td>
                        <td class="num"><strong>{{ sim.puntoEquilibrioSimulado.viable ? (sim.puntoEquilibrioSimulado.cantidadEquilibrio | number: '1.2-2') + ' u' : 'No viable' }}</strong></td>
                        <td class="num">—</td>
                      } @else {
                        <td colspan="3" style="color: var(--muted); text-align: right;">Equilibrio sin configurar</td>
                      }
                    </tr>
                  </tbody>
                </table>
              }
            </div>

            <div class="card">
              <div class="header-row" style="margin-bottom: 10px;">
                <div>
                  <h3>Insumos críticos</h3>
                  <p class="page-subtitle">Participación en el Costo Directo e impacto al variar +{{ simForm.porcentajeVariacion || 10 }}%</p>
                </div>
                <button type="button" class="btn-outline btn-sm" (click)="analizarCriticos()">Analizar críticos</button>
              </div>

              @if (criticos(); as crit) {
                <div class="bar-list" style="margin-bottom: 16px;">
                  @for (ic of crit.insumosCriticos.slice(0, 6); track ic.codigoInsumo; let i = $index) {
                    <div class="bar-item">
                      <span class="bar-label" [title]="ic.descripcion">{{ ic.codigoInsumo }} · {{ ic.descripcion }}</span>
                      <div class="bar-track">
                        <div class="bar-fill" [class.top]="i === 0" [style.width.%]="barWidthPct(ic.participacionPorcentaje)"></div>
                      </div>
                      <span class="bar-value">{{ ic.participacionPorcentaje | number: '1.1-1' }}%</span>
                    </div>
                  }
                </div>

                <table>
                  <thead>
                    <tr>
                      <th>Insumo</th>
                      <th class="num">Aporte CD</th>
                      <th class="num">Part. (%)</th>
                      <th class="num">Impacto PV</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (ic of crit.insumosCriticos; track ic.codigoInsumo) {
                      <tr>
                        <td><code>{{ ic.codigoInsumo }}</code> · {{ ic.descripcion }}</td>
                        <td class="num">{{ ic.aporteCostoDirecto | number: '1.2-2' }}</td>
                        <td class="num">{{ ic.participacionPorcentaje | number: '1.2-2' }}%</td>
                        <td class="num"><strong>{{ ic.impactoPrecioVenta | number: '1.2-2' }}</strong></td>
                      </tr>
                    }
                  </tbody>
                </table>
              } @else {
                <p style="color: var(--muted);">Pulsa <strong>Analizar críticos</strong> para calcular los insumos de mayor peso.</p>
              }
            </div>
          </div>
        }

        <!-- =================================================================
             TAB 5: VERSIONES INMUTABLES Y COMPARADOR (Figma P07)
             ================================================================= -->
        @if (activeTab() === 'versiones') {
          <div class="split-master-detail">
            <div class="card">
              <h3>Versiones guardadas</h3>
              <p class="page-subtitle">Snapshots inmutables creados manual o automáticamente al enviar a revisión.</p>

              @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
                <form (ngSubmit)="guardarVersionManual()" class="inline-form" style="margin-top: 10px;">
                  <input
                    [(ngModel)]="nuevaVersionEtiqueta"
                    name="verEtiqueta"
                    placeholder="Etiqueta (ej. Cotización v1)"
                    required
                    style="flex: 1;"
                  />
                  <button type="submit" class="btn-primary btn-sm">Guardar Snapshot</button>
                </form>
              }

              <div class="version-list">
                @for (v of versiones(); track v.id) {
                  <button
                    type="button"
                    class="version-item"
                    [class.selected]="snapshotSeleccionado()?.id === v.id"
                    (click)="verSnapshotVersion(v.id)"
                  >
                    <div class="header-row">
                      <strong>v{{ v.numeroVersion }} · {{ v.etiqueta }}</strong>
                      <span class="badge-review">{{ v.estadoEnMomento }}</span>
                    </div>
                    <small style="color: var(--muted);">PV: $ {{ v.precioVenta | number: '1.2-2' }} · {{ v.creadoPor }}</small>
                    @if (v.comentarioEnvio) {
                      <small style="color: var(--text);">"{{ v.comentarioEnvio }}"</small>
                    }
                  </button>
                } @empty {
                  <p style="color: var(--muted);">No hay versiones congeladas todavía.</p>
                }
              </div>
            </div>

            <div style="display: flex; flex-direction: column; gap: 16px;">
              @if (snapshotSeleccionado(); as snap) {
                <div class="card">
                  <div class="header-row" style="margin-bottom: 10px;">
                    <div>
                      <h3>Snapshot v{{ snap.numeroVersion }} · {{ snap.etiqueta }}</h3>
                      <p class="page-subtitle">{{ snap.fechaCreacion }} · Creador: <code>{{ snap.creadoPor }}</code></p>
                    </div>
                    <button type="button" class="btn-outline btn-sm" (click)="snapshotSeleccionado.set(null)">Cerrar</button>
                  </div>
                  <div class="kpi-grid">
                    <div class="stat-box">
                      <span>Costo Directo</span>
                      <strong>$ {{ snap.costoDirecto | number: '1.2-2' }}</strong>
                    </div>
                    <div class="stat-box">
                      <span>AIU (A / I / U)</span>
                      <strong>{{ snap.valorAdministracion | number: '1.0-0' }} / {{ snap.valorImprevistos | number: '1.0-0' }} / {{ snap.valorUtilidad | number: '1.0-0' }}</strong>
                    </div>
                    <div class="stat-box highlight">
                      <span>Precio Venta Congelado</span>
                      <strong>$ {{ snap.precioVenta | number: '1.2-2' }}</strong>
                    </div>
                  </div>
                </div>
              }

              <div class="card">
                <h3>Comparar Dos Versiones (UC-39)</h3>
                @if (versiones().length >= 2) {
                  <div class="grid-form">
                    <label>
                      Versión Base (A)
                      <select [(ngModel)]="versionAId" name="versionAId">
                        <option value="">Seleccione versión A...</option>
                        @for (v of versiones(); track v.id) {
                          <option [value]="v.id">v{{ v.numeroVersion }} · {{ v.etiqueta }}</option>
                        }
                      </select>
                    </label>
                    <label>
                      Versión Comparada (B)
                      <select [(ngModel)]="versionBId" name="versionBId">
                        <option value="">Seleccione versión B...</option>
                        @for (v of versiones(); track v.id) {
                          <option [value]="v.id">v{{ v.numeroVersion }} · {{ v.etiqueta }}</option>
                        }
                      </select>
                    </label>
                    <div class="actions-row">
                      <button type="button" class="btn-primary" (click)="compararVersiones()" [disabled]="!versionAId || !versionBId">
                        Comparar Versiones
                      </button>
                    </div>
                  </div>

                  @if (comparacionVersiones(); as comp) {
                    <div class="kpi-grid" style="margin-top: 14px;">
                      <div class="stat-box">
                        <span>Diferencia Costo Directo (B - A)</span>
                        <strong>$ {{ comp.diferenciaCostoDirecto | number: '1.2-2' }} COP</strong>
                      </div>
                      <div class="stat-box highlight">
                        <span>Diferencia Precio Venta (B - A)</span>
                        <strong>$ {{ comp.diferenciaPrecioVenta | number: '1.2-2' }} COP</strong>
                      </div>
                      <div class="stat-box">
                        <span>Resumen Cambios</span>
                        <strong>+{{ comp.itemsAgregados.length }} / -{{ comp.itemsEliminados.length }} / ~{{ comp.itemsModificados.length }}</strong>
                        <small>Agregados / Eliminados / Modificados</small>
                      </div>
                    </div>

                    @if (comp.itemsAgregados.length > 0) {
                      <h4 style="margin-top: 14px;"><span class="badge-approved">Agregados</span></h4>
                      <table>
                        <thead>
                          <tr><th>Capítulo</th><th>Código</th><th>Descripción</th><th class="num">Cantidad</th><th class="num">Precio</th><th class="num">Parcial</th></tr>
                        </thead>
                        <tbody>
                          @for (it of comp.itemsAgregados; track it.itemId) {
                            <tr>
                              <td>{{ it.capituloCodigo }}</td>
                              <td><code>{{ it.codigoInsumo }}</code></td>
                              <td>{{ it.descripcion }}</td>
                              <td class="num">{{ it.cantidad }}</td>
                              <td class="num">{{ it.precioUnitarioCopia }}</td>
                              <td class="num">{{ it.valorParcial | number: '1.2-2' }}</td>
                            </tr>
                          }
                        </tbody>
                      </table>
                    }

                    @if (comp.itemsEliminados.length > 0) {
                      <h4 style="margin-top: 14px;"><span class="badge-rejected">Eliminados</span></h4>
                      <table>
                        <thead>
                          <tr><th>Capítulo</th><th>Código</th><th>Descripción</th><th class="num">Cantidad</th><th class="num">Precio</th><th class="num">Parcial</th></tr>
                        </thead>
                        <tbody>
                          @for (it of comp.itemsEliminados; track it.itemId) {
                            <tr>
                              <td>{{ it.capituloCodigo }}</td>
                              <td><code>{{ it.codigoInsumo }}</code></td>
                              <td>{{ it.descripcion }}</td>
                              <td class="num">{{ it.cantidad }}</td>
                              <td class="num">{{ it.precioUnitarioCopia }}</td>
                              <td class="num">{{ it.valorParcial | number: '1.2-2' }}</td>
                            </tr>
                          }
                        </tbody>
                      </table>
                    }

                    @if (comp.itemsModificados.length > 0) {
                      <h4 style="margin-top: 14px;"><span class="badge-review">Modificados</span></h4>
                      <table>
                        <thead>
                          <tr>
                            <th>Capítulo</th><th>Código</th><th>Descripción</th>
                            <th class="num">Cant. Antes -> Después</th>
                            <th class="num">Precio Antes -> Después</th>
                            <th class="num">Dif. Parcial (COP)</th>
                          </tr>
                        </thead>
                        <tbody>
                          @for (it of comp.itemsModificados; track it.itemId) {
                            <tr>
                              <td>{{ it.capituloCodigo }}</td>
                              <td><code>{{ it.codigoInsumo }}</code></td>
                              <td>{{ it.descripcion }}</td>
                              <td class="num">{{ it.cantidadAntes }} -> {{ it.cantidadDespues }}</td>
                              <td class="num">{{ it.precioAntes }} -> {{ it.precioDespues }}</td>
                              <td class="num"><strong>{{ it.diferenciaParcial | number: '1.2-2' }}</strong></td>
                            </tr>
                          }
                        </tbody>
                      </table>
                    }
                  }
                } @else {
                  <p style="color: var(--muted); margin-top: 6px;">Se requieren al menos dos versiones congeladas para comparar cambios ítem a ítem.</p>
                }
              </div>
            </div>
          </div>
        }

        <!-- =================================================================
             TAB 6: DESEMBOLSOS EN EL TIEMPO (Figma P08)
             ================================================================= -->
        @if (activeTab() === 'desembolsos') {
          <div class="card">
            <div class="header-row" style="margin-bottom: 12px;">
              <div>
                <h3>Desembolsos en el Tiempo por Capítulo (RF-12 · UC-42)</h3>
                <p class="page-subtitle">Cada capítulo distribuye el 100% entre sus semanas; la última semana activa absorbe cualquier residuo exacto ($0.00 COP de desviación).</p>
              </div>
              @if (authService.isPresupuestador() && p.estado === 'BORRADOR' && p.capitulos.length > 0) {
                <div class="inline-form">
                  <label style="display:flex; align-items:center; gap:6px; font-size:0.85rem;">
                    Semanas:
                    <input type="number" min="1" max="52" [(ngModel)]="desembolsoSemanasInput" name="desSemanas" style="width: 78px;" />
                  </label>
                  <button type="button" class="btn-outline" (click)="repartirEquitativoDesembolsos()">Ajustar Semanas</button>
                  <button type="button" class="btn-primary" (click)="guardarDesembolsos()">Guardar Desembolsos</button>
                </div>
              }
            </div>

            @if (desembolsos(); as des) {
              @if (des.capitulos.length === 0) {
                <p style="color: var(--muted); margin-top: 10px;">Agrega capítulos en la pestaña Capítulos e Ítems para programar desembolsos.</p>
              } @else {
                <div class="kpi-grid" style="margin-bottom: 14px;">
                  <div class="stat-box">
                    <span>Costo Directo Total</span>
                    <strong>$ {{ des.costoDirectoTotal | number: '1.2-2' }} COP</strong>
                  </div>
                  <div class="stat-box success-box">
                    <span>Suma Total Desembolsos</span>
                    <strong>$ {{ des.totalDesembolsado | number: '1.2-2' }} COP</strong>
                    <small>Diferencia de conciliación: {{ des.diferenciaValidacion | number: '1.2-2' }} COP</small>
                  </div>
                </div>

                <table>
                  <thead>
                    <tr>
                      <th>Capítulo</th>
                      <th class="num">Subtotal (COP)</th>
                      @for (idx of semanasIndices(); track idx) {
                        <th class="num">Sem {{ idx + 1 }} (% / COP)</th>
                      }
                      <th class="num">Suma %</th>
                      <th class="num">Total Cap.</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (cap of des.capitulos; track cap.capituloId; let ci = $index) {
                      <tr>
                        <td><strong>{{ cap.codigo }}</strong> · {{ cap.nombre }}</td>
                        <td class="num">{{ cap.subtotalCapitulo | number: '1.2-2' }}</td>
                        @for (idx of semanasIndices(); track idx) {
                          <td class="num">
                            @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
                              <input
                                type="number"
                                step="0.01"
                                min="0"
                                max="100"
                                [(ngModel)]="desembolsoMatriz[ci][idx]"
                                [name]="'des_' + ci + '_' + idx"
                                style="width: 72px; text-align: right; margin-bottom: 4px;"
                              />
                              <div><small style="color: var(--muted);">{{ cap.montosPorSemana[idx] | number: '1.2-2' }}</small></div>
                            } @else {
                              <div>{{ cap.porcentajesPorSemana[idx] | number: '1.2-2' }}%</div>
                              <div><small style="color: var(--muted);">{{ cap.montosPorSemana[idx] | number: '1.2-2' }}</small></div>
                            }
                          </td>
                        }
                        <td class="num">
                          <span [class]="filaPctSuma(ci) === 100 ? 'badge-approved' : 'badge-rejected'">
                            {{ filaPctSuma(ci) | number: '1.0-2' }}%
                          </span>
                        </td>
                        <td class="num"><strong>$ {{ cap.totalCapituloDesembolsado | number: '1.2-2' }}</strong></td>
                      </tr>
                    }
                    <tr class="total-row">
                      <td><strong>Dinero necesario por semana</strong></td>
                      <td></td>
                      @for (tot of des.totalesPorSemana; track $index) {
                        <td class="num"><strong>{{ tot | number: '1.2-2' }}</strong></td>
                      }
                      <td></td>
                      <td class="num"><strong>$ {{ des.totalDesembolsado | number: '1.2-2' }}</strong></td>
                    </tr>
                    <tr>
                      <td><strong>Acumulado al corte</strong></td>
                      <td></td>
                      @for (ac of des.acumuladosPorSemana; track $index) {
                        <td class="num">{{ ac | number: '1.2-2' }}</td>
                      }
                      <td></td>
                      <td></td>
                    </tr>
                  </tbody>
                </table>
              }
            }
          </div>
        }
      }
    </section>

    <!-- MODAL: AGREGAR CAPÍTULO (Figma P03b) -->
    @if (mostrarNuevoCapModal() && authService.isPresupuestador()) {
      <div class="modal-backdrop" (click)="mostrarNuevoCapModal.set(false)">
        <div class="modal-dialog" (click)="$event.stopPropagation()">
          <div class="header-row">
            <div>
              <h3>Nuevo Capítulo</h3>
              <p class="page-subtitle">Agrupa partidas de obra (ej. CAP-01 · Preliminares).</p>
            </div>
            <button type="button" class="btn-outline btn-sm" (click)="mostrarNuevoCapModal.set(false)">Cerrar</button>
          </div>
          <form (ngSubmit)="agregarCapitulo()" class="grid-form" style="grid-template-columns: 1fr;">
            <label>
              Código del capítulo
              <input [(ngModel)]="nuevoCap.codigo" name="capCod" placeholder="CAP-01" required />
            </label>
            <label>
              Nombre del capítulo
              <input [(ngModel)]="nuevoCap.nombre" name="capNom" placeholder="Preliminares" required />
            </label>
            <div class="actions-row" style="justify-content: flex-end;">
              <button type="button" class="btn-outline" (click)="mostrarNuevoCapModal.set(false)">Cancelar</button>
              <button type="submit" class="btn-primary">Crear Capítulo</button>
            </div>
          </form>
        </div>
      </div>
    }

    <!-- MODAL: EDITAR ÍTEM -->
    @if (editandoItem(); as ei) {
      <div class="modal-backdrop" (click)="editandoItem.set(null)">
        <div class="modal-dialog" (click)="$event.stopPropagation()">
          <div class="header-row">
            <div>
              <h3>Editar Ítem {{ ei.item.codigoInsumo }}</h3>
              <p class="page-subtitle">{{ ei.item.descripcion }} ({{ ei.item.unidadMedida }})</p>
            </div>
            <button type="button" class="btn-outline btn-sm" (click)="editandoItem.set(null)">Cerrar</button>
          </div>
          <form (ngSubmit)="guardarItemEditado()" class="grid-form" style="grid-template-columns: 1fr;">
            <label>
              Cantidad (hasta 3 decimales)
              <input [(ngModel)]="editItemForm.cantidad" name="editCant" required />
            </label>
            <label>
              Precio Unitario en Presupuesto (hasta 3 decimales)
              <input [(ngModel)]="editItemForm.precioUnitarioCopia" name="editPrecio" required />
            </label>
            <div class="actions-row" style="justify-content: flex-end;">
              <button type="button" class="btn-outline" (click)="editandoItem.set(null)">Cancelar</button>
              <button type="submit" class="btn-primary">Guardar Ítem</button>
            </div>
          </form>
        </div>
      </div>
    }

    <!-- MODAL: ENVIAR A REVISIÓN (Figma P14) -->
    @if (mostrarEnvioRevision() && authService.isPresupuestador()) {
      <div class="modal-backdrop" (click)="mostrarEnvioRevision.set(false)">
        <div class="modal-dialog" (click)="$event.stopPropagation()">
          <div class="header-row">
            <div>
              <h3>Enviar Presupuesto a Revisión</h3>
              <p class="page-subtitle">Congela una versión inmutable y bloquea modificaciones mientras el revisor decide.</p>
            </div>
            <button type="button" class="btn-outline btn-sm" (click)="mostrarEnvioRevision.set(false)">Cerrar</button>
          </div>
          <form (ngSubmit)="enviarARevision()" class="grid-form" style="grid-template-columns: 1fr;">
            <label>
              Etiqueta de versión
              <input [(ngModel)]="envioForm.etiqueta" name="envioEtiqueta" placeholder="Ej. Entrega v1 para revisión" />
            </label>
            <label>
              Comentario para el revisor
              <textarea rows="3" [(ngModel)]="envioForm.comentario" name="envioComentario" placeholder="Notas o cambios realizados"></textarea>
            </label>
            <div class="actions-row" style="justify-content: flex-end;">
              <button type="button" class="btn-outline" (click)="mostrarEnvioRevision.set(false)">Cancelar</button>
              <button type="submit" class="btn-primary">Confirmar Envío a Revisión</button>
            </div>
          </form>
        </div>
      </div>
    }

    <!-- MODAL: RECHAZAR CON MOTIVO (Figma R04) -->
    @if (mostrarRechazo() && authService.isRevisor()) {
      <div class="modal-backdrop" (click)="mostrarRechazo.set(false)">
        <div class="modal-dialog" (click)="$event.stopPropagation()">
          <div class="header-row">
            <div>
              <h3>Rechazar Presupuesto</h3>
              <p class="page-subtitle">Devuelve el presupuesto a BORRADOR registrando el motivo obligatorio.</p>
            </div>
            <button type="button" class="btn-outline btn-sm" (click)="mostrarRechazo.set(false)">Cerrar</button>
          </div>
          <form (ngSubmit)="rechazar()" class="grid-form" style="grid-template-columns: 1fr;">
            <label>
              Motivo del rechazo (obligatorio)
              <textarea
                rows="3"
                [(ngModel)]="motivoRechazo"
                name="motivoRechazo"
                required
                placeholder="Describe qué capítulos, cantidades o precios deben ajustarse..."
              ></textarea>
            </label>
            <div class="actions-row" style="justify-content: flex-end;">
              <button type="button" class="btn-outline" (click)="mostrarRechazo.set(false)">Cancelar</button>
              <button type="submit" class="btn-danger">Confirmar Rechazo</button>
            </div>
          </form>
        </div>
      </div>
    }

    <!-- DRAWER LATERAL: TRAZABILIDAD DEL ÍTEM ¿CÓMO SE CALCULA? (Figma P03c) -->
    @if (trazabilidad(); as tr) {
      <div class="drawer-backdrop" (click)="trazabilidad.set(null)">
        <div class="drawer-panel" (click)="$event.stopPropagation()">
          <div class="header-row">
            <div>
              <h3>¿Cómo se calcula?</h3>
              <p class="page-subtitle">Trazabilidad de {{ tr.item.codigoInsumo }} · {{ tr.item.descripcion }}</p>
            </div>
            <button type="button" class="btn-outline btn-sm" (click)="trazabilidad.set(null)">Cerrar</button>
          </div>

          <div class="formula-box">
            {{ tr.item.cantidad }} {{ tr.item.unidadMedida }} × $ {{ tr.item.precioUnitarioCopia }} = <strong>$ {{ tr.item.valorParcial | number: '1.2-2' }} COP</strong>
          </div>

          <div>
            <div class="detail-kv">
              <span>Capítulo</span>
              <strong>{{ tr.capCodigo }} · {{ tr.capNombre }}</strong>
            </div>
            <div class="detail-kv">
              <span>Unidad</span>
              <strong>{{ tr.item.unidadMedida }}</strong>
            </div>
            <div class="detail-kv">
              <span>Cantidad exacta (3 dec.)</span>
              <strong>{{ tr.item.cantidad }}</strong>
            </div>
            <div class="detail-kv">
              <span>Precio unitario copiado</span>
              <strong>$ {{ tr.item.precioUnitarioCopia }}</strong>
            </div>
            <div class="detail-kv">
              <span>Regla de redondeo (D-02)</span>
              <strong>HALF_UP a 2 decimales en servidor</strong>
            </div>
            <div class="detail-kv">
              <span>Peso en Costo Directo</span>
              <strong>{{ pesoItemPct(tr.item.valorParcial) | number: '1.2-2' }}%</strong>
            </div>
          </div>

          @if (cambioCatalogoItem(tr.item.id); as camb) {
            <div class="alert-warning">
              <div>
                <strong>Precio distinto en catálogo hoy:</strong>
                copiado <code>{{ camb.precioAnteriorPresupuesto }}</code> vs. catálogo vigente <code>{{ camb.precioNuevoCatalogo }}</code>.
              </div>
              <button type="button" class="btn-outline btn-sm" (click)="irAPreciosDesdeDrawer()">Ver en Precios Catálogo</button>
            </div>
          }
        </div>
      </div>
    }
  `,
})
export class PresupuestoDetalleComponent implements OnInit {
  readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(PresupuestoApiService);
  private readonly insumoApi = inject(InsumoApiService);
  readonly Number = Number;

  readonly activeTab = signal<DetalleTab>('presupuesto');
  readonly presupuesto = signal<PresupuestoDetalle | null>(null);
  readonly catalogoInsumos = signal<Insumo[]>([]);
  readonly previewPrecios = signal<PrevisualizacionPrecios | null>(null);
  readonly simulacion = signal<SimulacionSensibilidad | null>(null);
  readonly criticos = signal<InsumosCriticos | null>(null);
  readonly editandoItem = signal<{ capituloId: string; item: ItemPresupuestoDetalle } | null>(null);
  readonly trazabilidad = signal<{ capCodigo: string; capNombre: string; item: ItemPresupuestoDetalle } | null>(null);
  readonly versiones = signal<VersionPresupuestoResumen[]>([]);
  readonly snapshotSeleccionado = signal<VersionPresupuestoDetalle | null>(null);
  readonly comparacionVersiones = signal<ComparacionVersiones | null>(null);
  readonly desembolsos = signal<DesembolsosResponse | null>(null);
  readonly capitulosColapsados = signal<Record<string, boolean>>({});
  readonly mostrarNuevoCapModal = signal<boolean>(false);
  readonly mostrarEnvioRevision = signal<boolean>(false);
  readonly mostrarRechazo = signal<boolean>(false);
  readonly cargando = signal<boolean>(false);
  readonly errorMsg = signal<string | null>(null);
  readonly successMsg = signal<string | null>(null);

  readonly totalItemsCount = computed(() => {
    const p = this.presupuesto();
    if (!p) return 0;
    return p.capitulos.reduce((acc, c) => acc + c.items.length, 0);
  });

  readonly codigosInsumoEnPresupuesto = computed(() => {
    const p = this.presupuesto();
    if (!p) return [];
    const seen = new Set<string>();
    for (const c of p.capitulos) {
      for (const it of c.items) {
        seen.add(it.codigoInsumo);
      }
    }
    return Array.from(seen);
  });

  readonly maxParticipacionCriticos = computed(() => {
    const c = this.criticos();
    if (!c || c.insumosCriticos.length === 0) return 1;
    return Math.max(...c.insumosCriticos.map((ic) => ic.participacionPorcentaje || 0), 1);
  });

  nuevoCap = { codigo: '', nombre: '' };
  nuevoItemPorCap: Record<string, { insumoId: string; cantidad: string }> = {};
  editItemForm = { cantidad: '', precioUnitarioCopia: '' };
  simForm = { codigoInsumo: '', porcentajeVariacion: 10 };
  envioForm = { etiqueta: '', comentario: '' };
  motivoRechazo = '';
  nuevaVersionEtiqueta = '';
  versionAId = '';
  versionBId = '';
  desembolsoSemanasInput = 4;
  desembolsoMatriz: number[][] = [];

  private presupuestoId = '';

  paramForm = {
    porcentajeAdministracion: 8,
    porcentajeImprevistos: 3,
    porcentajeUtilidad: 5,
    costoFijo: 0,
    precioVentaUnitario: null as number | null,
    costoVariableUnitario: null as number | null,
  };

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
        this.cargando.set(false);
        this.cargarVersiones();
        this.cargarDesembolsos();
      },
      error: (err) => {
        this.cargando.set(false);
        this.errorMsg.set(extraerErrorHttp(err));
      },
    });
  }

  estadoBadgeClass(estado: PresupuestoDetalle['estado']): string {
    if (estado === 'APROBADO') return 'badge-approved';
    if (estado === 'EN_REVISION') return 'badge-review';
    return 'badge-draft';
  }

  toggleCapitulo(capId: string): void {
    const cur = this.capitulosColapsados();
    this.capitulosColapsados.set({ ...cur, [capId]: !cur[capId] });
  }

  estaColapsado(capId: string): boolean {
    return !!this.capitulosColapsados()[capId];
  }

  abrirTrazabilidad(cap: { codigo: string; nombre: string }, item: ItemPresupuestoDetalle): void {
    this.trazabilidad.set({ capCodigo: cap.codigo, capNombre: cap.nombre, item });
    if (!this.previewPrecios() && this.authService.isPresupuestador()) {
      this.api.previsualizarActualizacionPrecios(this.presupuestoId).subscribe({
        next: (prev) => this.previewPrecios.set(prev),
        error: () => {},
      });
    }
  }

  cambioCatalogoItem(itemId: string) {
    return this.previewPrecios()?.cambios.find((c) => c.itemId === itemId) ?? null;
  }

  irAPreciosDesdeDrawer(): void {
    this.trazabilidad.set(null);
    this.activeTab.set('precios');
  }

  pesoItemPct(valorParcial: number): number {
    const cd = this.presupuesto()?.costoDirecto ?? 0;
    if (cd <= 0) return 0;
    return (valorParcial / cd) * 100;
  }

  barWidthPct(pct: number): number {
    return Math.max(4, Math.min(100, (pct / this.maxParticipacionCriticos()) * 100));
  }

  filaPctSuma(ci: number): number {
    const row = this.desembolsoMatriz[ci] ?? [];
    const total = row.reduce((acc, v) => acc + (Number(v) || 0), 0);
    return Math.round(total * 100) / 100;
  }

  seleccionarPrecios(): void {
    this.activeTab.set('precios');
    if (this.authService.isPresupuestador() && !this.previewPrecios()) {
      this.previsualizarPrecios();
    }
  }

  semanasIndices(): number[] {
    const n = this.desembolsos()?.semanas ?? this.desembolsoSemanasInput;
    return Array.from({ length: n }, (_, i) => i);
  }

  exportarCsv(): void {
    const p = this.presupuesto();
    if (!p) return;
    this.errorMsg.set(null);
    this.api.exportarCsv(p.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `presupuesto-${p.codigo}.csv`;
        link.click();
        URL.revokeObjectURL(url);
        this.successMsg.set(`Archivo presupuesto-${p.codigo}.csv descargado.`);
      },
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  enviarARevision(): void {
    this.errorMsg.set(null);
    this.successMsg.set(null);
    this.api.enviarARevision(this.presupuestoId, this.envioForm).subscribe({
      next: (detalle) => {
        this.mostrarEnvioRevision.set(false);
        this.envioForm = { etiqueta: '', comentario: '' };
        this.aplicarDetalle(detalle);
        this.cargarVersiones();
        this.successMsg.set('Presupuesto enviado a revisión con versión congelada.');
      },
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  rechazar(): void {
    if (!this.motivoRechazo.trim()) {
      this.errorMsg.set('El motivo del rechazo es obligatorio.');
      return;
    }
    this.errorMsg.set(null);
    this.successMsg.set(null);
    this.api.rechazar(this.presupuestoId, this.motivoRechazo.trim()).subscribe({
      next: (detalle) => {
        this.mostrarRechazo.set(false);
        this.motivoRechazo = '';
        this.aplicarDetalle(detalle);
        this.successMsg.set('Presupuesto devuelto a BORRADOR con el motivo registrado.');
      },
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  cargarVersiones(): void {
    this.api.listarVersiones(this.presupuestoId).subscribe({
      next: (lista) => this.versiones.set(lista),
    });
  }

  guardarVersionManual(): void {
    if (!this.nuevaVersionEtiqueta.trim()) return;
    this.errorMsg.set(null);
    this.api.crearVersion(this.presupuestoId, { etiqueta: this.nuevaVersionEtiqueta.trim() }).subscribe({
      next: () => {
        this.nuevaVersionEtiqueta = '';
        this.cargarVersiones();
        this.successMsg.set('Snapshot de versión guardado.');
      },
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  verSnapshotVersion(versionId: string): void {
    this.errorMsg.set(null);
    this.api.obtenerVersion(this.presupuestoId, versionId).subscribe({
      next: (det) => this.snapshotSeleccionado.set(det),
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  compararVersiones(): void {
    if (!this.versionAId || !this.versionBId) return;
    this.errorMsg.set(null);
    this.api.compararVersiones(this.presupuestoId, this.versionAId, this.versionBId).subscribe({
      next: (res) => this.comparacionVersiones.set(res),
      error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
    });
  }

  cargarDesembolsos(): void {
    this.api.obtenerDesembolsos(this.presupuestoId).subscribe({
      next: (res) => this.aplicarDesembolsos(res),
    });
  }

  repartirEquitativoDesembolsos(): void {
    const des = this.desembolsos();
    const semanas = Math.max(1, Math.min(52, Number(this.desembolsoSemanasInput) || 4));
    this.desembolsoSemanasInput = semanas;
    if (!des) return;
    const base = Math.floor(10000 / semanas) / 100;
    const ultimo = Math.round((100 - base * (semanas - 1)) * 100) / 100;
    const filaBase = Array.from({ length: semanas }, (_, idx) => (idx === semanas - 1 ? ultimo : base));
    this.desembolsoMatriz = des.capitulos.map(() => [...filaBase]);
    this.desembolsos.set({
      ...des,
      semanas,
      capitulos: des.capitulos.map((c, idx) => ({
        ...c,
        porcentajesPorSemana: [...this.desembolsoMatriz[idx]],
        montosPorSemana: Array(semanas).fill(0),
      })),
      totalesPorSemana: Array(semanas).fill(0),
      acumuladosPorSemana: Array(semanas).fill(0),
    });
  }

  guardarDesembolsos(): void {
    const des = this.desembolsos();
    if (!des) return;
    this.errorMsg.set(null);
    this.successMsg.set(null);
    this.api
      .guardarDesembolsos(this.presupuestoId, {
        semanas: this.desembolsoSemanasInput,
        capitulos: des.capitulos.map((cap, idx) => ({
          capituloId: cap.capituloId,
          porcentajesPorSemana: (this.desembolsoMatriz[idx] ?? []).map((n) => Number(n) || 0),
        })),
      })
      .subscribe({
        next: (res) => {
          this.aplicarDesembolsos(res);
          this.successMsg.set('Cronograma de desembolsos calculado y guardado (cuadre $0.00).');
        },
        error: (err) => this.errorMsg.set(extraerErrorHttp(err)),
      });
  }

  private aplicarDesembolsos(res: DesembolsosResponse): void {
    this.desembolsos.set(res);
    this.desembolsoSemanasInput = res.semanas;
    this.desembolsoMatriz = res.capitulos.map((c) => c.porcentajesPorSemana.map((v) => Number(v)));
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
        this.mostrarNuevoCapModal.set(false);
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
