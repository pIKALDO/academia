import { Component, EventEmitter, Output, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { GuardiansService } from '../../../../../core/services/guardians.service';
import { EmergencyContactsService } from '../../../../../core/services/emergency-contacts.service';
import { UsersService } from '../../../../../core/services/users.service';
import {
  isAdminGuardianLink,
  isAdminStudent,
  StudentAdminLinkedGuardian,
  StudentDetailDto,
} from '../../../../../core/models/student.model';
import {
  CreateEmergencyContactRequest,
  isAdminEmergencyContact,
  UpdateEmergencyContactRequest,
} from '../../../../../core/models/emergency-contact.model';
import {
  CreateGuardianRequest,
  GuardianDto,
  GuardianRelationship,
  StudentGuardianLinkRequest,
} from '../../../../../core/models/guardian.model';
import { UserSummary } from '../../../../../core/models/user.model';

const RELATIONSHIP_LABELS: Record<GuardianRelationship, string> = {
  FATHER: 'Padre',
  MOTHER: 'Madre',
  LEGAL_GUARDIAN: 'Tutor legal',
  OTHER: 'Otro',
};

type AddGuardianMode = 'closed' | 'existing' | 'new';

@Component({
  selector: 'app-student-family-tab',
  imports: [ReactiveFormsModule],
  templateUrl: './family-tab.html',
  styleUrl: './family-tab.css',
})
export class FamilyTab {
  private readonly fb = inject(FormBuilder);
  private readonly guardiansService = inject(GuardiansService);
  private readonly emergencyContactsService = inject(EmergencyContactsService);
  private readonly usersService = inject(UsersService);

  readonly student = input.required<StudentDetailDto>();
  readonly isAdmin = input.required<boolean>();
  @Output() readonly saved = new EventEmitter<void>();

  protected readonly relationshipLabel = (rel: string | null | undefined) =>
    rel ? (RELATIONSHIP_LABELS[rel as GuardianRelationship] ?? rel) : '—';

  protected readonly guardians = computed(() => this.student().guardians);
  protected readonly emergencyContacts = computed(() => this.student().emergencyContacts);
  protected readonly isAdminEmergencyContact = isAdminEmergencyContact;
  protected readonly isAdminGuardianLink = isAdminGuardianLink;

  private readonly linkedGuardianIds = computed(() => {
    const s = this.student();
    if (!isAdminStudent(s)) {
      return new Set<string>();
    }
    return new Set(s.guardians.map((g) => g.id));
  });

  // --- Vínculo con tutores existentes / edición del vínculo ---

  protected readonly editingLinkId = signal<string | null>(null);
  protected readonly savingLink = signal(false);

  protected readonly linkForm = this.fb.nonNullable.group({
    relationship: ['FATHER' as GuardianRelationship],
    isPrimary: [false],
    hasAccess: [true],
  });

  startEditLink(guardian: StudentAdminLinkedGuardian): void {
    this.linkForm.reset({
      relationship: guardian.relationship,
      isPrimary: guardian.isPrimary,
      hasAccess: guardian.hasAccess,
    });
    this.editingLinkId.set(guardian.id);
  }

  cancelEditLink(): void {
    this.editingLinkId.set(null);
  }

  submitEditLink(): void {
    const guardianId = this.editingLinkId();
    if (!guardianId || this.savingLink()) {
      return;
    }
    this.saveLink(guardianId);
  }

  unlink(guardianId: string): void {
    if (!window.confirm('¿Desvincular a este tutor del estudiante?')) {
      return;
    }
    this.guardiansService.unlink(this.student().id, guardianId).subscribe(() => {
      this.saved.emit();
    });
  }

  // --- Añadir tutor (existente o nuevo) ---

  protected readonly addMode = signal<AddGuardianMode>('closed');
  protected readonly savingNewLink = signal(false);
  protected readonly availableGuardians = signal<GuardianDto[]>([]);
  protected readonly availableUsers = signal<UserSummary[]>([]);
  protected readonly selectedGuardianId = new FormControl('', { nonNullable: true });

  protected readonly newLinkForm = this.fb.nonNullable.group({
    relationship: ['FATHER' as GuardianRelationship],
    isPrimary: [false],
    hasAccess: [true],
  });

  protected readonly newGuardianForm = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.email, Validators.maxLength(255)]],
    phone: ['', [Validators.maxLength(30)]],
    userId: [''],
  });

  openAddExisting(): void {
    this.addMode.set('existing');
    this.selectedGuardianId.setValue('');
    this.newLinkForm.reset({ relationship: 'FATHER', isPrimary: false, hasAccess: true });
    this.guardiansService.list(0, 100).subscribe((response) => {
      const linked = this.linkedGuardianIds();
      this.availableGuardians.set(response.content.filter((g) => !linked.has(g.id)));
    });
  }

  openAddNew(): void {
    this.addMode.set('new');
    this.newLinkForm.reset({ relationship: 'FATHER', isPrimary: false, hasAccess: true });
    this.newGuardianForm.reset({ firstName: '', lastName: '', email: '', phone: '', userId: '' });
    this.usersService.listByRole('GUARDIAN', 0, 100).subscribe((response) => {
      this.availableUsers.set(response.content);
    });
  }

  closeAdd(): void {
    this.addMode.set('closed');
  }

  submitLinkExisting(): void {
    const guardianId = this.selectedGuardianId.value;
    if (!guardianId || this.savingNewLink()) {
      return;
    }
    this.savingNewLink.set(true);
    const body: StudentGuardianLinkRequest = this.newLinkForm.getRawValue();
    this.guardiansService.link(this.student().id, guardianId, body).subscribe({
      next: () => {
        this.savingNewLink.set(false);
        this.addMode.set('closed');
        this.saved.emit();
      },
      error: () => this.savingNewLink.set(false),
    });
  }

  submitNewGuardian(): void {
    if (this.newGuardianForm.invalid || this.savingNewLink()) {
      this.newGuardianForm.markAllAsTouched();
      return;
    }
    this.savingNewLink.set(true);
    const raw = this.newGuardianForm.getRawValue();
    const createBody: CreateGuardianRequest = {
      firstName: raw.firstName,
      lastName: raw.lastName,
      email: raw.email || undefined,
      phone: raw.phone || undefined,
      userId: raw.userId || undefined,
    };
    const linkBody: StudentGuardianLinkRequest = this.newLinkForm.getRawValue();
    this.guardiansService.create(createBody).subscribe({
      next: (guardian) => {
        this.guardiansService.link(this.student().id, guardian.id, linkBody).subscribe({
          next: () => {
            this.savingNewLink.set(false);
            this.addMode.set('closed');
            this.saved.emit();
          },
          error: () => this.savingNewLink.set(false),
        });
      },
      error: () => this.savingNewLink.set(false),
    });
  }

  private saveLink(guardianId: string): void {
    this.savingLink.set(true);
    const body: StudentGuardianLinkRequest = this.linkForm.getRawValue();
    this.guardiansService.link(this.student().id, guardianId, body).subscribe({
      next: () => {
        this.savingLink.set(false);
        this.editingLinkId.set(null);
        this.saved.emit();
      },
      error: () => this.savingLink.set(false),
    });
  }

  // --- Contactos de emergencia ---

  protected readonly addingContact = signal(false);
  protected readonly editingContactId = signal<string | null>(null);
  protected readonly savingContact = signal(false);

  protected readonly contactForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    phone: ['', [Validators.required, Validators.maxLength(30)]],
    relationship: ['', [Validators.maxLength(100)]],
    priority: [1],
    notes: ['', [Validators.maxLength(500)]],
  });

  openAddContact(): void {
    this.editingContactId.set(null);
    this.contactForm.reset({ name: '', phone: '', relationship: '', priority: 1, notes: '' });
    this.addingContact.set(true);
  }

  startEditContact(contact: {
    id: string;
    name: string;
    phone: string;
    relationship: string | null;
    priority: number;
    notes: string | null;
  }): void {
    this.addingContact.set(false);
    this.contactForm.reset({
      name: contact.name,
      phone: contact.phone,
      relationship: contact.relationship ?? '',
      priority: contact.priority,
      notes: contact.notes ?? '',
    });
    this.editingContactId.set(contact.id);
  }

  cancelContactForm(): void {
    this.addingContact.set(false);
    this.editingContactId.set(null);
  }

  submitContact(): void {
    if (this.contactForm.invalid || this.savingContact()) {
      this.contactForm.markAllAsTouched();
      return;
    }
    this.savingContact.set(true);
    const raw = this.contactForm.getRawValue();

    const editingId = this.editingContactId();
    if (editingId) {
      const body: UpdateEmergencyContactRequest = {
        name: raw.name,
        phone: raw.phone,
        relationship: raw.relationship || undefined,
        priority: raw.priority,
        notes: raw.notes || undefined,
      };
      this.emergencyContactsService.update(editingId, body).subscribe({
        next: () => {
          this.savingContact.set(false);
          this.editingContactId.set(null);
          this.saved.emit();
        },
        error: () => this.savingContact.set(false),
      });
      return;
    }

    const body: CreateEmergencyContactRequest = {
      name: raw.name,
      phone: raw.phone,
      relationship: raw.relationship || undefined,
      priority: raw.priority,
      notes: raw.notes || undefined,
    };
    this.emergencyContactsService.create(this.student().id, body).subscribe({
      next: () => {
        this.savingContact.set(false);
        this.addingContact.set(false);
        this.saved.emit();
      },
      error: () => this.savingContact.set(false),
    });
  }

  deleteContact(id: string): void {
    if (!window.confirm('¿Eliminar este contacto de emergencia?')) {
      return;
    }
    this.emergencyContactsService.delete(id).subscribe(() => {
      this.saved.emit();
    });
  }
}
