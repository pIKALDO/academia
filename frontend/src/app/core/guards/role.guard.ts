import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { UserRole } from '../models/user.model';
import { SessionService } from '../services/session.service';

/**
 * Guarda de rutas por rol. Uso: `canActivate: [roleGuard(['ADMIN'])]`.
 * Solo decide navegación: la autorización real vive en el backend (AccessService),
 * nunca aquí (regla no negociable nº 3 de CLAUDE.md).
 */
export function roleGuard(allowedRoles: UserRole[]): CanActivateFn {
  return () => {
    const session = inject(SessionService);
    const router = inject(Router);

    const user = session.currentUser();
    if (!user) {
      return router.createUrlTree(['/login']);
    }
    if (!allowedRoles.includes(user.role)) {
      return router.createUrlTree(['/']);
    }
    return true;
  };
}
