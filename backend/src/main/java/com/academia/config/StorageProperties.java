package com.academia.config;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del almacenamiento S3-compatible (docs/modelo-datos.md sección 5.3): MinIO en
 * desarrollo local, Cloudflare R2 en servidor. Un único conjunto de propiedades para los dos,
 * sin ninguna rama de código que distinga el entorno: lo único que cambia es el valor de estos
 * campos.
 *
 * <ul>
 *   <li>{@code endpoint}/{@code pathStyleAccess}: MinIO exige {@code endpointOverride} explícito
 *       y estilo de ruta ({@code http://host:9000/bucket/clave}); R2 usa su propio endpoint y
 *       estilo "virtual-hosted" ({@code https://bucket.<cuenta>.r2.cloudflarestorage.com/clave}).</li>
 *   <li>{@code region}: MinIO la ignora, pero el SDK exige un valor no nulo.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @Nullable String endpoint,
        String region,
        String bucket,
        String accessKeyId,
        String secretAccessKey,
        boolean pathStyleAccess,
        Duration presignedUrlDuration) {
}
