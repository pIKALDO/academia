package com.academia.support;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Prepara usuarios, tutores, estudiantes y vínculos con SQL directo para los tests de
 * integración: el escenario de cada test se lee de un vistazo y no depende de que los
 * endpoints de alta funcionen (eso lo prueban sus propios tests).
 *
 * <p>La base de datos se comparte entre tests y no se limpia: cada test usa emails y nombres
 * únicos ({@link #unique}) para no ver los datos de los demás.
 */
@Component
public class StudentTestData {

    public static final String PASSWORD = "password-123";

    private final JdbcTemplate jdbc;
    private final String passwordHash;

    StudentTestData(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        // Un solo hash para toda la clase: BCrypt(12) tarda ~250 ms por llamada.
        this.passwordHash = passwordEncoder.encode(PASSWORD);
    }

    public static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Usuario activo con {@link #PASSWORD}; devuelve su id. */
    public UUID user(String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, role, status, display_name)
                VALUES (?, ?, ?, ?::user_role, 'ACTIVE'::user_status, 'Usuario de prueba')
                """, id, email, passwordHash, role);
        return id;
    }

    public UUID guardian(UUID userId, String firstName, String lastName) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO guardians (id, user_id, first_name, last_name, phone, email)
                VALUES (?, ?, ?, ?, '+34 600 111 222', 'tutor@example.com')
                """, id, userId, firstName, lastName);
        return id;
    }

    public UUID student(String firstName, String lastName) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO students (id, first_name, last_name, status)
                VALUES (?, ?, ?, 'ACTIVE'::student_status)
                """, id, firstName, lastName);
        return id;
    }

    public void link(UUID studentId, UUID guardianId, boolean hasAccess) {
        jdbc.update("""
                INSERT INTO student_guardians (student_id, guardian_id, relationship, is_primary, has_access)
                VALUES (?, ?, 'MOTHER'::guardian_relationship, true, ?)
                """, studentId, guardianId, hasAccess);
    }

    public void sportsProfile(UUID studentId, String coachNotes) {
        jdbc.update("""
                INSERT INTO sports_profiles (student_id, level, dominant_hand, ranking, history, goals, coach_notes)
                VALUES (?, 'Nacional sub-14', 'RIGHT'::dominant_hand, '12', 'Historial', 'Objetivos', ?)
                """, studentId, coachNotes);
    }

    public void housing(UUID studentId, String notes) {
        jdbc.update("""
                INSERT INTO housing_info (student_id, address_line, city, responsible_name, responsible_phone, notes)
                VALUES (?, 'Residencia, hab. 1', 'València', 'Responsable', '+34 600 000 000', ?)
                """, studentId, notes);
    }

    public void emergencyContact(UUID studentId, String name, String notes) {
        jdbc.update("""
                INSERT INTO emergency_contacts (id, student_id, name, relationship, phone, notes, priority)
                VALUES (?, ?, ?, 'Abuela', '+34 600 000 001', ?, 1)
                """, UUID.randomUUID(), studentId, name, notes);
    }

    /** Documento de prueba, sin fichero salvo que el propio test lo suba: id, categoría y estado a elegir. */
    public UUID document(UUID studentId, String category, String status, LocalDate expiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO documents (id, student_id, category, name, status, expires_at)
                VALUES (?, ?, ?::document_category, 'Documento de prueba.pdf', ?::document_status, ?)
                """, id, studentId, category, status, expiresAt == null ? null : Date.valueOf(expiresAt));
        return id;
    }

    /** Estudiante con tutor: devuelve el email de la cuenta del tutor. */
    public Family family(boolean hasAccess) {
        String email = unique("familia") + "@example.com";
        UUID userId = user(email, "GUARDIAN");
        UUID guardianId = guardian(userId, "Tutor", unique("Apellido"));
        UUID studentId = student("Hijo", unique("Apellido"));
        link(studentId, guardianId, hasAccess);
        return new Family(email, guardianId, studentId);
    }

    public record Family(String email, UUID guardianId, UUID studentId) {
    }
}
