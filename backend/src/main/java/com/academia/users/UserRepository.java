package com.academia.users;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(UserRole role);

    @Query("""
            SELECT u FROM UserEntity u
            WHERE (:role IS NULL OR u.role = :role)
            AND (:status IS NULL OR u.status = :status)
            """)
    Page<UserEntity> search(@Param("role") UserRole role, @Param("status") UserStatus status, Pageable pageable);
}
