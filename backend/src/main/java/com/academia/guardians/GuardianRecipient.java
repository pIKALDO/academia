package com.academia.guardians;

/** Fila mínima para el correo de aviso de caducidad de documentos: a quién y con qué nombre saludarlo. */
public record GuardianRecipient(String email, String firstName) {
}
