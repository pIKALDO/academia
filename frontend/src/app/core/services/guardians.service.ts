import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateGuardianRequest,
  GuardianDto,
  StudentGuardianLinkDto,
  StudentGuardianLinkRequest,
  UpdateGuardianRequest,
} from '../models/guardian.model';
import { PagedResponse } from '../models/paged-response.model';

const API_BASE = '/api/v1/guardians';

@Injectable({ providedIn: 'root' })
export class GuardiansService {
  constructor(private readonly http: HttpClient) {}

  list(page = 0, size = 50): Observable<PagedResponse<GuardianDto>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedResponse<GuardianDto>>(API_BASE, { params, withCredentials: true });
  }

  get(id: string): Observable<GuardianDto> {
    return this.http.get<GuardianDto>(`${API_BASE}/${id}`, { withCredentials: true });
  }

  create(body: CreateGuardianRequest): Observable<GuardianDto> {
    return this.http.post<GuardianDto>(API_BASE, body, { withCredentials: true });
  }

  update(id: string, body: UpdateGuardianRequest): Observable<GuardianDto> {
    return this.http.patch<GuardianDto>(`${API_BASE}/${id}`, body, { withCredentials: true });
  }

  /** PUT idempotente sobre la pareja (studentId, guardianId): vincula o actualiza el vínculo. */
  link(
    studentId: string,
    guardianId: string,
    body: StudentGuardianLinkRequest,
  ): Observable<StudentGuardianLinkDto> {
    return this.http.put<StudentGuardianLinkDto>(
      `/api/v1/students/${studentId}/guardians/${guardianId}`,
      body,
      { withCredentials: true },
    );
  }

  unlink(studentId: string, guardianId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/students/${studentId}/guardians/${guardianId}`, {
      withCredentials: true,
    });
  }
}
