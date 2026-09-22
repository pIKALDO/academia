package com.academia.devdata;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Datos inventados para probar los permisos a mano en local: dos familias, tres estudiantes.
 *
 * <pre>
 * Familia Kovalenko                       Familia Martín García
 *   olena.kovalenko@familia.local           marta.garcia@familia.local
 *     → Danylo, Sofiia (has_access)           → Lucía (has_access)
 *   taras.kovalenko@familia.local
 *     → Danylo (has_access)
 *     → Sofiia (vinculado, SIN has_access)
 * </pre>
 *
 * Lo que se puede comprobar con esto: Olena ve a sus dos hijos y no a Lucía (404); Taras ve a
 * Danylo pero Sofiia le da 404 aunque figure como su padre; Marta solo ve a Lucía; ninguna
 * familia recibe {@code coachNotes} ni {@code housing}, que sí están rellenos.
 *
 * <p>SQL directo y no los servicios: los servicios exigen una sesión de ADMIN, y los
 * repositorios de cada módulo son package-private a propósito. Un seed de desarrollo no
 * justifica abrir esa visibilidad.
 *
 * <p>Solo en el perfil {@code local}, solo si {@code students} está vacía (idempotente al
 * reiniciar) y solo si {@code SEED_FAMILY_PASSWORD} está definida: como con el admin de
 * {@code LocalAdminBootstrapper}, la contraseña no se escribe en el repositorio. Sin ella el
 * seed se omite, no falla: es opcional.
 */
@Component
@Profile("local")
class LocalDemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDemoDataSeeder.class);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final String familyPassword;

    LocalDemoDataSeeder(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
            @Value("${app.seed.family-password:}") String familyPassword) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.familyPassword = familyPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (familyPassword.isBlank()) {
            log.info("SEED_FAMILY_PASSWORD no definida: no se cargan los datos de demostración.");
            return;
        }
        Integer students = jdbc.queryForObject("SELECT count(*) FROM students", Integer.class);
        if (students != null && students > 0) {
            return;
        }

        String hash = passwordEncoder.encode(familyPassword);

        // Familia Kovalenko: dos hermanos, dos tutores.
        UUID olena = guardian(user("olena.kovalenko@familia.local", "Olena Kovalenko", hash),
                "Olena", "Kovalenko", "+34 600 000 001", "olena.kovalenko@familia.local");
        UUID taras = guardian(user("taras.kovalenko@familia.local", "Taras Kovalenko", hash),
                "Taras", "Kovalenko", "+34 600 000 002", "taras.kovalenko@familia.local");

        UUID danylo = student("Danylo", "Kovalenko", LocalDate.of(2011, 4, 22), "UA");
        UUID sofiia = student("Sofiia", "Kovalenko", LocalDate.of(2013, 9, 3), "UA");

        link(danylo, olena, "MOTHER", true, true);
        link(danylo, taras, "FATHER", false, true);
        link(sofiia, olena, "MOTHER", true, true);
        // Figura como padre en la ficha, pero sin acceso: Sofiia tiene que darle 404.
        link(sofiia, taras, "FATHER", false, false);

        sportsProfile(danylo, "Nacional sub-14", "RIGHT", "Necesita trabajar el revés cortado");
        housing(danylo, "Residencia de la academia, hab. 12", "Responsable de residencia");
        education(danylo, "IES Ejemplo", "2º ESO");
        emergencyContact(danylo, "Iryna Kovalenko", "Abuela", "+34 600 000 010");

        sportsProfile(sofiia, "Autonómico sub-12", "LEFT", "Muy buena actitud en los entrenamientos");
        education(sofiia, "CEIP Ejemplo", "6º Primaria");
        emergencyContact(sofiia, "Iryna Kovalenko", "Abuela", "+34 600 000 010");

        // Familia Martín García: una hija, una tutora.
        UUID marta = guardian(user("marta.garcia@familia.local", "Marta García", hash),
                "Marta", "García", "+34 600 000 003", "marta.garcia@familia.local");

        UUID lucia = student("Lucía", "Martín García", LocalDate.of(2012, 2, 11), "ES");
        link(lucia, marta, "MOTHER", true, true);

        sportsProfile(lucia, "Nacional sub-14", "RIGHT", "Le cuesta gestionar la presión en los tie-breaks");
        housing(lucia, "Familia de acogida, calle Ejemplo 3", "Familia de acogida");
        education(lucia, "IES Ejemplo", "1º ESO");
        emergencyContact(lucia, "Pedro Martín", "Padre", "+34 600 000 020");

        log.warn("Datos de demostración cargados: 2 familias (3 cuentas de tutor) y 3 estudiantes (solo en el perfil local).");
    }

    private UUID user(String email, String displayName, String passwordHash) {
        UUID id = newId();
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, role, status, display_name)
                VALUES (?, ?, ?, 'GUARDIAN'::user_role, 'ACTIVE'::user_status, ?)
                """, id, email, passwordHash, displayName);
        return id;
    }

    private UUID guardian(UUID userId, String firstName, String lastName, String phone, String email) {
        UUID id = newId();
        jdbc.update("""
                INSERT INTO guardians (id, user_id, first_name, last_name, phone, email)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, userId, firstName, lastName, phone, email);
        return id;
    }

    private UUID student(String firstName, String lastName, LocalDate birthDate, String nationality) {
        UUID id = newId();
        jdbc.update("""
                INSERT INTO students (id, first_name, last_name, birth_date, nationality, country, status, enrolled_at)
                VALUES (?, ?, ?, ?, ?, 'ES', 'ACTIVE'::student_status, ?)
                """, id, firstName, lastName, Date.valueOf(birthDate), nationality, Date.valueOf(LocalDate.of(2026, 1, 15)));
        return id;
    }

    private void link(UUID studentId, UUID guardianId, String relationship, boolean primary, boolean hasAccess) {
        jdbc.update("""
                INSERT INTO student_guardians (student_id, guardian_id, relationship, is_primary, has_access)
                VALUES (?, ?, ?::guardian_relationship, ?, ?)
                """, studentId, guardianId, relationship, primary, hasAccess);
    }

    private void sportsProfile(UUID studentId, String level, String dominantHand, String coachNotes) {
        jdbc.update("""
                INSERT INTO sports_profiles (student_id, level, dominant_hand, goals, coach_notes)
                VALUES (?, ?, ?::dominant_hand, 'Competir a nivel nacional', ?)
                """, studentId, level, dominantHand, coachNotes);
    }

    private void housing(UUID studentId, String addressLine, String responsibleName) {
        jdbc.update("""
                INSERT INTO housing_info (student_id, address_line, city, responsible_name, responsible_phone, notes)
                VALUES (?, ?, 'València', ?, '+34 600 000 099', 'Nota interna de alojamiento')
                """, studentId, addressLine, responsibleName);
    }

    private void education(UUID studentId, String schoolName, String grade) {
        jdbc.update("""
                INSERT INTO education_info (student_id, school_name, grade)
                VALUES (?, ?, ?)
                """, studentId, schoolName, grade);
    }

    private void emergencyContact(UUID studentId, String name, String relationship, String phone) {
        jdbc.update("""
                INSERT INTO emergency_contacts (id, student_id, name, relationship, phone, priority)
                VALUES (?, ?, ?, ?, ?, 1)
                """, newId(), studentId, name, relationship, phone);
    }

    /** El mismo generador UUID v7 que usan las entidades con {@code @UuidGenerator(style = TIME)}. */
    private static UUID newId() {
        return UuidVersion7Strategy.INSTANCE.generateUuid(null);
    }
}
