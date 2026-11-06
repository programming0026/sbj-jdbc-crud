package com.juniverse.jdbccrud.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Lote de productos a importar.
 *
 * @param products productos
 */
public record ImportRequest(@NotEmpty(message = "Envía al menos un producto") List<@Valid ProductRequest> products) {
}
