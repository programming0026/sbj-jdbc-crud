package com.juniverse.jdbccrud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CRUD con <b>Spring JDBC</b> y PostgreSQL, sin JPA ni ORM: el SQL se escribe a mano y Spring se encarga de abrir y cerrar
 * conexiones, manejar transacciones, mapear filas a objetos y traducir los errores de la base a excepciones de Spring.
 */
@SpringBootApplication
public class JdbcCrudApplication {

    /**
     * Arranca la aplicación.
     *
     * @param args argumentos de la línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(JdbcCrudApplication.class, args);
    }
}
