package com.juniverse.jdbccrud.repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Operaciones por lote con {@link JdbcTemplate}, la API clásica de Spring JDBC. Un {@code batchUpdate} envía muchas
 * sentencias en pocos viajes a la base: mucho más rápido que un {@code insert} por producto.
 */
@Repository
public class ProductBatchRepository {

    /**
     * Fila a insertar en un lote.
     *
     * @param sku código
     * @param name nombre
     * @param description descripción
     * @param price precio
     * @param stock existencias
     * @param categoryId categoría
     */
    public record NewProduct(String sku, String name, String description, BigDecimal price, int stock, long categoryId) {
    }

    private static final String INSERT = "insert into products (sku, name, description, price, stock, category_id) values (?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;

    /**
     * Crea el repositorio.
     *
     * @param jdbc plantilla JDBC que Spring Boot configura con el DataSource
     */
    public ProductBatchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserta muchos productos en lotes de {@code batchSize}.
     *
     * @param products productos a insertar
     * @param batchSize cuántas filas se envían por lote
     * @return total de filas insertadas
     */
    public int insertAll(List<NewProduct> products, int batchSize) {
        int[][] results = jdbc.batchUpdate(INSERT, products, batchSize, (PreparedStatement ps, NewProduct p) -> bind(ps, p));
        int inserted = 0;
        for (int[] batch : results) {
            for (int rows : batch) {
                // algunos drivers devuelven SUCCESS_NO_INFO (-2) en lotes: cuenta como una fila insertada
                inserted += rows == java.sql.Statement.SUCCESS_NO_INFO ? 1 : rows;
            }
        }
        return inserted;
    }

    private static void bind(PreparedStatement ps, NewProduct p) throws SQLException {
        ps.setString(1, p.sku());
        ps.setString(2, p.name());
        ps.setString(3, p.description());
        ps.setBigDecimal(4, p.price());
        ps.setInt(5, p.stock());
        ps.setLong(6, p.categoryId());
    }

    /**
     * Cuenta los productos de una categoría con una consulta simple ({@code queryForObject}).
     *
     * @param categoryId categoría
     * @return cantidad de productos
     */
    public long countByCategory(long categoryId) {
        Long count = jdbc.queryForObject("select count(*) from products where category_id = ?", Long.class, categoryId);
        return count == null ? 0 : count;
    }

    /**
     * Recorre TODOS los SKU sin cargarlos en memoria: el {@code RowCallbackHandler} procesa cada fila al llegar. Es la forma de
     * exportar tablas grandes sin agotar la memoria.
     *
     * @param consumer acción para cada SKU
     */
    public void forEachSku(java.util.function.Consumer<String> consumer) {
        jdbc.query("select sku from products order by sku", rs -> {
            consumer.accept(rs.getString("sku"));
        });
    }
}
