import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'password-reset',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/password-reset/password-reset-request').then(
        (m) => m.PasswordResetRequest,
      ),
  },
  {
    path: 'password-reset/confirm',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/password-reset/password-reset-confirm').then(
        (m) => m.PasswordResetConfirm,
      ),
  },
  {
    path: 'activate',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/activate/activate').then((m) => m.Activate),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./features/home/home').then((m) => m.Home),
  },
  { path: '**', redirectTo: '' },
];
