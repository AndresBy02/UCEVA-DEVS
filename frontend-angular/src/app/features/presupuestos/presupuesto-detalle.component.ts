import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
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
import { descargarCsvPresupuesto } from '../../core/utils/presupuesto-csv.util';
import { extraerErrorHttp } from '../../core/utils/http-error.util';

@Component({
  selector: 'app-presupuesto-detalle',
  standalone: true,
  imports: [FormsModule, DecimalPipe, RouterLink],
  template: `
    <section class="page-section">
      <div class="header-row">
        <a routerLink="/presupuestos" class="btn-secondary">Volver al listado</a>
        @if (presupuesto(); as p) {
          <div class="header-meta">
            <h2>{{ p.codigo }} · {{ p.nombre }}</h2>
            <span [class]="estadoBadgeClass(p.estado)">
              {{ p.estado }}
            </span>
          </div>
          <div class="actions-row">
            <button type="button" class="btn-secondary" (click)="exportarCsv()">Exportar CSV</button>
            <a [routerLink]="['/evaluacion-financiera']"
               [queryParams]="{ inversion: p.precioVenta, codigo: p.codigo }"
               class="btn-secondary">
              Evaluar VPN / TIR
            </a>
            @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
              <button type="button" class="btn-primary" (click)="mostrarEnvioRevision.set(!mostrarEnvioRevision())">
                Enviar a Revisión
              </button>
            }
            @if (authService.isRevisor() && p.estado !== 'APROBADO') {
              <button type="button" class="btn-primary" (click)="aprobar()">Aprobar Presupuesto</button>
            }
            @if (authService.isRevisor() && p.estado === 'EN_REVISION') {
              <button type="button" class="btn-secondary" (click)="mostrarRechazo.set(!mostrarRechazo())">
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
            <strong>Presupuesto devuelto por el revisor (<code>{{ p.rechazadoPor || '-' }}</code>):</strong>
            {{ p.motivoRechazo }}
          </div>
        }
        @if (p.estado === 'EN_REVISION') {
          <div class="alert-warning">
            Este presupuesto se encuentra <strong>EN_REVISION</strong> (enviado por <code>{{ p.enviadoRevisionPor || '-' }}</code>).
            La edición está bloqueada hasta que el revisor lo apruebe o lo devuelva con motivo.
          </div>
        }

        @if (mostrarEnvioRevision() && authService.isPresupuestador() && p.estado === 'BORRADOR') {
          <div class="card">
            <h3>Enviar Presupuesto a Revisión (Congelar Versión Inmutable)</h3>
            <form (ngSubmit)="enviarARevision()" class="grid-form">
              <label>
                Etiqueta de versión
                <input [(ngModel)]="envioForm.etiqueta" name="envioEtiqueta" placeholder="Ej. Entrega v1 para revisión" />
              </label>
              <label>
                Comentario para el revisor
                <input [(ngModel)]="envioForm.comentario" name="envioComentario" placeholder="Notas o cambios realizados" />
              </label>
              <div class="actions-row">
                <button type="submit" class="btn-primary">Confirmar Envío a Revisión</button>
                <button type="button" class="btn-secondary" (click)="mostrarEnvioRevision.set(false)">Cancelar</button>
              </div>
            </form>
          </div>
        }

        @if (mostrarRechazo() && authService.isRevisor() && p.estado === 'EN_REVISION') {
          <div class="card">
            <h3>Rechazar Presupuesto y Devolver a Borrador</h3>
            <form (ngSubmit)="rechazar()" class="grid-form">
              <label style="grid-column: 1 / -1;">
                Motivo del rechazo (obligatorio)
                <input [(ngModel)]="motivoRechazo" name="motivoRechazo" required
                       placeholder="Describe qué debe ajustarse en este presupuesto..." />
              </label>
              <div class="actions-row">
                <button type="submit" class="btn-primary">Confirmar Rechazo</button>
                <button type="button" class="btn-secondary" (click)="mostrarRechazo.set(false)">Cancelar</button>
              </div>
            </form>
          </div>
        }

        <div class="card">
          <div class="meta-grid">
            <div><strong>Cliente:</strong> {{ p.cliente }}</div>
            <div><strong>Creado por (sub):</strong> <code>{{ p.creadoPor }}</code></div>
            <div><strong>Enviado a revisión por:</strong> <code>{{ p.enviadoRevisionPor || '-' }}</code></div>
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
          <h3>Parámetros AIU y Punto de Equilibrio (Recálculo en Servidor)</h3>
          <form (ngSubmit)="guardarParametros()" class="grid-form">
            <label>
              % Administración (A)
              <input type="number" step="0.01" [(ngModel)]="paramForm.porcentajeAdministracion" name="porcA"
                     [disabled]="p.estado !== 'BORRADOR' || !authService.isPresupuestador()" />
            </label>
            <label>
              % Imprevistos (I)
              <input type="number" step="0.01" [(ngModel)]="paramForm.porcentajeImprevistos" name="porcI"
                     [disabled]="p.estado !== 'BORRADOR' || !authService.isPresupuestador()" />
            </label>
            <label>
              % Utilidad (U)
              <input type="number" step="0.01" [(ngModel)]="paramForm.porcentajeUtilidad" name="porcU"
                     [disabled]="p.estado !== 'BORRADOR' || !authService.isPresupuestador()" />
            </label>
            <label>
              Costo Fijo (COP)
              <input type="number" step="0.01" [(ngModel)]="paramForm.costoFijo" name="costoFijo"
                     [disabled]="p.estado !== 'BORRADOR' || !authService.isPresupuestador()" />
            </label>
            <label>
              Precio Venta Unitario (PE)
              <input type="number" step="0.01" [(ngModel)]="paramForm.precioVentaUnitario" name="pvu"
                     [disabled]="p.estado !== 'BORRADOR' || !authService.isPresupuestador()" />
            </label>
            <label>
              Costo Variable Unitario (PE)
              <input type="number" step="0.01" [(ngModel)]="paramForm.costoVariableUnitario" name="cvu"
                     [disabled]="p.estado !== 'BORRADOR' || !authService.isPresupuestador()" />
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
                      [disabled]="p.estado !== 'BORRADOR'">
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

        <div class="card">
          <div class="header-row">
            <h3>Versiones Inmutables y Comparador (RF-10 · UC-38 / UC-39)</h3>
            @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
              <form (ngSubmit)="guardarVersionManual()" class="inline-form">
                <input [(ngModel)]="nuevaVersionEtiqueta" name="verEtiqueta" placeholder="Etiqueta (ej. Cotización Cliente v1)" required />
                <button type="submit" class="btn-secondary">Guardar Snapshot</button>
              </form>
            }
          </div>

          <table>
            <thead>
              <tr>
                <th>Versión</th>
                <th>Etiqueta</th>
                <th>Estado</th>
                <th>Creado Por</th>
                <th>Fecha</th>
                <th class="num">Costo Directo</th>
                <th class="num">Precio Venta</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              @for (v of versiones(); track v.id) {
                <tr>
                  <td><strong>v{{ v.numeroVersion }}</strong></td>
                  <td>
                    {{ v.etiqueta }}
                    @if (v.comentarioEnvio) {
                      <div><small style="color: var(--muted);">{{ v.comentarioEnvio }}</small></div>
                    }
                  </td>
                  <td>{{ v.estadoEnMomento }}</td>
                  <td><small>{{ v.creadoPor }}</small></td>
                  <td><small>{{ v.fechaCreacion }}</small></td>
                  <td class="num">{{ v.costoDirecto | number: '1.2-2' }}</td>
                  <td class="num"><strong>{{ v.precioVenta | number: '1.2-2' }}</strong></td>
                  <td>
                    <button type="button" class="btn-secondary" (click)="verSnapshotVersion(v.id)">Ver Snapshot</button>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="8" style="color: var(--muted);">
                    No hay versiones congeladas todavía. Se crea una automáticamente al enviar a revisión o al guardar un snapshot.
                  </td>
                </tr>
              }
            </tbody>
          </table>

          @if (versiones().length >= 2) {
            <div class="chapter-block" style="margin-top: 14px;">
              <h4>Comparar Dos Versiones (UC-39)</h4>
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
                <div class="summary-grid" style="margin-top: 12px;">
                  <div class="stat-box">
                    <span>Diferencia Costo Directo (B - A)</span>
                    <strong>{{ comp.diferenciaCostoDirecto | number: '1.2-2' }} COP</strong>
                  </div>
                  <div class="stat-box highlight">
                    <span>Diferencia Precio Venta (B - A)</span>
                    <strong>{{ comp.diferenciaPrecioVenta | number: '1.2-2' }} COP</strong>
                  </div>
                  <div class="stat-box">
                    <span>Resumen Cambios</span>
                    <strong>+{{ comp.itemsAgregados.length }} / -{{ comp.itemsEliminados.length }} / ~{{ comp.itemsModificados.length }}</strong>
                    <small>Agregados / Eliminados / Modificados</small>
                  </div>
                </div>

                @if (comp.itemsAgregados.length > 0) {
                  <h4 style="margin-top: 12px;">Ítems Agregados</h4>
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
                  <h4 style="margin-top: 12px;">Ítems Eliminados</h4>
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
                  <h4 style="margin-top: 12px;">Ítems Modificados (Cantidad o Precio)</h4>
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
            </div>
          }

          @if (snapshotSeleccionado(); as snap) {
            <div class="chapter-block" style="margin-top: 14px;">
              <div class="header-row">
                <h4>Snapshot v{{ snap.numeroVersion }} · {{ snap.etiqueta }}</h4>
                <button type="button" class="btn-secondary" (click)="snapshotSeleccionado.set(null)">Cerrar Snapshot</button>
              </div>
              <p>
                Costo Directo: <strong>{{ snap.costoDirecto | number: '1.2-2' }}</strong> ·
                AIU: {{ snap.valorAdministracion | number: '1.2-2' }} / {{ snap.valorImprevistos | number: '1.2-2' }} / {{ snap.valorUtilidad | number: '1.2-2' }} ·
                Precio Venta: <strong>{{ snap.precioVenta | number: '1.2-2' }} COP</strong>
              </p>
            </div>
          }
        </div>

        <div class="card">
          <div class="header-row">
            <h3>Desembolsos en el Tiempo por Capítulo (RF-12 · UC-42)</h3>
            @if (authService.isPresupuestador() && p.estado === 'BORRADOR' && p.capitulos.length > 0) {
              <div class="inline-form">
                <label style="display:flex; align-items:center; gap:6px;">
                  Semanas:
                  <input type="number" min="1" max="52" [(ngModel)]="desembolsoSemanasInput" name="desSemanas" style="width: 80px;" />
                </label>
                <button type="button" class="btn-secondary" (click)="repartirEquitativoDesembolsos()">Ajustar Semanas</button>
                <button type="button" class="btn-primary" (click)="guardarDesembolsos()">Guardar Desembolsos</button>
              </div>
            }
          </div>

          @if (desembolsos(); as des) {
            @if (des.capitulos.length === 0) {
              <p style="color: var(--muted); margin-top: 10px;">Agrega capítulos para programar los desembolsos semanales.</p>
            } @else {
              <div class="summary-grid" style="margin-top: 10px;">
                <div class="stat-box">
                  <span>Costo Directo Total</span>
                  <strong>{{ des.costoDirectoTotal | number: '1.2-2' }} COP</strong>
                </div>
                <div class="stat-box highlight">
                  <span>Suma Total Desembolsos</span>
                  <strong>{{ des.totalDesembolsado | number: '1.2-2' }} COP</strong>
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
                    <th class="num">Total Cap.</th>
                  </tr>
                </thead>
                <tbody>
                  @for (cap of des.capitulos; track cap.capituloId; let ci = $index) {
                    <tr>
                      <td>{{ cap.codigo }} - {{ cap.nombre }}</td>
                      <td class="num">{{ cap.subtotalCapitulo | number: '1.2-2' }}</td>
                      @for (idx of semanasIndices(); track idx) {
                        <td class="num">
                          @if (authService.isPresupuestador() && p.estado === 'BORRADOR') {
                            <input type="number" step="0.01" min="0" max="100"
                                   [(ngModel)]="desembolsoMatriz[ci][idx]"
                                   [name]="'des_' + ci + '_' + idx"
                                   style="width: 72px; text-align: right; margin-bottom: 4px;" />
                            <div><small>{{ cap.montosPorSemana[idx] | number: '1.2-2' }}</small></div>
                          } @else {
                            <div>{{ cap.porcentajesPorSemana[idx] | number: '1.2-2' }}%</div>
                            <div><small>{{ cap.montosPorSemana[idx] | number: '1.2-2' }}</small></div>
                          }
                        </td>
                      }
                      <td class="num"><strong>{{ cap.totalCapituloDesembolsado | number: '1.2-2' }}</strong></td>
                    </tr>
                  }
                  <tr class="total-row">
                    <td><strong>Desembolso por Semana</strong></td>
                    <td></td>
                    @for (tot of des.totalesPorSemana; track $index) {
                      <td class="num"><strong>{{ tot | number: '1.2-2' }}</strong></td>
                    }
                    <td class="num"><strong>{{ des.totalDesembolsado | number: '1.2-2' }}</strong></td>
                  </tr>
                  <tr>
                    <td><strong>Acumulado al Corte</strong></td>
                    <td></td>
                    @for (ac of des.acumuladosPorSemana; track $index) {
                      <td class="num">{{ ac | number: '1.2-2' }}</td>
                    }
                    <td></td>
                  </tr>
                </tbody>
              </table>
            }
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
  readonly editandoItem = signal<{ capituloId: string; item: ItemPresupuestoDetalle } | null>(null);
  readonly versiones = signal<VersionPresupuestoResumen[]>([]);
  readonly snapshotSeleccionado = signal<VersionPresupuestoDetalle | null>(null);
  readonly comparacionVersiones = signal<ComparacionVersiones | null>(null);
  readonly desembolsos = signal<DesembolsosResponse | null>(null);
  readonly mostrarEnvioRevision = signal<boolean>(false);
  readonly mostrarRechazo = signal<boolean>(false);
  readonly cargando = signal<boolean>(false);
  readonly errorMsg = signal<string | null>(null);
  readonly successMsg = signal<string | null>(null);

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
      error: () => {
        // Respaldo (UC-37): si el endpoint del servidor no responde, se genera el CSV en el cliente
        // con los valores ya calculados por el backend (sin recalcular montos en TypeScript).
        descargarCsvPresupuesto(p);
        this.successMsg.set('Presupuesto exportado a CSV (generado en el navegador).');
      },
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
    const base = Math.floor((10000 / semanas)) / 100;
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
