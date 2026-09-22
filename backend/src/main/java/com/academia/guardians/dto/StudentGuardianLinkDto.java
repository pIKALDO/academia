package com.academia.guardians.dto;

import com.academia.guardians.GuardianRelationship;
import com.academia.guardians.StudentGuardianEntity;
import java.time.Instant;
import java.util.UUID;

/** Respuesta de {@code PUT /students/{sid}/guardians/{gid}}: el vínculo tal y como ha quedado. */
public record StudentGuardianLinkDto(
        UUID studentId,
        UUID guardianId,
        GuardianRelationship relationship,
        boolean isPrimary,
        boolean hasAccess,
        Instant createdAt) {

    public static StudentGuardianLinkDto from(StudentGuardianEntity link) {
        return new StudentGuardianLinkDto(link.getStudentId(), link.getGuardian().getId(), link.getRelationship(),
                link.isPrimary(), link.hasAccess(), link.getCreatedAt());
    }
}
