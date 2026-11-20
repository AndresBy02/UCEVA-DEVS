import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { VersionPresupuesto } from '../models/api.models';
import { PresupuestoApiService } from './presupuesto-api.service';

const BASE_URL = 'http://localhost:8082/api/v1/presupuestos';

const version: VersionPresupuesto = {
  id: 'version-1',
  presupuestoId: 'presupuesto-1',
  numero: 1,
  codigoPresupuesto: 'REV-001',
  nombrePresupuesto: 'Edificio UCEVA',
  estado: 'ENVIADA',
  creadoPor: 'sub-ana-01',
  fechaCreacion: '2026-11-19T20:00:00Z',
  destinatarioSub: 'sub-carlos-02',
  fechaNotificacion: '2026-11-19T20:00:00Z',
  mensaje: 'Revisar antes de enviar',
  snapshotJson: '{"codigo":"REV-001"}',
};

describe('PresupuestoApiService - versiones (UC-38)', () => {
  let service: PresupuestoApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PresupuestoApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('envía a revisión mediante POST con destinatario y mensaje', () => {
    service
      .enviarARevision('presupuesto-1', {
        destinatarioSub: 'sub-carlos-02',
        mensaje: 'Revisar antes de enviar',
      })
      .subscribe((respuesta) => expect(respuesta).toEqual(version));

    const req = httpMock.expectOne(`${BASE_URL}/presupuesto-1/enviar-revision`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      destinatarioSub: 'sub-carlos-02',
      mensaje: 'Revisar antes de enviar',
    });
    req.flush(version);
  });

  it('permite enviar a revisión sinBody explícito', () => {
    service.enviarARevision('presupuesto-1').subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/presupuesto-1/enviar-revision`);
    expect(req.request.body).toEqual({});
    req.flush(version);
  });

  it('lista las versiones congeladas mediante GET', () => {
    service.listarVersiones('presupuesto-1').subscribe((respuesta) => {
      expect(respuesta.length).toBe(1);
      expect(respuesta[0].numero).toBe(1);
      expect(respuesta[0].estado).toBe('ENVIADA');
    });

    const req = httpMock.expectOne(`${BASE_URL}/presupuesto-1/versiones`);
    expect(req.request.method).toBe('GET');
    req.flush([version]);
  });
});
