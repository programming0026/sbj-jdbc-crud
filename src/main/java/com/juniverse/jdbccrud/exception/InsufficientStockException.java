package com.juniverse.jdbccrud.exception;

/** No hay existencias suficientes para la salida pedida (409). */
public class InsufficientStockException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción.
     *
     * @param id producto
     * @param requested unidades pedidas
     */
    public InsufficientStockException(long id, int requested) {
        super("El producto " + id + " no tiene " + requested + " unidades disponibles");
    }
}
