import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { AppComponent } from './app.component';
import { AuthService } from './core/auth/auth.service';
import { PresupuestoApiService } from './core/services/presupuesto-api.service';

describe('AppComponent', () => {
  const isAuthSignal = signal(false);

  beforeEach(async () => {
    isAuthSignal.set(false);
    const authStub = {
      isAuthenticated: isAuthSignal,
      username: signal<string>('presupuestador1'),
      userSub: signal<string>('sub-test-001'),
      roles: signal<string[]>(['presupuestador']),
      sessionNotice: signal<string | null>(null),
      login: () => {},
      logout: () => {},
    };
    const presupuestoApiStub = {
      listarEnRevision: () => of([]),
    };

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authStub },
        { provide: PresupuestoApiService, useValue: presupuestoApiStub },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render application brand title', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.brand-title')?.textContent).toContain('Presupuestador de Obra UCEVA');
  });

  it('should render Figma-aligned sidebar navigation links when authenticated', () => {
    isAuthSignal.set(true);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const navText = compiled.querySelector('.sidebar-nav')?.textContent ?? '';
    expect(navText).toContain('Inicio');
    expect(navText).toContain('Mis presupuestos');
    expect(navText).toContain('Catálogo de insumos');
    expect(navText).toContain('Escenarios A vs B');
    expect(navText).toContain('Evaluación VPN / TIR');
    expect(navText).toContain('En Revisión');
    expect(navText).toContain('Benchmark JNA');
  });
});
