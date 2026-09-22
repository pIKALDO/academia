import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ProblemDetail } from '../models/problem-detail.model';
import { NotificationService } from '../services/notification.service';

const FALLBACK_MESSAGES: Record<number, string> = {
  0: 'No se ha podido contactar con el servidor. Comprueba tu conexión.',
  400: 'La petición no es válida.',
  401: 'Tu sesión ha caducado. Vuelve a iniciar sesión.',
  403: 'No tienes permiso para realizar esta acción.',
  404: 'El recurso solicitado no existe.',
  409: 'La operación entra en conflicto con el estado actual.',
  413: 'El fichero supera el tamaño máximo permitido.',
  415: 'El tipo de fichero no está permitido.',
  422: 'Los datos enviados no son válidos.',
  429: 'Demasiados intentos. Inténtalo de nuevo más tarde.',
  500: 'Ha ocurrido un error inesperado. Inténtalo de nuevo.',
};

/**
 * Traduce las respuestas de error (ProblemDetail, RFC 7807) en un único mensaje para el
 * usuario. `detail` ya viene en español desde GlobalExceptionHandler; solo hace falta un
 * mensaje de reserva cuando no hay cuerpo (errores de red, 500 sin ProblemDetail).
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notifications = inject(NotificationService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && req.url.startsWith('/api/')) {
        const problem = error.error as ProblemDetail | null;
        const message =
          problem?.detail ?? FALLBACK_MESSAGES[error.status] ?? FALLBACK_MESSAGES[500];

        const isLoginAttempt = req.url.endsWith('/auth/login');
        const isSessionCheck = req.url.endsWith('/auth/me');
        if (!(error.status === 401 && (isLoginAttempt || isSessionCheck))) {
          notifications.showError(message);
        }
      }
      return throwError(() => error);
    }),
  );
};
