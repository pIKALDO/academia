package com.academia.guardians;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

/** Clave compuesta de {@code student_guardians}: la pareja es el vínculo (docs/diseno-api.md sección 5.4). */
@Embeddable
public record StudentGuardianId(
        @Column(name = "student_id", columnDefinition = "uuid", nullable = false) UUID studentId,
        @Column(name = "guardian_id", columnDefinition = "uuid", nullable = false) UUID guardianId)
        implements Serializable {
}
