import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PagedResponse } from '../models/paged-response.model';
import {
  CreateStudentRequest,
  EducationDto,
  EducationRequest,
  HousingDto,
  HousingRequest,
  SportsProfileDto,
  SportsProfileRequest,
  StudentAdminDto,
  StudentDetailDto,
  StudentListDto,
  StudentStatus,
  UpdateStudentRequest,
} from '../models/student.model';

const API_BASE = '/api/v1/students';

export interface StudentListFilters {
  status?: StudentStatus | '';
  q?: string;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class StudentsService {
  constructor(private readonly http: HttpClient) {}

  list(filters: StudentListFilters = {}): Observable<PagedResponse<StudentListDto>> {
    let params = new HttpParams();
    if (filters.status) {
      params = params.set('status', filters.status);
    }
    if (filters.q) {
      params = params.set('q', filters.q);
    }
    params = params.set('page', filters.page ?? 0).set('size', filters.size ?? 20);
    return this.http.get<PagedResponse<StudentListDto>>(API_BASE, {
      params,
      withCredentials: true,
    });
  }

  get(id: string): Observable<StudentDetailDto> {
    return this.http.get<StudentDetailDto>(`${API_BASE}/${id}`, { withCredentials: true });
  }

  create(body: CreateStudentRequest): Observable<StudentAdminDto> {
    return this.http.post<StudentAdminDto>(API_BASE, body, { withCredentials: true });
  }

  update(id: string, body: UpdateStudentRequest): Observable<StudentAdminDto> {
    return this.http.patch<StudentAdminDto>(`${API_BASE}/${id}`, body, { withCredentials: true });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/${id}`, { withCredentials: true });
  }

  replaceSportsProfile(id: string, body: SportsProfileRequest): Observable<SportsProfileDto> {
    return this.http.put<SportsProfileDto>(`${API_BASE}/${id}/sports-profile`, body, {
      withCredentials: true,
    });
  }

  replaceEducation(id: string, body: EducationRequest): Observable<EducationDto> {
    return this.http.put<EducationDto>(`${API_BASE}/${id}/education`, body, {
      withCredentials: true,
    });
  }

  replaceHousing(id: string, body: HousingRequest): Observable<HousingDto> {
    return this.http.put<HousingDto>(`${API_BASE}/${id}/housing`, body, {
      withCredentials: true,
    });
  }
}
