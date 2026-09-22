import { Component, EventEmitter, Output, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { StudentsService } from '../../../../../core/services/students.service';
import {
  HousingRequest,
  isAdminStudent,
  StudentDetailDto,
} from '../../../../../core/models/student.model';

@Component({
  selector: 'app-student-housing-tab',
  imports: [ReactiveFormsModule],
  templateUrl: './housing-tab.html',
  styleUrl: './housing-tab.css',
})
export class HousingTab {
  private readonly fb = inject(FormBuilder);
  private readonly studentsService = inject(StudentsService);

  readonly student = input.required<StudentDetailDto>();
  readonly isAdmin = input.required<boolean>();
  @Output() readonly saved = new EventEmitter<void>();

  protected readonly editing = signal(false);
  protected readonly submitting = signal(false);

  /** El bloque `housing` nunca sale al portal de familias (regla no negociable nº 5). */
  protected readonly housing = computed(() => {
    const s = this.student();
    return isAdminStudent(s) ? s.housing : null;
  });

  protected readonly form = this.fb.nonNullable.group({
    addressLine: ['', [Validators.maxLength(255)]],
    city: ['', [Validators.maxLength(100)]],
    responsibleName: ['', [Validators.maxLength(200)]],
    responsiblePhone: ['', [Validators.maxLength(30)]],
    notes: [''],
  });

  startEdit(): void {
    const housing = this.housing();
    this.form.reset({
      addressLine: housing?.addressLine ?? '',
      city: housing?.city ?? '',
      responsibleName: housing?.responsibleName ?? '',
      responsiblePhone: housing?.responsiblePhone ?? '',
      notes: housing?.notes ?? '',
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
    const body: HousingRequest = {
      addressLine: raw.addressLine || undefined,
      city: raw.city || undefined,
      responsibleName: raw.responsibleName || undefined,
      responsiblePhone: raw.responsiblePhone || undefined,
      notes: raw.notes || undefined,
    };
    this.studentsService.replaceHousing(this.student().id, body).subscribe({
      next: () => {
        this.submitting.set(false);
        this.editing.set(false);
        this.saved.emit();
      },
      error: () => this.submitting.set(false),
    });
  }
}
