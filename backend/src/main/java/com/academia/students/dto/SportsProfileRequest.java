package com.academia.students.dto;

import com.academia.students.DominantHand;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /students/{id}/sports-profile}: reemplazo total del bloque (docs/diseno-api.md
 * sección 5.3). Un campo ausente queda a {@code null}, no "sin cambios".
 */
public record SportsProfileRequest(
        @Size(max = 50) String level,
        DominantHand dominantHand,
        @Size(max = 50) String ranking,
        @Size(max = 200) String previousClub,
        String history,
        String goals,
        String coachNotes) {
}
