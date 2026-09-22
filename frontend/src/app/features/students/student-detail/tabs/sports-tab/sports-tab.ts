import { Component, EventEmitter, Output, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { StudentsService } from '../../../../../core/services/students.service';
import {
  isAdminStudent,
  SportsProfileRequest,
  StudentDetailDto,
} from '../../../../../core/models/student.model';

const HAND_LABELS: Record<string, string> = {
  RIGHT: 'Diestro',
  LEFT: 'Zurdo',
  AMBIDEXTROUS: 'Ambidiestro',
};

@Component({
  selector: 'app-student-sports-tab',
  imports: [ReactiveFormsModule],
  templateUrl: './sports-tab.html',
  styleUrl: './sports-tab.css',
})
export class SportsTab {
  private readonly fb = inject(FormBuilder);
  private readonly studentsService = inject(StudentsService);

  readonly student = input.required<StudentDetailDto>();
  readonly isAdmin = input.required<boolean>();
  @Output() readonly saved = new EventEmitter<void>();

  protected readonly editing = signal(false);
  protected readonly submitting = signal(false);

  protected readonly sportsProfile = computed(() => this.student().sportsProfile);
  /** `coachNotes`, `ranking` e `history` solo existen en la vista de administrador. */
  protected readonly coachDetails = computed(() => {
    const s = this.student();
    return isAdminStudent(s) ? s.sportsProfile : null;
  });

  protected readonly handLabel = (hand: string | null | undefined) =>
    hand ? (HAND_LABELS[hand] ?? hand) : '—';

  protected readonly form = this.fb.nonNullable.group({
    level: ['', [Validators.maxLength(50)]],
    dominantHand: ['' as SportsProfileRequest['dominantHand'] | ''],
    previousClub: ['', [Validators.maxLength(200)]],
    goals: [''],
    ranking: ['', [Validators.maxLength(50)]],
    history: [''],
    coachNotes: [''],
  });

  startEdit(): void {
    const profile = this.sportsProfile();
    const coach = this.coachDetails();
    this.form.reset({
      level: profile?.level ?? '',
      dominantHand: profile?.dominantHand ?? '',
      previousClub: profile?.previousClub ?? '',
      goals: profile?.goals ?? '',
      ranking: coach?.ranking ?? '',
      history: coach?.history ?? '',
      coachNotes: coach?.coachNotes ?? '',
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
    const body: SportsProfileRequest = {
      level: raw.level || undefined,
      dominantHand: raw.dominantHand || undefined,
      previousClub: raw.previousClub || undefined,
      goals: raw.goals || undefined,
      ranking: raw.ranking || undefined,
      history: raw.history || undefined,
      coachNotes: raw.coachNotes || undefined,
    };
    this.studentsService.replaceSportsProfile(this.student().id, body).subscribe({
      next: () => {
        this.submitting.set(false);
        this.editing.set(false);
        this.saved.emit();
      },
      error: () => this.submitting.set(false),
    });
  }
}
