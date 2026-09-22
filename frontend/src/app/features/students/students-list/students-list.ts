import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, tap } from 'rxjs';
import { Avatar } from '../../../shared/avatar/avatar';
import { StatusBadge } from '../../../shared/status-badge/status-badge';
import { StudentListFilters, StudentsService } from '../../../core/services/students.service';
import { SessionService } from '../../../core/services/session.service';
import { StudentListDto, StudentStatus } from '../../../core/models/student.model';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-students-list',
  imports: [ReactiveFormsModule, RouterLink, Avatar, StatusBadge],
  templateUrl: './students-list.html',
  styleUrl: './students-list.css',
})
export class StudentsList {
  private readonly studentsService = inject(StudentsService);
  private readonly session = inject(SessionService);

  protected readonly isAdmin = () => this.session.currentUser()?.role === 'ADMIN';

  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly statusControl = new FormControl<StudentStatus | ''>('', {
    nonNullable: true,
  });

  protected readonly students = signal<StudentListDto[]>([]);
  protected readonly loading = signal(true);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);

  constructor() {
    this.searchControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe(() => this.fetch(0));

    this.statusControl.valueChanges.subscribe(() => this.fetch(0));

    this.fetch(0);
  }

  goToPage(page: number): void {
    this.fetch(page);
  }

  private fetch(page: number): void {
    this.loading.set(true);
    const filters: StudentListFilters = {
      q: this.searchControl.value.trim() || undefined,
      status: this.statusControl.value || undefined,
      page,
      size: PAGE_SIZE,
    };
    this.studentsService
      .list(filters)
      .pipe(tap(() => this.loading.set(false)))
      .subscribe((response) => {
        this.students.set(response.content);
        this.page.set(response.page.number);
        this.totalPages.set(response.page.totalPages);
        this.totalElements.set(response.page.totalElements);
      });
  }
}
