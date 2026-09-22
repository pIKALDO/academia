import { components } from '../api/schema';

export type GuardianDto = components['schemas']['GuardianDto'];
export type CreateGuardianRequest = components['schemas']['CreateGuardianRequest'];
export type UpdateGuardianRequest = components['schemas']['UpdateGuardianRequest'];

export type StudentGuardianLinkDto = components['schemas']['StudentGuardianLinkDto'];
export type StudentGuardianLinkRequest = components['schemas']['StudentGuardianLinkRequest'];
export type GuardianRelationship = StudentGuardianLinkRequest['relationship'];
