import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { components } from '../api/schema';

type PasswordResetRequest = components['schemas']['PasswordResetRequest'];
type PasswordResetConfirmRequest = components['schemas']['PasswordResetConfirmRequest'];
type ActivateAccountRequest = components['schemas']['ActivateAccountRequest'];

const API_BASE = '/api/v1/auth';

@Injectable({ providedIn: 'root' })
export class AuthService {
  constructor(private readonly http: HttpClient) {}

  requestPasswordReset(email: string): Observable<void> {
    const body: PasswordResetRequest = { email };
    return this.http.post<void>(`${API_BASE}/password-reset`, body);
  }

  confirmPasswordReset(token: string, newPassword: string): Observable<void> {
    const body: PasswordResetConfirmRequest = { token, newPassword };
    return this.http.post<void>(`${API_BASE}/password-reset/confirm`, body);
  }

  activate(token: string, newPassword: string): Observable<void> {
    const body: ActivateAccountRequest = { token, newPassword };
    return this.http.post<void>(`${API_BASE}/activate`, body);
  }
}
