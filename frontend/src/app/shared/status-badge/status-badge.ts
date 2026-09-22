import { Component, computed, input } from '@angular/core';
import { StudentStatus } from '../../core/models/student.model';

const LABELS: Record<StudentStatus, string> = {
  PROSPECT: 'Prospecto',
  ACTIVE: 'Activo',
  INACTIVE: 'Inactivo',
};

@Component({
  selector: 'app-status-badge',
  imports: [],
  templateUrl: './status-badge.html',
  styleUrl: './status-badge.css',
})
export class StatusBadge {
  readonly status = input.required<StudentStatus>();

  protected readonly label = computed(() => LABELS[this.status()] ?? this.status());
  protected readonly variant = computed(() => this.status().toLowerCase());
}
