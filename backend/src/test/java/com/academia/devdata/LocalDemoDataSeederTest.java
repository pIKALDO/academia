package com.academia.devdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class LocalDemoDataSeederTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void solo_se_registra_en_el_perfil_local() {
        Profile profile = LocalDemoDataSeeder.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("local");
    }

    @Test
    void sin_contrasena_de_familias_no_toca_la_base_de_datos() {
        new LocalDemoDataSeeder(jdbc, passwordEncoder, "").run(null);

        verifyNoInteractions(jdbc, passwordEncoder);
    }

    @Test
    void si_ya_hay_estudiantes_no_inserta_nada() {
        when(jdbc.queryForObject("SELECT count(*) FROM students", Integer.class)).thenReturn(3);

        new LocalDemoDataSeeder(jdbc, passwordEncoder, "familia-dev").run(null);

        verify(jdbc).queryForObject("SELECT count(*) FROM students", Integer.class);
        verifyNoMoreInteractions(jdbc);
        verifyNoInteractions(passwordEncoder);
    }
}
