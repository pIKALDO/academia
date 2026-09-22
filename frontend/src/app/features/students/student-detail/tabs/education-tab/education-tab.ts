import { Component, EventEmitter, Output, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { StudentsService } from '../../../../../core/services/students.service';
import {
  EducationRequest,
  isAdminStudent,
  StudentDetailDto,
} from '../../../../../core/models/student.model';

@Component({
  selector: 'app-student-education-tab',
  imports: [ReactiveFormsModule],
  templateUrl: './education-tab.html',
  styleUrl: './education-tab.css',
})
export class EducationTab {
  private readonly fb = inject(FormBuilder);
  private readonly studentsService = inject(StudentsService);

  readonly student = input.required<StudentDetailDto>();
  readonly isAdmin = input.required<boolean>();
  @Output() readonly saved = new EventEmitter<void>();

  protected readonly editing = signal(false);
  protected readonly submitting = signal(false);

  protected readonly education = computed(() => this.student().education);
  /** `notes` y `scheduleNotes` solo existen en la vista de administrador. */
  protected readonly adminEducation = computed(() => {
    const s = this.student();
    return isAdminStudent(s) ? s.education : null;
  });

  protected readonly form = this.fb.nonNullable.group({
    schoolName: ['', [Validators.maxLength(200)]],
    grade: ['', [Validators.maxLength(100)]],
    scheduleNotes: [''],
    notes: [''],
  });

  startEdit(): void {
    const education = this.education();
    const admin = this.adminEducation();
    this.form.reset({
      schoolName: education?.schoolName ?? '',
      grade: education?.grade ?? '',
      scheduleNotes: admin?.scheduleNotes ?? '',
      notes: admin?.notes ?? '',
    });
    this.editing.set(true);
  }

  cancel(): void {
    this.editing.set(false);
  }

  submit(): void {
    if (this.submitting()) {
      return;
    }
    this.submitting.set(true);
    const raw = this.form.getRawValue();
    const body: EducationRequest = {
      schoolName: raw.schoolName || undefined,
      grade: raw.grade || undefined,
      scheduleNotes: raw.scheduleNotes || undefined,
      notes: raw.notes || undefined,
    };
    this.studentsService.replaceEducation(this.student().id, body).subscribe({
      next: () => {
        this.submitting.set(false);
        this.editing.set(false);
        this.saved.emit();
      },
      error: () => this.submitting.set(false),
    });
  }
}
