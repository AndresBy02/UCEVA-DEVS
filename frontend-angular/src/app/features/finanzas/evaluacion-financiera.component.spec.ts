import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { EvaluacionFinancieraComponent } from './evaluacion-financiera.component';
import { CalculoApiService } from '../../core/services/calculo-api.service';

describe('EvaluacionFinancieraComponent', () => {
  it('muestra alerta explicita "no convergió" y nunca 0% falso cuando la TIR no converge', async () => {
    const calculoStub = {
      evaluarProyecto: () =>
        of({
          motorUtilizado: 'NATIVE_C_LIBFINANZAS',
          vpn: -450.0,
          tir: {
            convergio: false,
            estado: 'no convergió',
            codigoRetornoC: -4,
            tasaInternaRetorno: null,
            tasaInternaRetornoPorcentaje: null,
            iteraciones: 50,
            mensaje: 'no convergió (alcanzado el límite de 50 iteraciones)',
          },
          amortizacion: null,
        }),
    };

    await TestBed.configureTestingModule({
      imports: [EvaluacionFinancieraComponent],
      providers: [provideRouter([]), { provide: CalculoApiService, useValue: calculoStub }],
    }).compileComponents();

    const fixture = TestBed.createComponent(EvaluacionFinancieraComponent);
    fixture.detectChanges();
    fixture.componentInstance.evaluar();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('no convergió');
    expect(text).toContain('nunca reporta 0%');
  });
});
