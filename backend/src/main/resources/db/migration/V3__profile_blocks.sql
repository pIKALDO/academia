-- Bloques de la ficha del estudiante: tres tablas en relación 1:1 con students, con la
-- clave primaria compartida. Ver docs/modelo-datos.md sección 4.
--
-- Por qué tablas separadas y no columnas en students, en orden de peso:
-- 1. Los permisos son por bloque, no por estudiante: coach_notes no debe llegar nunca al
--    portal de familias, housing_info no debe llegar al portal del estudiante. Con bloques
--    separados el control se hace a nivel de recurso y es verificable con un test por bloque.
-- 2. Establece el patrón para medical_data: cuando (y si) llegue, entra como una tabla 1:1
--    más, sin tocar nada existente.
-- 3. Mantiene students legible: la entidad central se consulta en cada listado.
--
-- El coste: cargar la ficha completa son cuatro JOIN en lugar de uno. Irrelevante con este
-- volumen, y el listado de estudiantes no los necesita.

CREATE TYPE dominant_hand AS ENUM ('RIGHT', 'LEFT', 'AMBIDEXTROUS');

-- goals y coach_notes como TEXT libre en fase 1. Cuando llegue el seguimiento deportivo
-- estructurado (fase 3), pasan a evaluations y estos campos quedan como resumen.
CREATE TABLE sports_profiles (
    student_id      UUID PRIMARY KEY REFERENCES students(id) ON DELETE CASCADE,
    level           VARCHAR(50),
    dominant_hand   dominant_hand,
    ranking         VARCHAR(50),
    previous_club   VARCHAR(200),
    history         TEXT,
    goals           TEXT,
    -- coach_notes nunca sale al portal de familias (regla no negociable nº5).
    coach_notes     TEXT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE education_info (
    student_id      UUID PRIMARY KEY REFERENCES students(id) ON DELETE CASCADE,
    school_name     VARCHAR(200),
    grade           VARCHAR(100),
    schedule_notes  TEXT,
    notes           TEXT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- housing_info nunca sale al portal de familias (regla no negociable nº5).
CREATE TABLE housing_info (
    student_id          UUID PRIMARY KEY REFERENCES students(id) ON DELETE CASCADE,
    address_line        VARCHAR(255),
    city                VARCHAR(100),
    responsible_name    VARCHAR(200),
    responsible_phone   VARCHAR(30),
    notes               TEXT,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
