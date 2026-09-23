package com.academia.students;

import com.academia.common.web.PageRequestFactory;
import com.academia.common.web.PagedResponse;
import com.academia.students.dto.CreateStudentRequest;
import com.academia.students.dto.EducationDto;
import com.academia.students.dto.EducationRequest;
import com.academia.students.dto.HousingDto;
import com.academia.students.dto.HousingRequest;
import com.academia.students.dto.SportsProfileDto;
import com.academia.students.dto.SportsProfileRequest;
import com.academia.students.dto.StudentAdminDto;
import com.academia.students.dto.StudentDetailDto;
import com.academia.students.dto.StudentListDto;
import com.academia.students.dto.UpdateStudentRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Estudiantes y bloques de ficha (docs/diseno-api.md sección 5.3). Sin decisiones de permisos:
 * quién ve qué, y en qué representación, lo resuelve {@link StudentService} con
 * {@code AccessService} (regla no negociable nº2).
 */
@Tag(name = "Estudiantes")
@RestController
@RequestMapping("/api/v1/students")
class StudentController {

    private final StudentService studentService;

    StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping
    PagedResponse<StudentListDto> listStudents(
            @RequestParam(required = false) StudentStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequestFactory.of(page, size, Sort.by("lastName", "firstName").ascending());
        return studentService.list(status, q, pageable);
    }

    @PostMapping
    ResponseEntity<StudentAdminDto> createStudent(@Valid @RequestBody CreateStudentRequest request,
            UriComponentsBuilder uriBuilder) {
        StudentAdminDto created = studentService.create(request);
        // El UriComponentsBuilder que inyecta Spring MVC parte del servlet mapping, no de la URL
        // de esta petición: solo trae esquema, host y puerto. La ruta va completa.
        URI location = uriBuilder.path("/api/v1/students/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    StudentDetailDto getStudent(@PathVariable UUID id) {
        return studentService.get(id);
    }

    @PatchMapping("/{id}")
    StudentAdminDto updateStudent(@PathVariable UUID id, @Valid @RequestBody UpdateStudentRequest request) {
        return studentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteStudent(@PathVariable UUID id) {
        studentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/sports-profile")
    SportsProfileDto replaceSportsProfile(@PathVariable UUID id, @Valid @RequestBody SportsProfileRequest request) {
        return studentService.replaceSportsProfile(id, request);
    }

    @PutMapping("/{id}/education")
    EducationDto replaceEducation(@PathVariable UUID id, @Valid @RequestBody EducationRequest request) {
        return studentService.replaceEducation(id, request);
    }

    @PutMapping("/{id}/housing")
    HousingDto replaceHousing(@PathVariable UUID id, @Valid @RequestBody HousingRequest request) {
        return studentService.replaceHousing(id, request);
    }

    @PutMapping(path = "/{id}/photo", consumes = "multipart/form-data")
    StudentAdminDto replacePhoto(@PathVariable UUID id, @RequestPart MultipartFile file) {
        return studentService.replacePhoto(id, file);
    }
}
