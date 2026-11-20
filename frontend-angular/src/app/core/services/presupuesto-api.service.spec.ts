import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EnviarRevisionResponse, VersionPresupuestoResumen } from '../models/api.models';
import { PresupuestoApiService } from './presupuesto-api.service';

const BASE_URL = 'http://localhost:8082/api/v1/presupuestos';

const version: VersionPresupuestoResumen = {
  id: 'version-1',
  presupuestoId: 'presupuesto-1',
  numeroVersion: 1,
  etiqueta: 'Entrega v1',
  comentarioEnvio: 'Revisar antes de enviar',
  estadoEnMomento: 'EN_REVISION',
  creadoPor: 'sub-ana-01',
  fechaCreacion: '2026-11-19T20:00:00Z',
  costoDirecto: 100,
  valorAdministracion: 8,
  valorImprevistos: 3,
  valorUtilidad: 5,
  precioVenta: 116,
  codigoPresupuesto: 'REV-001',
  nombrePresupuesto: 'Edificio UCEVA',
  destinatarioSub: 'sub-carlos-02',
  fechaNotificacion: '2026-11-19T20:00:00Z',
};

const respuestaEnvio = version as unknown as EnviarRevisionResponse;

describe('PresupuestoApiService - versiones (UC-38 / UC-39)', () => {
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

  it('envía a revisión mediante POST con destinatario y comentario', () => {
    service
      .enviarARevision('presupuesto-1', {
        etiqueta: 'Entrega v1',
        comentario: 'Revisar antes de enviar',
        destinatarioSub: 'sub-carlos-02',
      })
      .subscribe((respuesta) => expect(respuesta).toEqual(respuestaEnvio));

    const req = httpMock.expectOne(`${BASE_URL}/presupuesto-1/enviar-revision`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      etiqueta: 'Entrega v1',
      comentario: 'Revisar antes de enviar',
      destinatarioSub: 'sub-carlos-02',
    });
    req.flush(respuestaEnvio);
  });

  it('permite enviar a revisión sin body explícito', () => {
    service.enviarARevision('presupuesto-1').subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/presupuesto-1/enviar-revision`);
    expect(req.request.body).toEqual({});
    req.flush(respuestaEnvio);
  });

  it('lista las versiones congeladas mediante GET', () => {
    service.listarVersiones('presupuesto-1').subscribe((respuesta) => {
      expect(respuesta.length).toBe(1);
      expect(respuesta[0].numeroVersion).toBe(1);
      expect(respuesta[0].estadoEnMomento).toBe('EN_REVISION');
      expect(respuesta[0].destinatarioSub).toBe('sub-carlos-02');
    });

    const req = httpMock.expectOne(`${BASE_URL}/presupuesto-1/versiones`);
    expect(req.request.method).toBe('GET');
    req.flush([version]);
  });
});
