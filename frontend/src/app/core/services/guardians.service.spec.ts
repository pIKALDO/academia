import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { GuardiansService } from './guardians.service';

describe('GuardiansService', () => {
  let service: GuardiansService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(GuardiansService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('link() hace PUT sobre la pareja (studentId, guardianId), no POST', () => {
    service
      .link('s1', 'g1', { relationship: 'MOTHER', isPrimary: true, hasAccess: true })
      .subscribe();

    const req = http.expectOne('/api/v1/students/s1/guardians/g1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      relationship: 'MOTHER',
      isPrimary: true,
      hasAccess: true,
    });
    req.flush({
      studentId: 's1',
      guardianId: 'g1',
      relationship: 'MOTHER',
      isPrimary: true,
      hasAccess: true,
      createdAt: '2026-01-01T00:00:00Z',
    });
  });

  it('unlink() hace DELETE sobre la misma pareja de identificadores', () => {
    service.unlink('s1', 'g1').subscribe();
    const req = http.expectOne('/api/v1/students/s1/guardians/g1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('list() pagina con los valores por defecto (0, 50)', () => {
    service.list().subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/guardians');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('50');
    req.flush({ content: [], page: { number: 0, size: 50, totalElements: 0, totalPages: 0 } });
  });
});
