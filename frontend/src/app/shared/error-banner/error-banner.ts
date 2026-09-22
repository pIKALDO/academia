import { Component, inject } from '@angular/core';
import { NotificationService } from '../../core/services/notification.service';

@Component({
  selector: 'app-error-banner',
  imports: [],
  templateUrl: './error-banner.html',
  styleUrl: './error-banner.css',
})
export class ErrorBanner {
  protected readonly notifications = inject(NotificationService);

  dismiss(id: number): void {
    this.notifications.dismiss(id);
  }
}
