package com.academia.students;

import com.academia.guardians.StudentGuardianEntity;
import java.util.List;
import java.util.Optional;

/**
 * La ficha completa leída de base de datos, antes de decidir qué sale a cada rol. Es la
 * entrada común de {@code StudentAdminDto.from} y {@code StudentGuardianDto.from}: los dos
 * mapeos leen del mismo sitio, y la única diferencia entre ellos es qué campos copia cada uno.
 * Nunca se serializa.
 */
public record StudentSheet(
        StudentEntity student,
        List<StudentGuardianEntity> guardians,
        List<EmergencyContactEntity> emergencyContacts,
        Optional<SportsProfileEntity> sportsProfile,
        Optional<EducationInfoEntity> education,
        Optional<HousingInfoEntity> housing) {
}
