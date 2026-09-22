/** RFC 7807, tal como lo sirve GlobalExceptionHandler (docs/diseno-api.md sección 7). */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  timestamp?: string;
  errors?: FieldError[];
}

export interface FieldError {
  field: string;
  message: string;
}
