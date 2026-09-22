import { HttpInterceptorFn } from '@angular/common/http';

const CSRF_COOKIE_NAME = 'XSRF-TOKEN';
const CSRF_HEADER_NAME = 'X-XSRF-TOKEN';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);

function readCookie(name: string): string | null {
  const match = document.cookie
    .split('; ')
    .find((row) => row.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.substring(name.length + 1)) : null;
}

/**
 * Reenvía el token CSRF que Spring Security deja en la cookie XSRF-TOKEN
 * (CookieCsrfTokenRepository.withHttpOnlyFalse) en la cabecera X-XSRF-TOKEN, para las
 * peticiones que modifican estado (docs/diseno-api.md sección 2.1).
 */
export const csrfInterceptor: HttpInterceptorFn = (req, next) => {
  if (SAFE_METHODS.has(req.method) || !req.url.startsWith('/api/')) {
    return next(req);
  }

  const token = readCookie(CSRF_COOKIE_NAME);
  if (!token) {
    return next(req);
  }

  return next(req.clone({ setHeaders: { [CSRF_HEADER_NAME]: token } }));
};
