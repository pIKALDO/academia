import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PagedResponse } from '../models/paged-response.model';
import { UserSummary } from '../models/user.model';

const API_BASE = '/api/v1/users';

/** Solo lo necesario para el selector de "vincular a una cuenta" al dar de alta un tutor. */
@Injectable({ providedIn: 'root' })
export class UsersService {
  constructor(private readonly http: HttpClient) {}

  listByRole(role: UserSummary['role'], page = 0, size = 100): Observable<PagedResponse<UserSummary>> {
    const params = new HttpParams().set('role', role).set('page', page).set('size', size);
    return this.http.get<PagedResponse<UserSummary>>(API_BASE, { params, withCredentials: true });
  }
}
