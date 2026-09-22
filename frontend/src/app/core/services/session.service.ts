import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Observable, switchMap, tap } from 'rxjs';
import { components } from '../api/schema';
import { UserProfile } from '../models/user.model';

type LoginRequest = components['schemas']['LoginRequest'];

const API_BASE = '/api/v1/auth';

@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly user = signal<UserProfile | null>(null);
  /** true mientras no se ha resuelto todavía la comprobación inicial de /auth/me. */
  private readonly resolved = signal(false);

  readonly currentUser = this.user.asReadonly();
  readonly isResolved = this.resolved.asReadonly();
  readonly isAuthenticated = computed(() => this.user() !== null);

  constructor(private readonly http: HttpClient) {}

  /**
   * Encadena `loadProfile()` (en vez de dispararlo como una suscripción aparte con `tap`):
   * si el observable devuelto completara antes de que el perfil esté cargado, un `next` que
   * navegara a una ruta protegida en ese instante encontraría `isAuthenticated()` todavía en
   * `false` y el guard rebotaría a `/login` — el síntoma era necesitar un segundo clic.
   */
  login(email: string, password: string): Observable<UserProfile> {
    const body: LoginRequest = { email, password };
    return this.http
      .post<void>(`${API_BASE}/login`, body, { withCredentials: true })
      .pipe(switchMap(() => this.loadProfile()));
  }

  logout(): Observable<void> {
    return this.http
      .post<void>(`${API_BASE}/logout`, {}, { withCredentials: true })
      .pipe(tap(() => this.user.set(null)));
  }

  /** Comprueba la sesión existente (recarga de página). Se llama una vez al arrancar la app. */
  loadProfile(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${API_BASE}/me`, { withCredentials: true }).pipe(
      tap({
        next: (profile) => {
          this.user.set(profile);
          this.resolved.set(true);
        },
        error: () => {
          this.user.set(null);
          this.resolved.set(true);
        },
      }),
    );
  }
}
