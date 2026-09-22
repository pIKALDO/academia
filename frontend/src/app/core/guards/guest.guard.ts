import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionService } from '../services/session.service';

/** Evita que un usuario ya autenticado vuelva a ver login/activación/recuperación. */
export const guestGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);

  if (session.isAuthenticated()) {
    return router.createUrlTree(['/']);
  }
  return true;
};
