import { Injectable, signal } from '@angular/core';

export interface Notification {
  message: string;
  id: number;
}

let nextId = 0;

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly errorsSignal = signal<Notification[]>([]);
  readonly errors = this.errorsSignal.asReadonly();

  showError(message: string): void {
    const notification: Notification = { message, id: nextId++ };
    this.errorsSignal.update((list) => [...list, notification]);
  }

  dismiss(id: number): void {
    this.errorsSignal.update((list) => list.filter((n) => n.id !== id));
  }
}
