package com.academia.students;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Contacto de emergencia (docs/modelo-datos.md sección 3). Guarda el {@code studentId} a
 * secas, sin {@code @ManyToOne}: nunca se navega del contacto al estudiante. Como la fila
 * sobrevive al borrado lógico del estudiante, {@code EmergencyContactService} comprueba que
 * el estudiante sigue existiendo antes de operar sobre un contacto suelto.
 */
@Entity
@Table(name = "emergency_contacts")
public class EmergencyContactEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "student_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID studentId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String relationship;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private short priority;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EmergencyContactEntity() {
        // JPA
    }

    EmergencyContactEntity(UUID studentId, String name, String phone, short priority) {
        this.studentId = studentId;
        this.name = name;
        this.phone = phone;
        this.priority = priority;
    }

    void changeName(String name) {
        this.name = name;
    }

    void changeRelationship(String relationship) {
        this.relationship = relationship;
    }

    void changePhone(String phone) {
        this.phone = phone;
    }

    void changeNotes(String notes) {
        this.notes = notes;
    }

    void changePriority(short priority) {
        this.priority = priority;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public String getName() {
        return name;
    }

    public String getRelationship() {
        return relationship;
    }

    public String getPhone() {
        return phone;
    }

    public String getNotes() {
        return notes;
    }

    public short getPriority() {
        return priority;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
