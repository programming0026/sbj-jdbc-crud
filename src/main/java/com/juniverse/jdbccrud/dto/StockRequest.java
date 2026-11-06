package com.juniverse.jdbccrud.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Ajuste de existencias.
 *
 * @param delta cantidad a sumar (negativa para una salida)
 */
public record StockRequest(@NotNull(message = "delta es obligatorio") Integer delta) {
}
