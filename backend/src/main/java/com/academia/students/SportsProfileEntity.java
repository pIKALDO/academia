package com.academia.students;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Bloque deportivo, 1:1 con {@code students} por clave primaria compartida
 * (docs/modelo-datos.md sección 4). {@code coachNotes} nunca sale al portal de familias
 * (regla no negociable nº5): solo lo lee {@code SportsProfileDto}, la vista de administrador.
 *
 * <p>Se identifica por {@code studentId} asignado a mano, no generado. Sin relación
 * {@code @OneToOne} con {@link StudentEntity}: el bloque se lee por separado cuando se pide la
 * ficha completa y nunca desde el listado, así que la asociación no aportaría nada.
 */
@Entity
@Table(name = "sports_profiles")
public class SportsProfileEntity {

    @Id
    @Column(name = "student_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID studentId;

    @Column(length = 50)
    private String level;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "dominant_hand", columnDefinition = "dominant_hand")
    private DominantHand dominantHand;

    @Column(length = 50)
    private String ranking;

    @Column(name = "previous_club", length = 200)
    private String previousClub;

    @Column(columnDefinition = "text")
    private String history;

    @Column(columnDefinition = "text")
    private String goals;

    @Column(name = "coach_notes", columnDefinition = "text")
    private String coachNotes;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SportsProfileEntity() {
        // JPA
    }

    SportsProfileEntity(UUID studentId) {
        this.studentId = studentId;
    }

    /** PUT: reemplazo total del bloque; un campo ausente en la petición queda a null. */
    void replace(String level, DominantHand dominantHand, String ranking, String previousClub, String history,
            String goals, String coachNotes) {
        this.level = level;
        this.dominantHand = dominantHand;
        this.ranking = ranking;
        this.previousClub = previousClub;
        this.history = history;
        this.goals = goals;
        this.coachNotes = coachNotes;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public String getLevel() {
        return level;
    }

    public DominantHand getDominantHand() {
        return dominantHand;
    }

    public String getRanking() {
        return ranking;
    }

    public String getPreviousClub() {
        return previousClub;
    }

    public String getHistory() {
        return history;
    }

    public String getGoals() {
        return goals;
    }

    public String getCoachNotes() {
        return coachNotes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
