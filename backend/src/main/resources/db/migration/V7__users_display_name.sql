-- Nombre visible del usuario: falta en V1 y hace falta tanto para /auth/me como para
-- uploadedBy.displayName en la representación de documentos (docs/diseno-api.md sección 5.6).
--
-- NOT NULL sin DEFAULT: es un prototipo sin datos reales (CLAUDE.md), así que no hay filas
-- existentes que retroalimentar con un valor inventado.

ALTER TABLE users ADD COLUMN display_name VARCHAR(150) NOT NULL;
