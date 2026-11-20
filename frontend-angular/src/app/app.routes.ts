import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { CallbackComponent } from './features/auth/callback.component';
import { ComparadorEscenariosComponent } from './features/escenarios/comparador-escenarios.component';
import { BenchmarkJnaComponent } from './features/finanzas/benchmark-jna.component';
import { EvaluacionFinancieraComponent } from './features/finanzas/evaluacion-financiera.component';
import { InicioComponent } from './features/inicio/inicio.component';
import { InsumosComponent } from './features/insumos/insumos.component';
import { PresupuestoDetalleComponent } from './features/presupuestos/presupuesto-detalle.component';
import { PresupuestosListComponent } from './features/presupuestos/presupuestos-list.component';
import { RevisionQueueComponent } from './features/revision/revision-queue.component';

export const routes: Routes = [
  { path: 'auth/callback', component: CallbackComponent },
  { path: 'inicio', component: InicioComponent, canActivate: [authGuard] },
  { path: 'presupuestos', component: PresupuestosListComponent, canActivate: [authGuard] },
  { path: 'presupuestos/:id', component: PresupuestoDetalleComponent, canActivate: [authGuard] },
  { path: 'revision', component: RevisionQueueComponent, canActivate: [authGuard] },
  { path: 'escenarios/comparar', component: ComparadorEscenariosComponent, canActivate: [authGuard] },
  { path: 'insumos', component: InsumosComponent, canActivate: [authGuard] },
  { path: 'evaluacion-financiera', component: EvaluacionFinancieraComponent, canActivate: [authGuard] },
  { path: 'benchmark-jna', component: BenchmarkJnaComponent, canActivate: [authGuard] },
  { path: '', redirectTo: 'presupuestos', pathMatch: 'full' },
  { path: '**', redirectTo: 'presupuestos' },
];
