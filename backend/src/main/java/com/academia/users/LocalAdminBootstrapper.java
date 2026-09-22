package com.academia.users;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Con la base de datos vacía no hay forma de entrar: crear usuarios exige ser ADMIN
 * ({@code UserService.create}, {@code @PreAuthorize("@access.canManageUsers()")}), y no hay
 * ningún endpoint para saltarse esa comprobación. Este arranque es la única puerta.
 *
 * <p>Solo corre en el perfil {@code local} —nunca en producción, cuando exista ese perfil— y
 * solo si todavía no hay ningún ADMIN, así que ejecutarlo dos veces (por ejemplo, al reiniciar
 * el backend) no crea duplicados. Las credenciales vienen de variables de entorno
 * ({@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD} en {@code .env}), nunca escritas aquí: sin
 * ellas exportadas, {@code application-local.yml} no puede resolver el placeholder y el
 * arranque falla, en vez de crear un admin con una contraseña fija conocida.
 *
 * <p>Va directo a {@link UserRepository}, no a {@code UserService.create}: ese método deja al
 * usuario en {@code PENDING_ACTIVATION} a la espera de un correo de activación, que no tiene
 * sentido para la primera cuenta con la que se va a entrar por primera vez.
 */
@Component
@Profile("local")
class LocalAdminBootstrapper implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalAdminBootstrapper.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    LocalAdminBootstrapper(UserRepository userRepository, PasswordEncoder passwordEncoder,
            @Value("${app.admin.email}") String adminEmail,
            @Value("${app.admin.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            return;
        }

        UserEntity admin = new UserEntity(adminEmail, UserRole.ADMIN, "Administrador");
        admin.activate(passwordEncoder.encode(adminPassword));
        userRepository.save(admin);

        log.warn("No había ningún ADMIN: creado {} a partir de ADMIN_EMAIL/ADMIN_PASSWORD "
                + "(solo en el perfil local).", adminEmail);
    }
}
