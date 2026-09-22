package com.academia.students;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Bloque de alojamiento, 1:1 con {@code students}. Nunca sale al portal de familias (regla no
 * negociable nº5): {@code StudentGuardianDto} no tiene ningún campo donde meterlo.
 */
@Entity
@Table(name = "housing_info")
public class HousingInfoEntity {

    @Id
    @Column(name = "student_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID studentId;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(length = 100)
    private String city;

    @Column(name = "responsible_name", length = 200)
    private String responsibleName;

    @Column(name = "responsible_phone", length = 30)
    private String responsiblePhone;

    @Column(columnDefinition = "text")
    private String notes;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HousingInfoEntity() {
        // JPA
    }

    HousingInfoEntity(UUID studentId) {
        this.studentId = studentId;
    }

    /** PUT: reemplazo total del bloque. */
    void replace(String addressLine, String city, String responsibleName, String responsiblePhone, String notes) {
        this.addressLine = addressLine;
        this.city = city;
        this.responsibleName = responsibleName;
        this.responsiblePhone = responsiblePhone;
        this.notes = notes;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getCity() {
        return city;
    }

    public String getResponsibleName() {
        return responsibleName;
    }

    public String getResponsiblePhone() {
        return responsiblePhone;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
