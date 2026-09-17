# Academia — plataforma de gestión

Plataforma privada de gestión para una academia de tenis: fichas de estudiantes,
documentación con control de caducidades y acceso diferenciado para administración
y familias.

**Prototipo con datos inventados.** No contiene ni debe contener datos reales de
ninguna persona.

## Organización del repositorio

Monorepo. Backend y frontend evolucionan juntos y se versionan juntos.

```
academia/
├── CLAUDE.md
├── README.md
├── docker-compose.yml       PostgreSQL, MinIO y Mailpit para desarrollo local
├── docs/
│   ├── modelo-datos.md      esquema completo y justificación de cada decisión
│   └── diseno-api.md        contrato de la API: endpoints, DTOs por rol, códigos
├── backend/                 Spring Boot
│   ├── pom.xml
│   └── src/
└── frontend/                Angular
    ├── package.json
    └── src/
```

**Todas las rutas de este documento son relativas a la raíz del repositorio.**
El `pom.xml` va en `backend/`, nunca en la raíz. El `package.json` en `frontend/`.
El `docker-compose.yml` sí va en la raíz: levanta la infraestructura común.

## Documentos de referencia

Antes de implementar cualquier cosa, consulta:

- `docs/modelo-datos.md` — esquema completo, con la justificación de cada decisión
- `docs/diseno-api.md` — contrato de la API: endpoints, DTOs por rol, códigos de estado

**Estos documentos son la fuente de verdad.** Si algo que te pido contradice lo que
dicen, dímelo antes de implementarlo en lugar de elegir por tu cuenta.

## Stack

**Backend**
- Java 25, Spring Boot 4.1 (verificar la versión vigente antes de fijarla)
- PostgreSQL 17, JPA/Hibernate, Flyway
- Spring Security con sesión y cookie
- Almacenamiento S3-compatible: MinIO en local, Cloudflare R2 en servidor
- springdoc-openapi
- JUnit 5 + Testcontainers

**Frontend**
- Angular, configurado como PWA instalable
- El sistema visual procede de Google Stitch: los tokens de diseño se importan
  como variables CSS desde `frontend/src/styles/tokens.css`. Stitch genera
  HTML/React, así que su código sirve de referencia visual, no se copia.

## Estructura del backend

Organización **por dominio, no por capa técnica**:

```
backend/src/main/java/com/academia
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

## Estructura del frontend

```
frontend/src/app
├── core/            interceptores, guardas, servicio de sesión
├── shared/          componentes reutilizables
├── features/
│   ├── auth/
│   ├── students/
│   ├── documents/
│   └── family/      portal de familias
└── styles/          tokens.css importados de Stitch
```

## Reglas no negociables

Estas atraviesan todo el código. Si una tarea te obliga a romper una, para y avisa.

**1. Recursos ajenos devuelven 404, no 403.**
Un 403 confirmaría que ese identificador existe. El 403 se reserva para cuando el
usuario ve el recurso pero no puede hacer esa operación (una familia intentando
revisar un documento de su propio hijo).

**2. La autorización vive en la capa de servicio del backend.**
`@PreAuthorize("@access.canViewStudent(#studentId)")` sobre el método de servicio,
nunca en el controlador y nunca en el front. Toda regla nueva se añade a
`AccessService`.

**3. El frontend no filtra por permisos.**
Oculta lo que la API no le devuelve; nunca recibe un campo y decide no mostrarlo.
Si una vista necesita esconder un dato, es que ese dato no debería haber salido
del backend.

**4. DTOs separados por rol.**
`StudentAdminDto` y `StudentGuardianDto` son clases distintas. Prohibido usar una
clase con campos anulables y `@JsonInclude(NON_NULL)`: un campo nuevo se filtraría
por defecto. Con clases separadas, el campo no existe hasta que alguien lo añade
a propósito.

**5. `coachNotes` y el bloque `housing` nunca salen al portal de familias.**
Hay un test que lo verifica. No lo relajes.

**6. Ningún error expone detalles internos.**
Nada de trazas, nombres de tabla ni SQL en las respuestas. El detalle va al log
con un `traceId`; la respuesta lleva ese identificador.

**7. Los ficheros no se sirven por URL pública.**
Descarga: el backend valida permiso y responde `302` a una URL prefirmada de 60
segundos. La clave de almacenamiento es `students/{studentId}/{uuid}`, nunca el
nombre original del fichero.

**8. Validación de subidas por contenido, no por extensión ni por `Content-Type`.**
Ambos son triviales de falsear.

**9. Toda colección se pagina**, envuelta en `PagedResponse<T>`. Nunca serializar
`Page` de Spring Data directamente. Tope de `size` en 100.

**10. `ddl-auto: validate`, nunca `update`.** El esquema lo define Flyway.

**11. El estado del documento no incluye «caducado».**
`PENDING → RECEIVED → REVIEWED` es flujo de trabajo. La caducidad se deriva de
`expires_at` y se expone como campo calculado (`expired`, `daysUntilExpiry`).

## Convenciones

**Backend**
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

**Frontend**
- Componentes standalone, sin NgModules.
- Señales para el estado local; servicios para el estado compartido.
- Un servicio de API por dominio, con los tipos generados a partir de OpenAPI.
- Los tipos TypeScript de la API **no se escriben a mano**: se generan desde
  `docs/openapi.json` con `openapi-typescript`. Así una ruptura del contrato se
  ve al compilar.
- Interceptor único que traduce `ProblemDetail` al mensaje mostrado al usuario.

## Tests

- Nombres en español y descriptivos: `familia_no_puede_ver_estudiante_de_otra_familia`.
- Tests de integración con Testcontainers (PostgreSQL real), no H2: el esquema usa
  tipos `ENUM`, `JSONB` e `INET` de PostgreSQL que H2 no reproduce.
- **Cada regla de `AccessService` tiene su test.** Es el núcleo del proyecto.
- Sufijo `IT` para tests de integración, `Test` para unitarios.

## Commits

Conventional Commits, en español, en imperativo. **El ámbito indica el módulo, y
para cambios de front se antepone `web`:**

```
feat(students): añadir endpoint de listado con filtro por estado
feat(web/students): añadir tabla de estudiantes con filtro
fix(documents): evitar reenvío de avisos tras reinicio
test(security): verificar 404 en estudiante de otra familia
docs(api): documentar códigos de error del módulo de documentos
chore(ci): filtrar el workflow de backend por ruta
```

Un commit por unidad lógica. No mezclar refactor con funcionalidad nueva.
No mezclar en un mismo commit cambios de `backend/` y de `frontend/` salvo que
sean la misma unidad lógica (por ejemplo, añadir un campo a la API y consumirlo).

## Integración continua

Dos workflows con filtros por ruta, para que un cambio en el front no dispare los
tests de Java ni al revés:

- `.github/workflows/backend.yml` → `paths: ['backend/**', '.github/workflows/backend.yml']`
- `.github/workflows/frontend.yml` → `paths: ['frontend/**', '.github/workflows/frontend.yml']`

El workflow de backend exporta la especificación OpenAPI a `docs/openapi.json` y
falla si el fichero commiteado no coincide: así cualquier ruptura del contrato
aparece en el diff de la pull request antes de desplegarse.

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
