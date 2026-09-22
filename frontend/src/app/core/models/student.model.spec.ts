import {
  EmergencyContactAdminDto,
  EmergencyContactGuardianDto,
  isAdminEmergencyContact,
} from './emergency-contact.model';
import {
  isAdminGuardianLink,
  isAdminStudent,
  StudentAdminDto,
  StudentAdminLinkedGuardian,
  StudentGuardianDto,
  StudentGuardianLinkedGuardian,
} from './student.model';

/**
 * `StudentDetailDto`/`EmergencyContactDto` son un `oneOf` sin discriminador (docs/diseno-api.md
 * sección 4): el frontend distingue la vista por la presencia de la clave, nunca por el rol de
 * sesión (regla no negociable nº 3 de CLAUDE.md). Estos tests fijan ese contrato: si el backend
 * dejara de omitir una clave para GUARDIAN, debería romper aquí antes que en producción.
 */
describe('isAdminStudent', () => {
  it('reconoce la vista de administrador por la presencia de `housing`', () => {
    const admin = { housing: null } as unknown as StudentAdminDto;
    expect(isAdminStudent(admin)).toBe(true);
  });

  it('la vista de familia no trae la clave `housing` en absoluto', () => {
    const guardian = {} as unknown as StudentGuardianDto;
    expect(isAdminStudent(guardian)).toBe(false);
  });
});

describe('isAdminGuardianLink', () => {
  it('reconoce el vínculo de administrador por la presencia de `id`', () => {
    const link = { id: 'g1' } as unknown as StudentAdminLinkedGuardian;
    expect(isAdminGuardianLink(link)).toBe(true);
  });

  it('el vínculo de familia no trae `id`, `hasAccess` ni contacto del tutor', () => {
    const link = { firstName: 'Olena' } as unknown as StudentGuardianLinkedGuardian;
    expect(isAdminGuardianLink(link)).toBe(false);
  });
});

describe('isAdminEmergencyContact', () => {
  it('reconoce el contacto de administrador por la presencia de `id`', () => {
    const contact = { id: 'c1' } as unknown as EmergencyContactAdminDto;
    expect(isAdminEmergencyContact(contact)).toBe(true);
  });

  it('el contacto de familia no trae `id`, `priority` ni `notes`', () => {
    const contact = { name: 'Iryna' } as unknown as EmergencyContactGuardianDto;
    expect(isAdminEmergencyContact(contact)).toBe(false);
  });
});
