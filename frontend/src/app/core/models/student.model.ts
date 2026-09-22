import { components } from '../api/schema';

export type StudentStatus = components['schemas']['StudentListDto']['status'];
export type StudentListDto = components['schemas']['StudentListDto'];

export type StudentAdminDto = components['schemas']['StudentAdminDto'];
export type StudentGuardianDto = components['schemas']['StudentGuardianDto'];
/** Refleja el `oneOf` de StudentDetailDto: la vista depende del rol de la sesión. */
export type StudentDetailDto = StudentAdminDto | StudentGuardianDto;

export type CreateStudentRequest = components['schemas']['CreateStudentRequest'];
export type UpdateStudentRequest = components['schemas']['UpdateStudentRequest'];

export type EducationDto = components['schemas']['EducationDto'];
export type EducationRequest = components['schemas']['EducationRequest'];
export type HousingDto = components['schemas']['HousingDto'];
export type HousingRequest = components['schemas']['HousingRequest'];
export type SportsProfileDto = components['schemas']['SportsProfileDto'];
export type SportsProfileRequest = components['schemas']['SportsProfileRequest'];
export type DominantHand = NonNullable<SportsProfileDto['dominantHand']>;

export type StudentAdminLinkedGuardian = components['schemas']['StudentAdminLinkedGuardian'];
export type StudentGuardianLinkedGuardian = components['schemas']['StudentGuardianLinkedGuardian'];

/**
 * `housing` solo existe como clave en la vista de administrador (regla no negociable nº 5):
 * el tipo de familia ni siquiera declara la propiedad, así que basta con comprobar su presencia.
 */
export function isAdminStudent(student: StudentDetailDto): student is StudentAdminDto {
  return 'housing' in student;
}

/** Solo la vista de administrador trae `id`, `hasAccess` y los datos de contacto del tutor. */
export function isAdminGuardianLink(
  guardian: StudentAdminLinkedGuardian | StudentGuardianLinkedGuardian,
): guardian is StudentAdminLinkedGuardian {
  return 'id' in guardian;
}
