/** Refleja UserRole del backend (com.academia.users.UserRole). */
export type UserRole = 'ADMIN' | 'GUARDIAN' | 'STUDENT';

/** Refleja UserStatus del backend (com.academia.users.UserStatus). */
export type UserStatus = 'PENDING_ACTIVATION' | 'ACTIVE' | 'DISABLED';

/** Respuesta de GET /auth/me (com.academia.users.dto.UserProfileDto). */
export interface UserProfile {
  id: string;
  email: string;
  displayName: string;
  role: UserRole;
  status: UserStatus;
  lastLoginAt: string | null;
}
