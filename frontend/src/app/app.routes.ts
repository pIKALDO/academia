import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { roleGuard } from './core/guards/role.guard';

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
    loadComponent: () => import('./shared/shell/shell').then((m) => m.Shell),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/students/students-list/students-list').then(
            (m) => m.StudentsList,
          ),
      },
      {
        path: 'students/new',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/students/student-create/student-create').then(
            (m) => m.StudentCreate,
          ),
      },
      {
        path: 'students/:id',
        loadComponent: () =>
          import('./features/students/student-detail/student-detail').then(
            (m) => m.StudentDetail,
          ),
      },
      {
        path: 'guardians',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/guardians/guardians-list/guardians-list').then(
            (m) => m.GuardiansList,
          ),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
