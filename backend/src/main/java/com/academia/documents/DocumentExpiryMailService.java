package com.academia.documents;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Correo de aviso de caducidad de documentación (corte 3), mismo patrón que
 * {@code users.AuthMailService}: plantilla Thymeleaf, y un fallo de envío se registra sin
 * tumbar el proceso que lo originó —aquí, el programador nocturno de avisos— porque el registro
 * de idempotencia ya se insertó antes de intentar el envío
 * ({@link DocumentExpiryNotificationService}).
 */
@Component
class DocumentExpiryMailService {

    private static final Logger log = LoggerFactory.getLogger(DocumentExpiryMailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String frontendBaseUrl;
    private final String fromAddress;

    DocumentExpiryMailService(JavaMailSender mailSender, TemplateEngine templateEngine,
            @Value("${app.frontend-base-url}") String frontendBaseUrl,
            @Value("${app.mail.from-address}") String fromAddress) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.frontendBaseUrl = frontendBaseUrl;
        this.fromAddress = fromAddress;
    }

    void sendExpiryNotice(String recipientEmail, String guardianName, String studentName, java.util.UUID studentId,
            DocumentCategory category, String documentName, LocalDate expiresAt, long daysUntilExpiry) {
        Context context = new Context();
        context.setVariable("guardianName", guardianName);
        context.setVariable("studentName", studentName);
        context.setVariable("documentCategory", category);
        context.setVariable("documentName", documentName);
        context.setVariable("expiresAt", expiresAt);
        context.setVariable("daysUntilExpiry", daysUntilExpiry);
        // La ruta del front es "/students/:id" (frontend/src/app/app.routes.ts): no existe
        // todavía "/estudiantes/{id}" en español, así que el enlace usa la ruta real.
        context.setVariable("actionUrl", frontendBaseUrl + "/students/" + studentId);
        String html = templateEngine.process("mail/document-expiry", context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(recipientEmail);
            helper.setFrom(fromAddress);
            helper.setSubject("Un documento de " + studentName + " caduca pronto");
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            log.error("No se pudo enviar el aviso de caducidad a un destinatario.", e);
        }
    }
}
