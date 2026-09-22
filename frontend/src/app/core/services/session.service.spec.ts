import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SessionService } from './session.service';

/**
 * Regresión: `login()` encadenaba `loadProfile()` con `tap(() => ...subscribe())`, una
 * suscripción aparte que el observable devuelto no esperaba. `Login.submit()` navegaba a `/`
 * en cuanto `POST /auth/login` respondía, con `currentUser()` todavía `null` — el guard
 * rebotaba a `/login` y hacía falta un segundo clic para que el `loadProfile()` del primer
 * intento ya hubiera terminado en segundo plano. Ahora usa `switchMap`: el observable de
 * `login()` no completa hasta que el perfil está cargado.
 */
describe('SessionService.login', () => {
  let service: SessionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SessionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('no emite hasta que el perfil se ha cargado, y deja currentUser() poblado', () => {
    let emitted = false;

    service.login('admin@academia.local', 'admin-dev-1234').subscribe(() => {
      emitted = true;
    });

    const loginReq = http.expectOne('/api/v1/auth/login');
    loginReq.flush(null);

    // El POST de login ya respondió, pero el perfil todavía no: no debe haber emitido.
    expect(emitted).toBe(false);
    expect(service.isAuthenticated()).toBe(false);

    const meReq = http.expectOne('/api/v1/auth/me');
    meReq.flush({
      id: '018f0000-0000-7000-8000-000000000000',
      displayName: 'Admin',
      email: 'admin@academia.local',
      role: 'ADMIN',
      status: 'ACTIVE',
      lastLoginAt: null,
    });

    expect(emitted).toBe(true);
    expect(service.isAuthenticated()).toBe(true);
  });
});
