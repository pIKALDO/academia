package com.academia.documents;

/**
 * Espejo del tipo {@code document_status} de PostgreSQL (V4__documents.sql). No incluye
 * "caducado" a propósito (regla no negociable nº11): es flujo de trabajo, no calendario.
 */
public enum DocumentStatus {
    PENDING,
    RECEIVED,
    REVIEWED
}
