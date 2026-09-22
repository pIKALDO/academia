import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Avatar } from '../../../shared/avatar/avatar';
import { StatusBadge } from '../../../shared/status-badge/status-badge';
import { StudentsService } from '../../../core/services/students.service';
import { SessionService } from '../../../core/services/session.service';
import { isAdminStudent, StudentDetailDto } from '../../../core/models/student.model';
import { PersonalTab } from './tabs/personal-tab/personal-tab';
import { FamilyTab } from './tabs/family-tab/family-tab';
import { SportsTab } from './tabs/sports-tab/sports-tab';
import { EducationTab } from './tabs/education-tab/education-tab';
import { HousingTab } from './tabs/housing-tab/housing-tab';

type TabId = 'personal' | 'familia' | 'deportiva' | 'estudios' | 'alojamiento';

@Component({
  selector: 'app-student-detail',
  imports: [
    RouterLink,
    Avatar,
    StatusBadge,
    PersonalTab,
    FamilyTab,
    SportsTab,
    EducationTab,
    HousingTab,
  ],
  templateUrl: './student-detail.html',
  styleUrl: './student-detail.css',
})
export class StudentDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly studentsService = inject(StudentsService);
  private readonly session = inject(SessionService);

  protected readonly isAdmin = () => this.session.currentUser()?.role === 'ADMIN';
  protected readonly isAdminStudent = isAdminStudent;

  protected readonly student = signal<StudentDetailDto | null>(null);
  protected readonly loading = signal(true);
  protected readonly notFound = signal(false);
  protected readonly activeTab = signal<TabId>('personal');

  private readonly studentId = this.route.snapshot.paramMap.get('id')!;

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.notFound.set(false);
    this.studentsService.get(this.studentId).subscribe({
      next: (student) => {
        this.student.set(student);
        this.loading.set(false);
        if (this.activeTab() === 'alojamiento' && !isAdminStudent(student)) {
          this.activeTab.set('personal');
        }
      },
      error: (error) => {
        this.loading.set(false);
        if (error?.status === 404) {
          this.notFound.set(true);
        }
      },
    });
  }

  confirmDelete(): void {
    const s = this.student();
    if (!s) {
      return;
    }
    const confirmed = window.confirm(
      `¿Eliminar a ${s.firstName} ${s.lastName}? Esta acción no se puede deshacer desde la interfaz.`,
    );
    if (!confirmed) {
      return;
    }
    this.studentsService.delete(this.studentId).subscribe(() => {
      this.router.navigateByUrl('/');
    });
  }
}
