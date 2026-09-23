package com.academia.config;

import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

/**
 * Envoltorio del almacenamiento S3-compatible, usado por {@code documents} y {@code students}
 * (fotografía del estudiante). Clave de almacenamiento siempre {@code students/{studentId}/{uuid}}
 * (regla no negociable nº7): nunca el nombre original del fichero.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    StorageService(S3Client s3Client, S3Presigner s3Presigner, StorageProperties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    public void upload(String key, byte[] content, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
    }

    /** URL prefirmada de descarga (docs/diseno-api.md sección 5.6), válida durante {@code duration}. */
    public URI presignedGetUrl(String key, Duration duration) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(duration.truncatedTo(ChronoUnit.SECONDS))
                .getObjectRequest(getObjectRequest)
                .build();
        return URI.create(s3Presigner.presignGetObject(presignRequest).url().toString());
    }

    /**
     * Borrado best-effort: se usa para limpiar el objeto anterior al reemplazar una foto de
     * estudiante. Un fallo de almacenamiento no debe tumbar la petición HTTP que lo originó
     * (la foto nueva ya se guardó y el estudiante ya apunta a ella); se registra y se sigue.
     */
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
        } catch (S3Exception e) {
            log.warn("No se pudo borrar el objeto '{}' del almacenamiento.", key, e);
        }
    }
}
