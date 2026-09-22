package com.academia.students;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Ficha principal del estudiante (docs/modelo-datos.md sección 3.1). Los bloques
 * (deportivo, académico, alojamiento) son entidades aparte con la clave primaria compartida:
 * aquí solo vive lo que se consulta en cada listado.
 *
 * <p>{@code @SQLRestriction}: el borrado es lógico ({@code deleted_at}), y la restricción
 * hace que ninguna consulta de Hibernate —incluidas las de autorización de
 * {@code StudentAccessRepository}— pueda olvidarse de filtrar los borrados. El precio es que
 * un estudiante borrado deja de existir para todo el mundo, administrador incluido; decisión
 * a revisar en el corte de documentos (docs/PROGRESO.md).
 *
 * <p>No se mapean {@code user_id} (portal del estudiante, fase 2), {@code photo_key} (llega
 * con el almacenamiento, en el corte de documentos) ni {@code left_at} (sin caso de uso en
 * fase 1): {@code ddl-auto: validate} solo comprueba las columnas mapeadas.
 */
@Entity
@Table(name = "students")
@SQLRestriction("deleted_at IS NULL")
public class StudentEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 150)
    private String lastName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 2)
    private String nationality;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(length = 100)
    private String city;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 2)
    private String country;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "student_status")
    private StudentStatus status;

    @Column(name = "enrolled_at")
    private LocalDate enrolledAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudentEntity() {
        // JPA
    }

    public StudentEntity(String firstName, String lastName, StudentStatus status) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
    }

    public void markDeleted(Instant when) {
        this.deletedAt = when;
    }

    public void changeName(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public void changeBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public void changeNationality(String nationality) {
        this.nationality = nationality;
    }

    public void changeStatus(StudentStatus status) {
        this.status = status;
    }

    public void changeEnrolledAt(LocalDate enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    public void changePhone(String phone) {
        this.phone = phone;
    }

    public void changeEmail(String email) {
        this.email = email;
    }

    public void changeAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public void changeCity(String city) {
        this.city = city;
    }

    public void changePostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public void changeCountry(String country) {
        this.country = country;
    }

    public UUID getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getNationality() {
        return nationality;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getCity() {
        return city;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountry() {
        return country;
    }

    public StudentStatus getStatus() {
        return status;
    }

    public LocalDate getEnrolledAt() {
        return enrolledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
