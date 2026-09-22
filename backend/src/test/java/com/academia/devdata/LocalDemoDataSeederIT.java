package com.academia.devdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.academia.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Los tests corren con el perfil por defecto, como correría cualquier entorno que no active
 * {@code local} a propósito: ahí el seed no debe ni registrarse.
 */
class LocalDemoDataSeederIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void fuera_del_perfil_local_el_seed_no_existe_ni_carga_datos() {
        assertThat(context.getEnvironment().getActiveProfiles()).doesNotContain("local");
        assertThat(context.getBeanNamesForType(LocalDemoDataSeeder.class)).isEmpty();

        Integer cuentasDelSeed = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE email LIKE '%@familia.local'", Integer.class);
        assertThat(cuentasDelSeed).isZero();
    }
}
