package com.academia.students;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;

/** Bloque académico, 1:1 con {@code students}. Mismo patrón que {@link SportsProfileEntity}. */
@Entity
@Table(name = "education_info")
public class EducationInfoEntity {

    @Id
    @Column(name = "student_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID studentId;

    @Column(name = "school_name", length = 200)
    private String schoolName;

    @Column(length = 100)
    private String grade;

    @Column(name = "schedule_notes", columnDefinition = "text")
    private String scheduleNotes;

    @Column(columnDefinition = "text")
    private String notes;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EducationInfoEntity() {
        // JPA
    }

    EducationInfoEntity(UUID studentId) {
        this.studentId = studentId;
    }

    /** PUT: reemplazo total del bloque. */
    void replace(String schoolName, String grade, String scheduleNotes, String notes) {
        this.schoolName = schoolName;
        this.grade = grade;
        this.scheduleNotes = scheduleNotes;
        this.notes = notes;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public String getSchoolName() {
        return schoolName;
    }

    public String getGrade() {
        return grade;
    }

    public String getScheduleNotes() {
        return scheduleNotes;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
