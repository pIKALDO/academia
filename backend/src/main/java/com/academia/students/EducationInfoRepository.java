package com.academia.students;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EducationInfoRepository extends JpaRepository<EducationInfoEntity, UUID> {
}
