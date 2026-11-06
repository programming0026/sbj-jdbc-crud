package com.juniverse.jdbccrud.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Datos para reemplazar un producto: los mismos campos más la versión que el cliente leyó.
 *
 * @param version versión leída (control optimista)
 * @param product nuevos datos
 */
public record UpdateRequest(@Min(value = 0, message = "La versión es obligatoria") int version, @Valid @NotNull ProductRequest product) {
}
