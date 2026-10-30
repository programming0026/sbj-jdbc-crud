package com.juniverse.jdbccrud.repository;

import com.juniverse.jdbccrud.model.Category;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

/**
 * Categorías. Para insertar usa {@link SimpleJdbcInsert}: Spring arma el INSERT a partir del nombre de la tabla y de las
 * columnas, y recupera la clave generada por la base sin escribir SQL.
 */
@Repository
public class CategoryRepository {

    private final JdbcClient jdbc;
    private final SimpleJdbcInsert insert;

    /**
     * Crea el repositorio.
     *
     * @param jdbc cliente JDBC
     * @param dataSource origen de datos (lo necesita SimpleJdbcInsert)
     */
    public CategoryRepository(JdbcClient jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.insert = new SimpleJdbcInsert(dataSource).withTableName("categories").usingGeneratedKeyColumns("id");
    }

    /**
     * Inserta una categoría.
     *
     * @param name nombre único
     * @return la categoría creada, con su id
     */
    public Category insert(String name) {
        Number id = insert.executeAndReturnKey(Map.of("name", name));
        return new Category(id.longValue(), name);
    }

    /**
     * Lista todas las categorías ordenadas por nombre.
     *
     * @return categorías
     */
    public List<Category> findAll() {
        return jdbc.sql("select id, name from categories order by name").query(Category.class).list();
    }

    /**
     * Busca una categoría.
     *
     * @param id identificador
     * @return la categoría, si existe
     */
    public Optional<Category> findById(long id) {
        return jdbc.sql("select id, name from categories where id = :id").param("id", id).query(Category.class).optional();
    }
}
