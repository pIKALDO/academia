package com.academia.students.dto;

import com.academia.guardians.GuardianEntity;
import com.academia.guardians.GuardianRelationship;
import com.academia.guardians.StudentGuardianEntity;
import com.academia.students.StudentEntity;
import com.academia.students.StudentSheet;
import com.academia.students.StudentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code GET /students/{id}} con rol ADMIN (docs/diseno-api.md sección 4.1): la ficha
 * completa, con {@code coachNotes}, {@code housing}, el contacto de cada tutor y los
 * metadatos de gestión. Los bloques que aún no se han rellenado salen a {@code null}.
 *
 * Los records anidados llevan {@code @Schema(name)} propio: springdoc nombra los esquemas por
 * el nombre simple de la clase, y el {@code Contact} de esta vista y el de la de familia se
 * pisarían en {@code docs/openapi.json}.
 */
public record StudentAdminDto(
        UUID id,
        String firstName,
        String lastName,
        @Nullable LocalDate birthDate,
        @Nullable String nationality,
        StudentStatus status,
        @Nullable LocalDate enrolledAt,
        Contact contact,
        List<LinkedGuardian> guardians,
        List<EmergencyContactAdminDto> emergencyContacts,
        @Nullable SportsProfileDto sportsProfile,
        @Nullable EducationDto education,
        @Nullable HousingDto housing,
        Instant createdAt,
        Instant updatedAt) implements StudentDetailDto {

    @Schema(name = "StudentAdminContact")
    public record Contact(@Nullable String phone, @Nullable String email, Address address) {
    }

    @Schema(name = "StudentAdminAddress")
    public record Address(
            @Nullable String line,
            @Nullable String city,
            @Nullable String postalCode,
            @Nullable String country) {
    }

    @Schema(name = "StudentAdminLinkedGuardian")
    public record LinkedGuardian(
            UUID id,
            String firstName,
            String lastName,
            GuardianRelationship relationship,
            boolean isPrimary,
            boolean hasAccess,
            @Nullable String phone,
            @Nullable String email) {

        static LinkedGuardian from(StudentGuardianEntity link) {
            GuardianEntity guardian = link.getGuardian();
            return new LinkedGuardian(guardian.getId(), guardian.getFirstName(), guardian.getLastName(),
                    link.getRelationship(), link.isPrimary(), link.hasAccess(), guardian.getPhone(),
                    guardian.getEmail());
        }
    }

    public static StudentAdminDto from(StudentSheet sheet) {
        StudentEntity s = sheet.student();
        return new StudentAdminDto(
                s.getId(),
                s.getFirstName(),
                s.getLastName(),
                s.getBirthDate(),
                s.getNationality(),
                s.getStatus(),
                s.getEnrolledAt(),
                new Contact(s.getPhone(), s.getEmail(),
                        new Address(s.getAddressLine(), s.getCity(), s.getPostalCode(), s.getCountry())),
                sheet.guardians().stream().map(LinkedGuardian::from).toList(),
                sheet.emergencyContacts().stream().map(EmergencyContactAdminDto::from).toList(),
                sheet.sportsProfile().map(SportsProfileDto::from).orElse(null),
                sheet.education().map(EducationDto::from).orElse(null),
                sheet.housing().map(HousingDto::from).orElse(null),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }
}
