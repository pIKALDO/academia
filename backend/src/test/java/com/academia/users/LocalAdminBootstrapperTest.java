package com.academia.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class LocalAdminBootstrapperTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void con_la_base_de_datos_vacia_crea_un_admin_activo_con_las_credenciales_del_entorno() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode("password-del-env")).thenReturn("hash-simulado");
        LocalAdminBootstrapper bootstrapper = new LocalAdminBootstrapper(
                userRepository, passwordEncoder, "admin@academia.local", "password-del-env");

        bootstrapper.run(null);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity admin = captor.getValue();
        assertThat(admin.getEmail()).isEqualTo("admin@academia.local");
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getPasswordHash()).isEqualTo("hash-simulado");
    }

    @Test
    void si_ya_existe_un_admin_no_crea_otro() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(true);
        LocalAdminBootstrapper bootstrapper = new LocalAdminBootstrapper(
                userRepository, passwordEncoder, "admin@academia.local", "password-del-env");

        bootstrapper.run(null);

        verify(userRepository, never()).save(any());
    }
}
