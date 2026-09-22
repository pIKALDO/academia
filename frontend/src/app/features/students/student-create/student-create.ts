import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { StudentsService } from '../../../core/services/students.service';
import { CreateStudentRequest } from '../../../core/models/student.model';

@Component({
  selector: 'app-student-create',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './student-create.html',
  styleUrl: './student-create.css',
})
export class StudentCreate {
  private readonly fb = inject(FormBuilder);
  private readonly studentsService = inject(StudentsService);
  private readonly router = inject(Router);

  protected readonly submitting = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(150)]],
    birthDate: [''],
    nationality: ['', [Validators.pattern('[A-Z]{2}')]],
    status: ['PROSPECT' as CreateStudentRequest['status']],
    enrolledAt: [''],
    phone: ['', [Validators.maxLength(30)]],
    email: ['', [Validators.email, Validators.maxLength(255)]],
    addressLine: ['', [Validators.maxLength(255)]],
    city: ['', [Validators.maxLength(100)]],
    postalCode: ['', [Validators.maxLength(20)]],
    country: ['', [Validators.pattern('[A-Z]{2}')]],
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    const raw = this.form.getRawValue();
    const body: CreateStudentRequest = {
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

    this.studentsService.create(body).subscribe({
      next: (student) => {
        this.submitting.set(false);
        this.router.navigate(['/students', student.id]);
      },
      error: () => this.submitting.set(false),
    });
  }
}
