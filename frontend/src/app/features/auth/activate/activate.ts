import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-activate',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './activate.html',
  styleUrl: './activate.css',
})
export class Activate {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly token = this.route.snapshot.queryParamMap.get('token') ?? '';

  protected readonly submitting = signal(false);
  protected readonly done = signal(false);
  protected readonly tokenMissing = this.token === '';

  protected readonly form = this.fb.nonNullable.group({
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
  });

  submit(): void {
    if (this.form.invalid || this.submitting() || this.tokenMissing) {
      return;
    }

    this.submitting.set(true);
    this.auth.activate(this.token, this.form.getRawValue().newPassword).subscribe({
      next: () => {
        this.submitting.set(false);
        this.done.set(true);
        setTimeout(() => this.router.navigateByUrl('/login'), 2000);
      },
      error: () => {
        this.submitting.set(false);
      },
    });
  }
}
