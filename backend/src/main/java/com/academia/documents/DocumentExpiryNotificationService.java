package com.academia.documents;

import com.academia.guardians.GuardianRecipient;
import com.academia.guardians.StudentGuardianRepository;
import com.academia.students.StudentEntity;
import com.academia.students.StudentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aviso diario de documentos que caducan pronto (docs/modelo-datos.md sección 5). Solo
 * {@code EXPIRY_30D} y {@code EXPIRY_7D}: {@code EXPIRED} ya existe en el enum de la base de
 * datos para un corte futuro, y no se implementa su envío aquí (ver informe de cierre).
 *
 * Idempotencia: se inserta primero en {@code document_notifications} (con
 * {@code ON CONFLICT DO NOTHING}, sobre la restricción única
 * {@code (document_id, kind, recipient_email)}) y solo si la inserción afecta una fila se envía
 * el correo. Nunca al revés: un correo enviado y no registrado se repetiría en la siguiente
 * ejecución; un registro sin correo enviado, en el peor caso, se pierde un aviso, que es
 * preferible a duplicarlo.
 */
@Service
class DocumentExpiryNotificationService {

    private static final int[] HORIZON_DAYS = {30, 7};

    private final DocumentRepository documentRepository;
    private final StudentRepository studentRepository;
    private final StudentGuardianRepository studentGuardianRepository;
    private final DocumentExpiryMailService mailService;
    private final JdbcTemplate jdbc;

    DocumentExpiryNotificationService(DocumentRepository documentRepository, StudentRepository studentRepository,
            StudentGuardianRepository studentGuardianRepository, DocumentExpiryMailService mailService,
            JdbcTemplate jdbc) {
        this.documentRepository = documentRepository;
        this.studentRepository = studentRepository;
        this.studentGuardianRepository = studentGuardianRepository;
        this.mailService = mailService;
        this.jdbc = jdbc;
    }

    /** Diario a las 06:00 (hora del servidor). Público para que el test lo invoque directamente. */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void sendExpiryNotifications() {
        LocalDate today = LocalDate.now();
        sendForHorizon(today, 30, "EXPIRY_30D");
        sendForHorizon(today, 7, "EXPIRY_7D");
    }

    private void sendForHorizon(LocalDate today, int days, String kind) {
        LocalDate horizon = today.plusDays(days);
        for (DocumentEntity document : documentRepository.findByExpiresAtAndDeletedAtIsNull(horizon)) {
            // Un documento de un estudiante ya borrado no debe generar avisos nuevos.
            if (!studentRepository.existsById(document.getStudentId())) {
                continue;
            }
            StudentEntity student = studentRepository.findById(document.getStudentId()).orElse(null);
            if (student == null) {
                continue;
            }
            String studentName = student.getFirstName() + " " + student.getLastName();
            List<GuardianRecipient> recipients =
                    studentGuardianRepository.findAccessibleGuardianRecipients(document.getStudentId());
            for (GuardianRecipient recipient : recipients) {
                notifyOnce(document, kind, recipient, studentName, today);
            }
        }
    }

    private void notifyOnce(DocumentEntity document, String kind, GuardianRecipient recipient, String studentName,
            LocalDate today) {
        int inserted = jdbc.update("""
                INSERT INTO document_notifications (id, document_id, kind, recipient_email)
                VALUES (?, ?, ?::notification_kind, ?)
                ON CONFLICT (document_id, kind, recipient_email) DO NOTHING
                """, newId(), document.getId(), kind, recipient.email());
        if (inserted == 1) {
            long daysUntilExpiry = document.daysUntilExpiry(today);
            mailService.sendExpiryNotice(recipient.email(), recipient.firstName(), studentName,
                    document.getStudentId(), document.getCategory(), document.getName(), document.getExpiresAt(),
                    daysUntilExpiry);
        }
    }

    /** Mismo generador UUID v7 que {@code LocalDemoDataSeeder}: SQL directo, sin entidad JPA. */
    private static UUID newId() {
        return UuidVersion7Strategy.INSTANCE.generateUuid(null);
    }
}
