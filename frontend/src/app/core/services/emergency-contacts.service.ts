import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateEmergencyContactRequest,
  EmergencyContactAdminDto,
  UpdateEmergencyContactRequest,
} from '../models/emergency-contact.model';

@Injectable({ providedIn: 'root' })
export class EmergencyContactsService {
  constructor(private readonly http: HttpClient) {}

  create(
    studentId: string,
    body: CreateEmergencyContactRequest,
  ): Observable<EmergencyContactAdminDto> {
    return this.http.post<EmergencyContactAdminDto>(
      `/api/v1/students/${studentId}/emergency-contacts`,
      body,
      { withCredentials: true },
    );
  }

  update(id: string, body: UpdateEmergencyContactRequest): Observable<EmergencyContactAdminDto> {
    return this.http.patch<EmergencyContactAdminDto>(`/api/v1/emergency-contacts/${id}`, body, {
      withCredentials: true,
    });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/emergency-contacts/${id}`, { withCredentials: true });
  }
}
