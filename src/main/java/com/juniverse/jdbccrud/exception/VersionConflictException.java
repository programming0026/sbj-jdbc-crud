package com.juniverse.jdbccrud.exception;

/** Alguien modificó el producto desde que se leyó (409): hay que volver a leerlo y reintentar. */
public class VersionConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción.
     *
     * @param id producto en conflicto
     */
    public VersionConflictException(long id) {
        super("El producto " + id + " fue modificado por otra persona; vuelve a cargarlo e inténtalo de nuevo");
    }
}
