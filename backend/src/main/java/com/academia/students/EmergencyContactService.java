package com.academia.students;

import com.academia.common.audit.Audited;
import com.academia.common.error.NotFoundException;
import com.academia.common.web.PagedResponse;
import com.academia.security.AccessService;
import com.academia.security.HideStudentWhenNotVisible;
import com.academia.students.dto.CreateEmergencyContactRequest;
import com.academia.students.dto.EmergencyContactAdminDto;
import com.academia.students.dto.EmergencyContactDto;
import com.academia.students.dto.EmergencyContactGuardianDto;
import com.academia.students.dto.UpdateEmergencyContactRequest;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.method.HandleAuthorizationDenied;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contactos de emergencia (docs/diseno-api.md sección 5.5). Anidados bajo el estudiante para
 * listar y crear; planos ({@code /emergency-contacts/{id}}) para modificar y borrar (sección
 * 1.2).
 */
@Service
public class EmergencyContactService {

    private final EmergencyContactRepository emergencyContactRepository;
    private final StudentRepository studentRepository;
    private final AccessService access;

    EmergencyContactService(EmergencyContactRepository emergencyContactRepository,
            StudentRepository studentRepository, AccessService access) {
        this.emergencyContactRepository = emergencyContactRepository;
        this.studentRepository = studentRepository;
        this.access = access;
    }

    /** Paginado aunque sean dos o tres por estudiante (regla no negociable nº9). */
    @PreAuthorize("@access.canViewStudent(#studentId)")
    @HandleAuthorizationDenied(handlerClass = HideStudentWhenNotVisible.class)
    @Transactional(readOnly = true)
    public PagedResponse<EmergencyContactDto> list(UUID studentId, Pageable pageable) {
        requireStudent(studentId);
        Function<EmergencyContactEntity, EmergencyContactDto> mapper = access.canSeeInternalStudentData()
                ? EmergencyContactAdminDto::from
                : EmergencyContactGuardianDto::from;
        return PagedResponse.from(emergencyContactRepository.findByStudentId(studentId, pageable), mapper);
    }

    @PreAuthorize("@access.canEditStudent(#studentId)")
    @Audited(action = "EMERGENCY_CONTACT_CREATED", entity = "EMERGENCY_CONTACT", studentIdParam = "studentId")
    @Transactional
    public EmergencyContactAdminDto create(UUID studentId, CreateEmergencyContactRequest request) {
        requireStudent(studentId);
        short priority = request.priority() != null ? request.priority().shortValue() : 1;
        EmergencyContactEntity contact = new EmergencyContactEntity(studentId, request.name(), request.phone(), priority);
        contact.changeRelationship(request.relationship());
        contact.changeNotes(request.notes());
        return EmergencyContactAdminDto.from(emergencyContactRepository.save(contact));
    }

    @PreAuthorize("@access.canEditEmergencyContact(#id)")
    @Audited(action = "EMERGENCY_CONTACT_UPDATED", entity = "EMERGENCY_CONTACT")
    @Transactional
    public EmergencyContactAdminDto update(UUID id, UpdateEmergencyContactRequest request) {
        EmergencyContactEntity contact = getEntityOrThrow(id);
        if (request.name() != null) {
            contact.changeName(request.name());
        }
        if (request.relationship() != null) {
            contact.changeRelationship(request.relationship());
        }
        if (request.phone() != null) {
            contact.changePhone(request.phone());
        }
        if (request.notes() != null) {
            contact.changeNotes(request.notes());
        }
        if (request.priority() != null) {
            contact.changePriority(request.priority().shortValue());
        }
        return EmergencyContactAdminDto.from(contact);
    }

    @PreAuthorize("@access.canEditEmergencyContact(#id)")
    @Audited(action = "EMERGENCY_CONTACT_DELETED", entity = "EMERGENCY_CONTACT")
    @Transactional
    public void delete(UUID id) {
        emergencyContactRepository.delete(getEntityOrThrow(id));
    }

    /**
     * La fila del contacto sobrevive al borrado lógico de su estudiante: sin comprobar que el
     * estudiante sigue existiendo, se podría seguir editando la ficha de un borrado.
     */
    private EmergencyContactEntity getEntityOrThrow(UUID id) {
        return emergencyContactRepository.findById(id)
                .filter(contact -> studentRepository.existsById(contact.getStudentId()))
                .orElseThrow(() -> new NotFoundException("Contacto de emergencia no encontrado."));
    }

    private void requireStudent(UUID studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new StudentNotFoundException();
        }
    }
}
