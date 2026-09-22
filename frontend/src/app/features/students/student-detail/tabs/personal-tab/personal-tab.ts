import { Component, EventEmitter, Output, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { StatusBadge } from '../../../../../shared/status-badge/status-badge';
import { StudentsService } from '../../../../../core/services/students.service';
import {
  isAdminStudent,
  StudentDetailDto,
  UpdateStudentRequest,
} from '../../../../../core/models/student.model';

@Component({
  selector: 'app-student-personal-tab',
  imports: [ReactiveFormsModule, StatusBadge, DatePipe],
  templateUrl: './personal-tab.html',
  styleUrl: './personal-tab.css',
})
export class PersonalTab {
  private readonly fb = inject(FormBuilder);
  private readonly studentsService = inject(StudentsService);

  readonly student = input.required<StudentDetailDto>();
  readonly isAdmin = input.required<boolean>();
  @Output() readonly saved = new EventEmitter<void>();

  protected readonly editing = signal(false);
  protected readonly submitting = signal(false);

  /** Solo la vista de administrador trae dirección, fecha de alta y marcas de auditoría. */
  protected readonly adminStudent = computed(() => {
    const s = this.student();
    return isAdminStudent(s) ? s : null;
  });

  protected readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(150)]],
    birthDate: [''],
    nationality: ['', [Validators.pattern('[A-Z]{2}')]],
    status: ['PROSPECT' as UpdateStudentRequest['status']],
    enrolledAt: [''],
    phone: ['', [Validators.maxLength(30)]],
    email: ['', [Validators.email, Validators.maxLength(255)]],
    addressLine: ['', [Validators.maxLength(255)]],
    city: ['', [Validators.maxLength(100)]],
    postalCode: ['', [Validators.maxLength(20)]],
    country: ['', [Validators.pattern('[A-Z]{2}')]],
  });

  startEdit(): void {
    const s = this.student();
    const admin = this.adminStudent();
    this.form.reset({
      firstName: s.firstName,
      lastName: s.lastName,
      birthDate: s.birthDate ?? '',
      nationality: s.nationality ?? '',
      status: s.status,
      enrolledAt: admin?.enrolledAt ?? '',
      phone: s.contact.phone ?? '',
      email: s.contact.email ?? '',
      addressLine: admin?.contact.address.line ?? '',
      city: admin?.contact.address.city ?? '',
      postalCode: admin?.contact.address.postalCode ?? '',
      country: admin?.contact.address.country ?? '',
    });
    this.editing.set(true);
  }

  cancel(): void {
    this.editing.set(false);
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    const raw = this.form.getRawValue();
    const body: UpdateStudentRequest = {
      firstName: raw.firstName,
      lastName: raw.lastName,
      birthDate: raw.birthDate || undefined,
      nationality: raw.nationality || undefined,
      status: raw.status,
      enrolledAt: raw.enrolledAt || undefined,
      phone: raw.phone || undefined,
      email: raw.email || undefined,
      addressLine: raw.addressLine || undefined,
      city: raw.city || undefined,
      postalCode: raw.postalCode || undefined,
      country: raw.country || undefined,
    };

    this.studentsService.update(this.student().id, body).subscribe({
      next: () => {
        this.submitting.set(false);
        this.editing.set(false);
        this.saved.emit();
      },
      error: () => this.submitting.set(false),
    });
  }
}
