import { Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-avatar',
  imports: [],
  templateUrl: './avatar.html',
  styleUrl: './avatar.css',
})
export class Avatar {
  readonly firstName = input.required<string>();
  readonly lastName = input.required<string>();
  readonly size = input<'sm' | 'md' | 'lg'>('md');

  protected readonly initials = computed(() => {
    const first = this.firstName().trim().charAt(0);
    const last = this.lastName().trim().charAt(0);
    return `${first}${last}`.toUpperCase();
  });
}
