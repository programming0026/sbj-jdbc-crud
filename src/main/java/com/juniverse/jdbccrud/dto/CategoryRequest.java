package com.juniverse.jdbccrud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear una categoría.
 *
 * @param name nombre
 */
public record CategoryRequest(@NotBlank(message = "El nombre es obligatorio") @Size(max = 80, message = "El nombre no puede superar los 80 caracteres") String name) {
}
