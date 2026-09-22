package com.academia.guardians;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Tutor: la persona, no la cuenta (docs/modelo-datos.md sección 3). {@code userId} es la
 * cuenta de acceso con la que entra en el portal de familias; nulable porque un tutor puede
 * figurar en la ficha sin tener cuenta. Es el enlace que usa la autorización: "qué
 * estudiantes ve este usuario" se resuelve por {@code guardians.user_id}.
 */
@Entity
@Table(name = "guardians")
public class GuardianEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 150)
    private String lastName;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GuardianEntity() {
        // JPA
    }

    GuardianEntity(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    void changeUserId(UUID userId) {
        this.userId = userId;
    }

    void changeFirstName(String firstName) {
        this.firstName = firstName;
    }

    void changeLastName(String lastName) {
        this.lastName = lastName;
    }

    void changePhone(String phone) {
        this.phone = phone;
    }

    void changeEmail(String email) {
        this.email = email;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
