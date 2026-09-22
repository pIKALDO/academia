package com.academia.users;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
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
 * Correos de activación y recuperación de contraseña, con plantilla Thymeleaf
 * (CLAUDE.md, corte 1). En desarrollo local los captura Mailpit
 * (docker-compose.yml); no hay perfil de servidor todavía.
 *
 * Un fallo de envío no debe tumbar la petición HTTP que lo originó (crear un usuario, pedir
 * una recuperación de contraseña): el token ya quedó persistido, así que un reenvío o un
 * administrador con acceso directo al correo capturado siguen funcionando. Se registra el
 * error y se sigue.
 */
@Component
class AuthMailService {

    private static final Logger log = LoggerFactory.getLogger(AuthMailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String frontendBaseUrl;
    private final String fromAddress;

    AuthMailService(JavaMailSender mailSender, TemplateEngine templateEngine,
            @Value("${app.frontend-base-url}") String frontendBaseUrl,
            @Value("${app.mail.from-address}") String fromAddress) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.frontendBaseUrl = frontendBaseUrl;
        this.fromAddress = fromAddress;
    }

    void sendActivationEmail(UserEntity user, String rawToken) {
        String activationUrl = frontendBaseUrl + "/activar-cuenta?token=" + rawToken;
        send(user.getEmail(), "Activa tu cuenta en la academia", "mail/activation",
                user.getDisplayName(), activationUrl);
    }

    void sendPasswordResetEmail(UserEntity user, String rawToken) {
        String resetUrl = frontendBaseUrl + "/restablecer-contrasena?token=" + rawToken;
        send(user.getEmail(), "Recupera tu contraseña", "mail/password-reset",
                user.getDisplayName(), resetUrl);
    }

    private void send(String to, String subject, String template, String displayName, String actionUrl) {
        Context context = new Context();
        context.setVariable("displayName", displayName);
        context.setVariable("actionUrl", actionUrl);
        String html = templateEngine.process(template, context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setFrom(fromAddress);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            log.error("No se pudo enviar el correo '{}' a un destinatario.", subject, e);
        }
    }
}
