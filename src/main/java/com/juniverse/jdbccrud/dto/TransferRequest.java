package com.juniverse.jdbccrud.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Movimiento de existencias entre dos productos.
 *
 * @param fromId origen
 * @param toId destino
 * @param quantity unidades
 */
public record TransferRequest(@NotNull Long fromId, @NotNull Long toId, @Min(value = 1, message = "La cantidad mínima es 1") int quantity) {
}
