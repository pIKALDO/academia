import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-password-reset-request',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './password-reset-request.html',
  styleUrl: './password-reset-request.css',
})
export class PasswordResetRequest {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  protected readonly submitting = signal(false);
  protected readonly sent = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      return;
    }

    this.submitting.set(true);
    this.auth.requestPasswordReset(this.form.getRawValue().email).subscribe({
      // 202 siempre, exista o no el correo (docs/diseno-api.md sección 2.2): el mensaje
      // de éxito no depende de si la cuenta existe.
      next: () => {
        this.submitting.set(false);
        this.sent.set(true);
      },
      error: () => {
        this.submitting.set(false);
      },
    });
  }
}
