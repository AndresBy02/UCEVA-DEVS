import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { AppComponent } from './app.component';
import { AuthService } from './core/auth/auth.service';
import { PresupuestoApiService } from './core/services/presupuesto-api.service';

describe('AppComponent', () => {
  beforeEach(async () => {
    const authStub = {
      isAuthenticated: signal(false),
      username: signal<string | null>(null),
      userSub: signal<string | null>(null),
      roles: signal<string[]>([]),
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
});
