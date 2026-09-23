package com.academia.students.dto;

import com.academia.documents.dto.DocumentsSummaryDto;
import com.academia.guardians.GuardianEntity;
import com.academia.guardians.GuardianRelationship;
import com.academia.guardians.StudentGuardianEntity;
import com.academia.students.DominantHand;
import com.academia.students.EducationInfoEntity;
import com.academia.students.SportsProfileEntity;
import com.academia.students.StudentEntity;
import com.academia.students.StudentSheet;
import com.academia.students.StudentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code GET /students/{id}} con rol GUARDIAN, para su propio hijo (docs/diseno-api.md
 * sección 4.2). Frente a {@link StudentAdminDto} faltan, a propósito:
 *
 * <ul>
 *   <li>{@code coachNotes}, {@code ranking} e {@code history}: {@link SportsProfile} no tiene
 *       esos campos (regla no negociable nº5).</li>
 *   <li>{@code housing}: no existe ningún campo donde ponerlo (regla nº5).</li>
 *   <li>Contacto de los tutores: la plataforma no debe ser el canal por el que un tutor
 *       obtiene el teléfono del otro.</li>
 *   <li>Dirección del estudiante, {@code createdAt}, {@code updatedAt}, {@code enrolledAt}:
 *       metadatos de gestión.</li>
 * </ul>
 *
 * Añadir un campo aquí es la única forma de que llegue a las familias.
 */
public record StudentGuardianDto(
        UUID id,
        String firstName,
        String lastName,
        @Nullable String photoUrl,
        @Nullable LocalDate birthDate,
        @Nullable String nationality,
        StudentStatus status,
        Contact contact,
        List<LinkedGuardian> guardians,
        List<EmergencyContactGuardianDto> emergencyContacts,
        @Nullable SportsProfile sportsProfile,
        @Nullable Education education,
        DocumentsSummaryDto documentsSummary) implements StudentDetailDto {

    @Schema(name = "StudentGuardianContact")
    public record Contact(@Nullable String phone, @Nullable String email) {
    }

    @Schema(name = "StudentGuardianLinkedGuardian")
    public record LinkedGuardian(
            String firstName,
            String lastName,
            GuardianRelationship relationship,
            boolean isPrimary) {

        static LinkedGuardian from(StudentGuardianEntity link) {
            GuardianEntity guardian = link.getGuardian();
            return new LinkedGuardian(guardian.getFirstName(), guardian.getLastName(), link.getRelationship(),
                    link.isPrimary());
        }
    }

    @Schema(name = "StudentGuardianSportsProfile")
    public record SportsProfile(
            @Nullable String level,
            @Nullable DominantHand dominantHand,
            @Nullable String previousClub,
            @Nullable String goals) {

        static SportsProfile from(SportsProfileEntity entity) {
            return new SportsProfile(entity.getLevel(), entity.getDominantHand(), entity.getPreviousClub(),
                    entity.getGoals());
        }
    }

    @Schema(name = "StudentGuardianEducation")
    public record Education(@Nullable String schoolName, @Nullable String grade) {

        static Education from(EducationInfoEntity entity) {
            return new Education(entity.getSchoolName(), entity.getGrade());
        }
    }

    public static StudentGuardianDto from(StudentSheet sheet, @Nullable String photoUrl,
            DocumentsSummaryDto documentsSummary) {
        StudentEntity s = sheet.student();
        return new StudentGuardianDto(
                s.getId(),
                s.getFirstName(),
                s.getLastName(),
                photoUrl,
                s.getBirthDate(),
                s.getNationality(),
                s.getStatus(),
                new Contact(s.getPhone(), s.getEmail()),
                sheet.guardians().stream().map(LinkedGuardian::from).toList(),
                sheet.emergencyContacts().stream().map(EmergencyContactGuardianDto::from).toList(),
                sheet.sportsProfile().map(SportsProfile::from).orElse(null),
                sheet.education().map(Education::from).orElse(null),
                documentsSummary);
    }
}
