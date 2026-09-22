import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { StudentsService } from './students.service';

describe('StudentsService', () => {
  let service: StudentsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(StudentsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('list() manda page/size por defecto y omite status/q cuando están vacíos', () => {
    service.list().subscribe();
    const req = http.expectOne(
      (r) => r.url === '/api/v1/students' && r.method === 'GET',
    );
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.has('status')).toBe(false);
    expect(req.request.params.has('q')).toBe(false);
    req.flush({ content: [], page: { number: 0, size: 20, totalElements: 0, totalPages: 0 } });
  });

  it('list() manda status y q cuando se indican, y no cuando status es cadena vacía', () => {
    service.list({ status: 'ACTIVE', q: 'kova', page: 1, size: 10 }).subscribe();
    const req = http.expectOne('/api/v1/students?status=ACTIVE&q=kova&page=1&size=10');
    req.flush({ content: [], page: { number: 1, size: 10, totalElements: 0, totalPages: 1 } });

    service.list({ status: '', page: 0 }).subscribe();
    const req2 = http.expectOne((r) => r.url === '/api/v1/students');
    expect(req2.request.params.has('status')).toBe(false);
    req2.flush({ content: [], page: { number: 0, size: 20, totalElements: 0, totalPages: 0 } });
  });

  it('replaceHousing() hace PUT a /students/{id}/housing', () => {
    service.replaceHousing('s1', { addressLine: 'Calle Falsa 123' }).subscribe();
    const req = http.expectOne('/api/v1/students/s1/housing');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ addressLine: 'Calle Falsa 123' });
    req.flush({
      addressLine: 'Calle Falsa 123',
      city: null,
      notes: null,
      responsibleName: null,
      responsiblePhone: null,
      updatedAt: '2026-01-01T00:00:00Z',
    });
  });

  it('delete() hace DELETE a /students/{id}', () => {
    service.delete('s1').subscribe();
    const req = http.expectOne('/api/v1/students/s1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
