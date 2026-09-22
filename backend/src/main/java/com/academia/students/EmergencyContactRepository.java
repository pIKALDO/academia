package com.academia.students;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface EmergencyContactRepository extends JpaRepository<EmergencyContactEntity, UUID> {

    Page<EmergencyContactEntity> findByStudentId(UUID studentId, Pageable pageable);

    List<EmergencyContactEntity> findByStudentIdOrderByPriorityAscNameAsc(UUID studentId);
}
