# Academia — plataforma de gestión

Plataforma privada de gestión para una academia de tenis: fichas de estudiantes,
documentación con control de caducidades y acceso diferenciado para administración
y familias.

**Prototipo con datos inventados.** No contiene ni debe contener datos reales de
ninguna persona.

## Documentos de referencia

Antes de implementar cualquier cosa, consulta:

- `docs/modelo-datos.md` — esquema completo, con la justificación de cada decisión
- `docs/diseno-api.md` — contrato de la API: endpoints, DTOs por rol, códigos de estado

**Estos documentos son la fuente de verdad.** Si algo que te pido contradice lo que
dicen, dímelo antes de implementarlo en lugar de elegir por tu cuenta.

## Stack

- Java 25, Spring Boot 4.1 (verificar versión vigente antes de fijarla)
- PostgreSQL 17, JPA/Hibernate, Flyway
- Spring Security con sesión y cookie
- Almacenamiento S3-compatible: MinIO en local, Cloudflare R2 en servidor
- springdoc-openapi
- JUnit 5 + Testcontainers

## Estructura de paquetes

Organización **por dominio, no por capa técnica**:

```
com.academia
├── config/          seguridad, OpenAPI, almacenamiento
├── common/
│   ├── audit/       aspecto de registro de acciones
│   ├── error/       ProblemDetail y manejador global
│   └── web/         paginación
├── security/        AccessService y resolución de permisos
├── users/           usuarios y autenticación
├── students/        estudiantes y bloques de ficha
├── guardians/       tutores y vínculos
└── documents/       documentación y caducidades
```

Dentro de cada módulo: `XController`, `XService`, `XRepository`, `dto/`, `XEntity`.
No crear paquetes `controller/`, `service/`, `repository/` en la raíz.

## Reglas no negociables

Estas atraviesan todo el código. Si una tarea te obliga a romper una, para y avisa.

**1. Recursos ajenos devuelven 404, no 403.**
Un 403 confirmaría que ese identificador existe. El 403 se reserva para cuando el
usuario ve el recurso pero no puede hacer esa operación (una familia intentando
revisar un documento de su propio hijo).

**2. La autorización vive en la capa de servicio.**
`@PreAuthorize("@access.canViewStudent(#studentId)")` sobre el método de servicio,
nunca en el controlador y nunca en el front. Toda regla nueva se añade a
`AccessService`.

**3. DTOs separados por rol.**
`StudentAdminDto` y `StudentGuardianDto` son clases distintas. Prohibido usar una
clase con campos anulables y `@JsonInclude(NON_NULL)`: un campo nuevo se filtraría
por defecto. Con clases separadas, el campo no existe hasta que alguien lo añade
a propósito.

**4. `coachNotes` y el bloque `housing` nunca salen al portal de familias.**
Hay un test que lo verifica. No lo relajes.

**5. Ningún error expone detalles internos.**
Nada de trazas, nombres de tabla ni SQL en las respuestas. El detalle va al log
con un `traceId`; la respuesta lleva ese identificador.

**6. Los ficheros no se sirven por URL pública.**
Descarga: el backend valida permiso y responde `302` a una URL prefirmada de 60
segundos. La clave de almacenamiento es `students/{studentId}/{uuid}`, nunca el
nombre original del fichero.

**7. Validación de subidas por contenido, no por extensión ni por `Content-Type`.**
Ambos son triviales de falsear.

**8. Toda colección se pagina**, envuelta en `PagedResponse<T>`. Nunca serializar
`Page` de Spring Data directamente. Tope de `size` en 100.

**9. `ddl-auto: validate`, nunca `update`.** El esquema lo define Flyway.

**10. El estado del documento no incluye «caducado».**
`PENDING → RECEIVED → REVIEWED` es flujo de trabajo. La caducidad se deriva de
`expires_at` y se expone como campo calculado (`expired`, `daysUntilExpiry`).

## Convenciones

- **Base de datos:** `snake_case`. **API:** `camelCase`. **Java:** estándar.
- **Identificadores:** UUID v7 vía `@UuidGenerator(style = TIME)` de Hibernate.
- **Fechas:** `LocalDate` para fechas, `Instant` UTC para marcas de tiempo.
  Todas las columnas temporales son `TIMESTAMPTZ`.
- **Inyección por constructor**, nunca `@Autowired` en campos.
- **Records para DTOs.** Entidades JPA como clases.
- **Nada de Lombok.** Con records y Java 25 no aporta, y añade una dependencia
  que rompe en cada actualización de versión.
- **Transacciones en el servicio**, no en el repositorio ni en el controlador.
- **Mapeo entidad→DTO con métodos estáticos explícitos** en el propio DTO
  (`StudentAdminDto.from(entity)`), no MapStruct ni ModelMapper: el mapeo es donde
  se decide qué campo sale a cada rol, y tiene que ser legible de un vistazo.

## Tests

- Nombres en español y descriptivos: `familia_no_puede_ver_estudiante_de_otra_familia`.
- Tests de integración con Testcontainers (PostgreSQL real), no H2: el esquema usa
  tipos `ENUM`, `JSONB` e `INET` de PostgreSQL que H2 no reproduce.
- **Cada regla de `AccessService` tiene su test.** Es el núcleo del proyecto.
- Sufijo `IT` para tests de integración, `Test` para unitarios.

## Commits

Conventional Commits, en español, en imperativo:

```
feat(students): añadir endpoint de listado con filtro por estado
fix(documents): evitar reenvío de avisos tras reinicio
test(security): verificar 404 en estudiante de otra familia
docs(api): documentar códigos de error del módulo de documentos
refactor(common): extraer PagedResponse
```

Un commit por unidad lógica. No mezclar refactor con funcionalidad nueva.

## Cómo quiero que trabajes

- **Explícame las decisiones técnicas** cuando elijas entre alternativas. Necesito
  poder defenderlas en una entrevista.
- **Señala malas prácticas y problemas** aunque no los pregunte. No valides algo
  solo por complacerme.
- **No implementes de más.** Si una tarea pide un endpoint, no construyas cuatro.
- **Pregunta cuando algo sea ambiguo** en lugar de decidir por tu cuenta.
- Respuestas concisas. El detalle, en el código y sus comentarios.

## Fuera de alcance en fase 1

No implementes nada de esto aunque parezca natural: datos médicos, alergias e
intolerancias, necesidades, datos físicos, trámites, calendario, portal del
estudiante, seguimiento deportivo, avisos manuales, Telegram, permisos
configurables por campo, informes, pagos.

`docs/modelo-datos.md` reserva el hueco de estas tablas en el diseño, pero no se
crean sus migraciones.
