package com.academia.guardians;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface GuardianRepository extends JpaRepository<GuardianEntity, UUID> {

    boolean existsByUserId(UUID userId);
}
