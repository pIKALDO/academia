package com.academia.documents;

import com.academia.documents.dto.DocumentsSummaryDto;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Pública porque {@code StudentService} necesita {@link #summarize} y
 * {@link #summarizeByStudentIds} para {@code documentsSummary}, y
 * {@code security.JpaDocumentAccessRepository} necesita {@link #findStudentIdByIdAndDeletedAtIsNull}.
 * Sin {@code @SQLRestriction} en {@link DocumentEntity}: el filtro {@code deleted_at IS NULL} va
 * explícito en cada consulta (decisión registrada en docs/PROGRESO.md).
 */
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID>, JpaSpecificationExecutor<DocumentEntity> {

    Page<DocumentEntity> findByStudentIdAndDeletedAtIsNull(UUID studentId, Pageable pageable);

    Optional<UUID> findStudentIdByIdAndDeletedAtIsNull(UUID id);

    /** Documentos que cumplen años/días exactos hasta caducidad, usados por el aviso diario. */
    List<DocumentEntity> findByExpiresAtAndDeletedAtIsNull(LocalDate expiresAt);

    /**
     * Una sola consulta de agregación en vez de cuatro {@code COUNT} separados. Se devuelve
     * como fila cruda ({@code Object[]}: total, pending, expiringSoon, expired, todos
     * {@code Long}) porque una expresión constructora JPQL exige que los tipos casen
     * exactamente con el record, y {@link DocumentsSummaryDto} usa {@code int}.
     */
    @Query("""
            SELECT COUNT(d),
                SUM(CASE WHEN d.status = :pending THEN 1 ELSE 0 END),
                SUM(CASE WHEN d.expiresAt IS NOT NULL AND d.expiresAt >= :today AND d.expiresAt <= :horizon THEN 1 ELSE 0 END),
                SUM(CASE WHEN d.expiresAt IS NOT NULL AND d.expiresAt < :today THEN 1 ELSE 0 END)
            FROM DocumentEntity d
            WHERE d.studentId = :studentId AND d.deletedAt IS NULL
            """)
    List<Object[]> summarizeRow(@Param("studentId") UUID studentId, @Param("today") LocalDate today,
            @Param("horizon") LocalDate horizon, @Param("pending") DocumentStatus pending);

    default DocumentsSummaryDto summarize(UUID studentId, LocalDate today, LocalDate horizon) {
        List<Object[]> rows = summarizeRow(studentId, today, horizon, DocumentStatus.PENDING);
        return rows.isEmpty() ? DocumentsSummaryDto.EMPTY : toSummary(rows.get(0));
    }

    /** Fila cruda por estudiante: {@code studentId}, total, pending, expiringSoon, expired. */
    @Query("""
            SELECT d.studentId,
                COUNT(d),
                SUM(CASE WHEN d.status = :pending THEN 1 ELSE 0 END),
                SUM(CASE WHEN d.expiresAt IS NOT NULL AND d.expiresAt >= :today AND d.expiresAt <= :horizon THEN 1 ELSE 0 END),
                SUM(CASE WHEN d.expiresAt IS NOT NULL AND d.expiresAt < :today THEN 1 ELSE 0 END)
            FROM DocumentEntity d
            WHERE d.studentId IN :studentIds AND d.deletedAt IS NULL
            GROUP BY d.studentId
            """)
    List<Object[]> summarizeByStudentIdsRows(@Param("studentIds") Collection<UUID> studentIds,
            @Param("today") LocalDate today, @Param("horizon") LocalDate horizon,
            @Param("pending") DocumentStatus pending);

    /** Evita el N+1 en {@code GET /students}: una sola consulta agrupada por página. */
    default Map<UUID, DocumentsSummaryDto> summarizeByStudentIds(Collection<UUID> studentIds, LocalDate today,
            LocalDate horizon) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, DocumentsSummaryDto> result = new HashMap<>();
        for (Object[] row : summarizeByStudentIdsRows(studentIds, today, horizon, DocumentStatus.PENDING)) {
            result.put((UUID) row[0], toSummary(new Object[] {row[1], row[2], row[3], row[4]}));
        }
        return result;
    }

    private static DocumentsSummaryDto toSummary(Object[] row) {
        return new DocumentsSummaryDto(toInt(row[0]), toInt(row[1]), toInt(row[2]), toInt(row[3]));
    }

    private static int toInt(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }
}
