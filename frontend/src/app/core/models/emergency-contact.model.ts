import { components } from '../api/schema';

export type EmergencyContactAdminDto = components['schemas']['EmergencyContactAdminDto'];
export type EmergencyContactGuardianDto = components['schemas']['EmergencyContactGuardianDto'];
/** Refleja el `oneOf` de EmergencyContactDto: la vista depende del rol de la sesión. */
export type EmergencyContactDto = EmergencyContactAdminDto | EmergencyContactGuardianDto;

export type CreateEmergencyContactRequest = components['schemas']['CreateEmergencyContactRequest'];
export type UpdateEmergencyContactRequest = components['schemas']['UpdateEmergencyContactRequest'];

/** Solo la vista de administrador trae `id` (y por tanto permite editar/borrar). */
export function isAdminEmergencyContact(
  contact: EmergencyContactDto,
): contact is EmergencyContactAdminDto {
  return 'id' in contact;
}
