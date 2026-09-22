package com.academia.students.dto;

import com.academia.students.DominantHand;
import com.academia.students.SportsProfileEntity;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Bloque deportivo, vista de administrador: ficha completa ({@link StudentAdminDto}) y
 * respuesta de {@code PUT /students/{id}/sports-profile}. Incluye {@code coachNotes}; la
 * familia recibe {@link StudentGuardianDto.SportsProfile}, que no lo tiene.
 */
public record SportsProfileDto(
        @Nullable String level,
        @Nullable DominantHand dominantHand,
        @Nullable String ranking,
        @Nullable String previousClub,
        @Nullable String history,
        @Nullable String goals,
        @Nullable String coachNotes,
        Instant updatedAt) {

    public static SportsProfileDto from(SportsProfileEntity entity) {
        return new SportsProfileDto(entity.getLevel(), entity.getDominantHand(), entity.getRanking(),
                entity.getPreviousClub(), entity.getHistory(), entity.getGoals(), entity.getCoachNotes(),
                entity.getUpdatedAt());
    }
}
