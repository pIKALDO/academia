import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { GuardiansService } from '../../../core/services/guardians.service';
import {
  CreateGuardianRequest,
  GuardianDto,
  UpdateGuardianRequest,
} from '../../../core/models/guardian.model';

const PAGE_SIZE = 50;

@Component({
  selector: 'app-guardians-list',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './guardians-list.html',
  styleUrl: './guardians-list.css',
})
export class GuardiansList {
  private readonly fb = inject(FormBuilder);
  private readonly guardiansService = inject(GuardiansService);

  protected readonly guardians = signal<GuardianDto[]>([]);
  protected readonly loading = signal(true);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);

  protected readonly creating = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly submitting = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.email, Validators.maxLength(255)]],
    phone: ['', [Validators.maxLength(30)]],
    userId: [''],
  });

  constructor() {
    this.fetch(0);
  }

  goToPage(page: number): void {
    this.fetch(page);
  }

  openCreate(): void {
    this.editingId.set(null);
    this.form.reset({ firstName: '', lastName: '', email: '', phone: '', userId: '' });
    this.creating.set(true);
  }

  startEdit(guardian: GuardianDto): void {
    this.creating.set(false);
    this.form.reset({
      firstName: guardian.firstName,
      lastName: guardian.lastName,
      email: guardian.email ?? '',
      phone: guardian.phone ?? '',
      userId: guardian.userId ?? '',
    });
    this.editingId.set(guardian.id);
  }

  cancelForm(): void {
    this.creating.set(false);
    this.editingId.set(null);
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const raw = this.form.getRawValue();

    const editingId = this.editingId();
    if (editingId) {
      const body: UpdateGuardianRequest = {
        firstName: raw.firstName,
        lastName: raw.lastName,
        email: raw.email || undefined,
        phone: raw.phone || undefined,
        userId: raw.userId || undefined,
      };
      this.guardiansService.update(editingId, body).subscribe({
        next: () => {
          this.submitting.set(false);
          this.editingId.set(null);
          this.fetch(this.page());
        },
        error: () => this.submitting.set(false),
      });
      return;
    }

    const body: CreateGuardianRequest = {
      firstName: raw.firstName,
      lastName: raw.lastName,
      email: raw.email || undefined,
      phone: raw.phone || undefined,
      userId: raw.userId || undefined,
    };
    this.guardiansService.create(body).subscribe({
      next: () => {
        this.submitting.set(false);
        this.creating.set(false);
        this.fetch(0);
      },
      error: () => this.submitting.set(false),
    });
  }

  private fetch(page: number): void {
    this.loading.set(true);
    this.guardiansService.list(page, PAGE_SIZE).subscribe((response) => {
      this.loading.set(false);
      this.guardians.set(response.content);
      this.page.set(response.page.number);
      this.totalPages.set(response.page.totalPages);
      this.totalElements.set(response.page.totalElements);
    });
  }
}
