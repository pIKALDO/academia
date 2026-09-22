import { components } from '../api/schema';

export type PageInfo = components['schemas']['PageInfo'];

/**
 * Misma forma que los `PagedResponse*Dto` generados (`content` + `page`), pero genérica: evita
 * repetir el envoltorio por cada recurso paginado.
 */
export interface PagedResponse<T> {
  content: T[];
  page: PageInfo;
}
