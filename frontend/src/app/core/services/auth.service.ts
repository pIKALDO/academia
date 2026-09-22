import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

const API_BASE = '/api/v1/auth';

@Injectable({ providedIn: 'root' })
export class AuthService {
  constructor(private readonly http: HttpClient) {}

  requestPasswordReset(email: string): Observable<void> {
    return this.http.post<void>(`${API_BASE}/password-reset`, { email });
  }

  confirmPasswordReset(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${API_BASE}/password-reset/confirm`, { token, newPassword });
  }

  activate(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${API_BASE}/activate`, { token, newPassword });
  }
}
