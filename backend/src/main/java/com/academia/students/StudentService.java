package com.academia.students;

import com.academia.common.audit.Audited;
import com.academia.common.web.PagedResponse;
import com.academia.guardians.StudentGuardianRepository;
import com.academia.security.AccessService;
import com.academia.security.HideStudentWhenNotVisible;
import com.academia.students.dto.CreateStudentRequest;
import com.academia.students.dto.EducationDto;
import com.academia.students.dto.EducationRequest;
import com.academia.students.dto.HousingDto;
import com.academia.students.dto.HousingRequest;
import com.academia.students.dto.SportsProfileDto;
import com.academia.students.dto.SportsProfileRequest;
import com.academia.students.dto.StudentAdminDto;
import com.academia.students.dto.StudentDetailDto;
import com.academia.students.dto.StudentGuardianDto;
import com.academia.students.dto.StudentListDto;
import com.academia.students.dto.UpdateStudentRequest;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.method.HandleAuthorizationDenied;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ficha del estudiante y sus bloques (docs/diseno-api.md sección 5.3).
 *
 * Toda la autorización está en las anotaciones y en {@link AccessService}: este servicio no
 * pregunta por roles. Las lecturas llevan {@code @HandleAuthorizationDenied}, que convierte
 * en 404 un estudiante ajeno; las escrituras, solo {@code @PreAuthorize}, que da 403.
 */
@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final SportsProfileRepository sportsProfileRepository;
    private final EducationInfoRepository educationInfoRepository;
    private final HousingInfoRepository housingInfoRepository;
    private final EmergencyContactRepository emergencyContactRepository;
    private final StudentGuardianRepository studentGuardianRepository;
    private final AccessService access;

    StudentService(StudentRepository studentRepository, SportsProfileRepository sportsProfileRepository,
            EducationInfoRepository educationInfoRepository, HousingInfoRepository housingInfoRepository,
            EmergencyContactRepository emergencyContactRepository,
            StudentGuardianRepository studentGuardianRepository, AccessService access) {
        this.studentRepository = studentRepository;
        this.sportsProfileRepository = sportsProfileRepository;
        this.educationInfoRepository = educationInfoRepository;
        this.housingInfoRepository = housingInfoRepository;
        this.emergencyContactRepository = emergencyContactRepository;
        this.studentGuardianRepository = studentGuardianRepository;
        this.access = access;
    }

    /**
     * Un único listado para todos los roles (docs/diseno-api.md sección 5.3): la familia no
     * tiene un {@code /my-students}, recibe el mismo endpoint filtrado por
     * {@link AccessService#visibleStudents()}.
     */
    @PreAuthorize("@access.canListStudents()")
    @Transactional(readOnly = true)
    public PagedResponse<StudentListDto> list(StudentStatus status, String q, Pageable pageable) {
        Specification<StudentEntity> spec = access.visibleStudents()
                .and(statusIs(status))
                .and(nameContains(q));
        return PagedResponse.from(studentRepository.findAll(spec, pageable), StudentListDto::from);
    }

    @PreAuthorize("@access.canViewStudent(#id)")
    @HandleAuthorizationDenied(handlerClass = HideStudentWhenNotVisible.class)
    @Transactional(readOnly = true)
    public StudentDetailDto get(UUID id) {
        StudentSheet sheet = loadSheet(id);
        return access.canSeeInternalStudentData()
                ? StudentAdminDto.from(sheet)
                : StudentGuardianDto.from(sheet);
    }

    @PreAuthorize("@access.canCreateStudent()")
    @Audited(action = "STUDENT_CREATED", entity = "STUDENT")
    @Transactional
    public StudentAdminDto create(CreateStudentRequest request) {
        StudentStatus status = request.status() != null ? request.status() : StudentStatus.ACTIVE;
        StudentEntity student = new StudentEntity(request.firstName(), request.lastName(), status);
        student.changeBirthDate(request.birthDate());
        student.changeNationality(request.nationality());
        student.changeEnrolledAt(request.enrolledAt());
        student.changePhone(request.phone());
        student.changeEmail(request.email());
        student.changeAddressLine(request.addressLine());
        student.changeCity(request.city());
        student.changePostalCode(request.postalCode());
        student.changeCountry(request.country());
        studentRepository.saveAndFlush(student);
        return StudentAdminDto.from(loadSheet(student.getId()));
    }

    @PreAuthorize("@access.canEditStudent(#id)")
    @Audited(action = "STUDENT_UPDATED", entity = "STUDENT", studentIdParam = "id")
    @Transactional
    public StudentAdminDto update(UUID id, UpdateStudentRequest request) {
        StudentEntity student = getEntityOrThrow(id);
        if (request.firstName() != null || request.lastName() != null) {
            student.changeName(
                    request.firstName() != null ? request.firstName() : student.getFirstName(),
                    request.lastName() != null ? request.lastName() : student.getLastName());
        }
        if (request.birthDate() != null) {
            student.changeBirthDate(request.birthDate());
        }
        if (request.nationality() != null) {
            student.changeNationality(request.nationality());
        }
        if (request.status() != null) {
            student.changeStatus(request.status());
        }
        if (request.enrolledAt() != null) {
            student.changeEnrolledAt(request.enrolledAt());
        }
        if (request.phone() != null) {
            student.changePhone(request.phone());
        }
        if (request.email() != null) {
            student.changeEmail(request.email());
        }
        if (request.addressLine() != null) {
            student.changeAddressLine(request.addressLine());
        }
        if (request.city() != null) {
            student.changeCity(request.city());
        }
        if (request.postalCode() != null) {
            student.changePostalCode(request.postalCode());
        }
        if (request.country() != null) {
            student.changeCountry(request.country());
        }
        studentRepository.flush();
        return StudentAdminDto.from(loadSheet(id));
    }

    /**
     * Borrado lógico (docs/modelo-datos.md sección 1.3). A partir de aquí la
     * {@code @SQLRestriction} de {@link StudentEntity} lo oculta de toda consulta.
     */
    @PreAuthorize("@access.canEditStudent(#id)")
    @Audited(action = "STUDENT_DELETED", entity = "STUDENT", studentIdParam = "id")
    @Transactional
    public void delete(UUID id) {
        getEntityOrThrow(id).markDeleted(Instant.now());
    }

    @PreAuthorize("@access.canEditStudent(#id)")
    @Audited(action = "STUDENT_SPORTS_PROFILE_UPDATED", entity = "SPORTS_PROFILE", studentIdParam = "id")
    @Transactional
    public SportsProfileDto replaceSportsProfile(UUID id, SportsProfileRequest request) {
        requireExists(id);
        SportsProfileEntity block = sportsProfileRepository.findById(id).orElseGet(() -> new SportsProfileEntity(id));
        block.replace(request.level(), request.dominantHand(), request.ranking(), request.previousClub(),
                request.history(), request.goals(), request.coachNotes());
        return SportsProfileDto.from(sportsProfileRepository.saveAndFlush(block));
    }

    @PreAuthorize("@access.canEditStudent(#id)")
    @Audited(action = "STUDENT_EDUCATION_UPDATED", entity = "EDUCATION_INFO", studentIdParam = "id")
    @Transactional
    public EducationDto replaceEducation(UUID id, EducationRequest request) {
        requireExists(id);
        EducationInfoEntity block = educationInfoRepository.findById(id).orElseGet(() -> new EducationInfoEntity(id));
        block.replace(request.schoolName(), request.grade(), request.scheduleNotes(), request.notes());
        return EducationDto.from(educationInfoRepository.saveAndFlush(block));
    }

    @PreAuthorize("@access.canEditStudent(#id)")
    @Audited(action = "STUDENT_HOUSING_UPDATED", entity = "HOUSING_INFO", studentIdParam = "id")
    @Transactional
    public HousingDto replaceHousing(UUID id, HousingRequest request) {
        requireExists(id);
        HousingInfoEntity block = housingInfoRepository.findById(id).orElseGet(() -> new HousingInfoEntity(id));
        block.replace(request.addressLine(), request.city(), request.responsibleName(), request.responsiblePhone(),
                request.notes());
        return HousingDto.from(housingInfoRepository.saveAndFlush(block));
    }

    private StudentSheet loadSheet(UUID id) {
        StudentEntity student = getEntityOrThrow(id);
        return new StudentSheet(
                student,
                studentGuardianRepository.findWithGuardianByStudentId(id),
                emergencyContactRepository.findByStudentIdOrderByPriorityAscNameAsc(id),
                sportsProfileRepository.findById(id),
                educationInfoRepository.findById(id),
                housingInfoRepository.findById(id));
    }

    private StudentEntity getEntityOrThrow(UUID id) {
        return studentRepository.findById(id).orElseThrow(StudentNotFoundException::new);
    }

    private void requireExists(UUID id) {
        if (!studentRepository.existsById(id)) {
            throw new StudentNotFoundException();
        }
    }

    private static Specification<StudentEntity> statusIs(StudentStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    /** Filtro {@code q} (docs/diseno-api.md sección 8): nombre o apellido, sin distinguir mayúsculas. */
    private static Specification<StudentEntity> nameContains(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("firstName")), pattern, '\\'),
                    cb.like(cb.lower(root.get("lastName")), pattern, '\\'));
        };
    }

    /** Sin esto, un {@code q=%} devolvería todo y {@code q=_} cualquier nombre de una letra o más. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
