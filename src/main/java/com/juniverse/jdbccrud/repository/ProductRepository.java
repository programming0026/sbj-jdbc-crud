package com.juniverse.jdbccrud.repository;

import com.juniverse.jdbccrud.model.CategoryStock;
import com.juniverse.jdbccrud.model.PageResult;
import com.juniverse.jdbccrud.model.Product;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * CRUD de productos con {@link JdbcClient}, la API fluida de Spring JDBC: parámetros con nombre ({@code :sku}), mapeo de filas
 * con {@code RowMapper} y resultados tipados. Todos los valores viajan como <b>parámetros</b> (nunca concatenados al SQL),
 * lo que evita la inyección SQL.
 */
@Repository
public class ProductRepository {

    private static final String SELECT = """
            select p.id, p.sku, p.name, p.description, p.price, p.stock, p.category_id, c.name as category_name,
                   p.created_at, p.updated_at, p.version
            from products p join categories c on c.id = p.category_id""";

    /** Columnas por las que se permite ordenar: lista blanca, porque el nombre de una columna no se puede enviar como parámetro. */
    private static final Map<String, String> SORTABLE = Map.of(
            "name", "p.name", "price", "p.price", "stock", "p.stock", "created", "p.created_at", "sku", "p.sku");

    private static final RowMapper<Product> MAPPER = ProductRepository::mapRow;

    private final JdbcClient jdbc;

    /**
     * Crea el repositorio.
     *
     * @param jdbc cliente JDBC que Spring Boot configura con el DataSource
     */
    public ProductRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Product mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Product(rs.getLong("id"), rs.getString("sku"), rs.getString("name"), rs.getString("description"),
                rs.getBigDecimal("price"), rs.getInt("stock"), rs.getLong("category_id"), rs.getString("category_name"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                rs.getInt("version"));
    }

    /**
     * Busca un producto por id (SELECT con JOIN).
     *
     * @param id identificador
     * @return el producto, si existe
     */
    public Optional<Product> findById(long id) {
        return jdbc.sql(SELECT + " where p.id = :id").param("id", id).query(MAPPER).optional();
    }

    /**
     * Busca por SKU.
     *
     * @param sku código
     * @return el producto, si existe
     */
    public Optional<Product> findBySku(String sku) {
        return jdbc.sql(SELECT + " where p.sku = :sku").param("sku", sku).query(MAPPER).optional();
    }

    /**
     * Lista con filtros opcionales, orden y paginación (dos consultas: el total y la página).
     *
     * @param text texto a buscar en nombre o SKU (sin distinguir mayúsculas), o {@code null}
     * @param categoryId categoría, o {@code null}
     * @param sort campo de orden (name, price, stock, created, sku); si no está permitido se usa name
     * @param descending {@code true} para orden descendente
     * @param page página (desde 0)
     * @param size tamaño de página
     * @return página de productos
     */
    public PageResult<Product> search(String text, Long categoryId, String sort, boolean descending, int page, int size) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (text != null && !text.isBlank()) {
            where.append(" and (lower(p.name) like :text or lower(p.sku) like :text)");
            params.put("text", "%" + text.trim().toLowerCase() + "%");
        }
        if (categoryId != null) {
            where.append(" and p.category_id = :categoryId");
            params.put("categoryId", categoryId);
        }
        long total = jdbc.sql("select count(*) from products p" + where).params(params).query(Long.class).single();

        String column = SORTABLE.getOrDefault(sort, "p.name");
        params.put("limit", size);
        params.put("offset", (long) page * size);
        List<Product> content = jdbc.sql(SELECT + where + " order by " + column + (descending ? " desc" : " asc") + ", p.id limit :limit offset :offset")
                .params(params).query(MAPPER).list();
        return new PageResult<>(content, page, size, total, (int) Math.ceil(total / (double) size));
    }

    /**
     * Inserta un producto y devuelve el id que generó PostgreSQL ({@code RETURNING id}).
     *
     * @param sku código
     * @param name nombre
     * @param description descripción
     * @param price precio
     * @param stock existencias
     * @param categoryId categoría
     * @return id generado
     */
    public long insert(String sku, String name, String description, java.math.BigDecimal price, int stock, long categoryId) {
        return jdbc.sql("""
                insert into products (sku, name, description, price, stock, category_id)
                values (:sku, :name, :description, :price, :stock, :categoryId) returning id""")
                .param("sku", sku).param("name", name).param("description", description)
                .param("price", price).param("stock", stock).param("categoryId", categoryId)
                .query(Long.class).single();
    }

    /**
     * Actualiza un producto con <b>bloqueo optimista</b>: solo cambia si la versión sigue siendo la que el cliente leyó.
     *
     * @param id identificador
     * @param version versión que el cliente tenía
     * @param sku código
     * @param name nombre
     * @param description descripción
     * @param price precio
     * @param stock existencias
     * @param categoryId categoría
     * @return filas modificadas (0 si no existe o la versión cambió)
     */
    public int update(long id, int version, String sku, String name, String description, java.math.BigDecimal price, int stock, long categoryId) {
        return jdbc.sql("""
                update products
                set sku = :sku, name = :name, description = :description, price = :price, stock = :stock,
                    category_id = :categoryId, version = version + 1, updated_at = now()
                where id = :id and version = :version""")
                .param("id", id).param("version", version).param("sku", sku).param("name", name).param("description", description)
                .param("price", price).param("stock", stock).param("categoryId", categoryId).update();
    }

    /**
     * Elimina un producto.
     *
     * @param id identificador
     * @return filas eliminadas (0 si no existía)
     */
    public int delete(long id) {
        return jdbc.sql("delete from products where id = :id").param("id", id).update();
    }

    /**
     * Suma o resta existencias de forma atómica en una sola sentencia. La condición {@code stock + :delta >= 0} va en el
     * mismo {@code UPDATE}, así dos peticiones simultáneas nunca dejan el stock en negativo (no hace falta leer antes).
     *
     * @param id identificador
     * @param delta cantidad a sumar (negativa para una salida)
     * @return filas modificadas (0 si no existe o no alcanzan las existencias)
     */
    public int adjustStock(long id, int delta) {
        return jdbc.sql("update products set stock = stock + :delta, version = version + 1, updated_at = now() where id = :id and stock + :delta >= 0")
                .param("id", id).param("delta", delta).update();
    }

    public boolean exists(long id) {
        return jdbc.sql("select exists(select 1 from products where id = :id)").param("id", id).query(Boolean.class).single();
    }

    /**
     * Inventario por categoría. Es una agregación: un solo SELECT con GROUP BY, mapeado automáticamente a un record.
     *
     * @return una fila por categoría con productos
     */
    public List<CategoryStock> stockByCategory() {
        return jdbc.sql("""
                select c.name as category, count(p.id) as products, coalesce(sum(p.stock), 0) as total_stock,
                       coalesce(sum(p.stock * p.price), 0) as inventory_value
                from categories c join products p on p.category_id = c.id
                group by c.name order by c.name""").query(CategoryStock.class).list();
    }
}
