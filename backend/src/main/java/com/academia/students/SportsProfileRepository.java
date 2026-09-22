package com.academia.students;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SportsProfileRepository extends JpaRepository<SportsProfileEntity, UUID> {
}
