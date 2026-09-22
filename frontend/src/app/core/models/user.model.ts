import { components } from '../api/schema';

/** Refleja UserRole del backend (com.academia.users.UserRole). */
export type UserRole = components['schemas']['UserProfileDto']['role'];

/** Refleja UserStatus del backend (com.academia.users.UserStatus). */
export type UserStatus = components['schemas']['UserProfileDto']['status'];

/** Respuesta de GET /auth/me. Tipo generado desde docs/openapi.json. */
export type UserProfile = components['schemas']['UserProfileDto'];
