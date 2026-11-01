# CRUD con Spring JDBC y PostgreSQL

Ejemplo con **Spring Boot 4** y **Java 21** de un CRUD de productos con **Spring JDBC** (sin JPA ni ORM) sobre **PostgreSQL**, que se levanta con Docker. El SQL lo escribes tú; Spring se ocupa de las conexiones, las transacciones, el mapeo de filas y la traducción de errores.

## ¿Por qué Spring JDBC y no JPA?

| | Spring JDBC | JPA / Hibernate |
|---|---|---|
| SQL | Lo escribes tú (control total, fácil de optimizar y de depurar) | Lo genera el ORM |
| Curva de aprendizaje | Baja: es SQL + un poco de Spring | Alta (sesiones, estados, *lazy loading*, N+1...) |
| Mapeo objeto-relacional | Manual con `RowMapper` o records | Automático con anotaciones |
| Ideal para | Consultas complejas, reportes, lotes, rendimiento, esquemas existentes | Modelos de dominio ricos con muchas relaciones |

## Qué muestra el ejemplo

| Tema | Dónde |
|------|-------|
| **`JdbcClient`**: API fluida con parámetros con nombre (`:sku`), `RowMapper`, `optional()`, `single()`, `list()` | `ProductRepository` |
| Mapeo automático de una fila a un **record** (por nombre de columna) | `CategoryStock`, `Category` |
| `INSERT ... RETURNING id` para recuperar la clave que genera PostgreSQL | `ProductRepository.insert` |
| **`SimpleJdbcInsert`**: insertar sin escribir SQL y obtener la clave generada | `CategoryRepository` |
| **`JdbcTemplate.batchUpdate`**: importar miles de filas en pocos viajes a la base | `ProductBatchRepository` |
| `RowCallbackHandler`: recorrer una tabla grande fila a fila sin cargarla en memoria | `ProductBatchRepository.forEachSku` |
| Búsqueda dinámica con filtros opcionales, **paginación** y orden con **lista blanca** de columnas | `ProductRepository.search` |
| **Control de concurrencia optimista** con una columna `version` | `ProductRepository.update` |
| Actualización **atómica** de stock en una sola sentencia (sin leer antes) | `ProductRepository.adjustStock` |
| **Transacciones** con `@Transactional`: transferir stock y lote "todo o nada" (rollback) | `ProductService` |
| Traducción de errores SQL a excepciones de Spring (`DuplicateKeyException`, `DataIntegrityViolationException`) y a respuestas HTTP | `ApiExceptionHandler` |
| Agregación (`GROUP BY`) para un reporte de inventario | `ProductRepository.stockByCategory` |
| Esquema y datos de ejemplo con `schema.sql` / `data.sql` (idempotentes) | `src/main/resources` |

## Tablas

```sql
categories (id bigserial pk, name unique)
products   (id bigserial pk, sku unique, name, description, price >= 0, stock >= 0,
            category_id → categories, created_at, updated_at, version)
```

Las restricciones (`unique`, `check`, llave foránea) viven en la base de datos: son la última defensa aunque falle la validación de la aplicación.

## API

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/products?q=&categoryId=&sort=&desc=&page=&size=` | Lista paginada (orden: `name`, `price`, `stock`, `created`, `sku`) |
| GET | `/api/products/{id}` | Un producto con el nombre de su categoría |
| POST | `/api/products` | Crea (201 + `Location`) |
| PUT | `/api/products/{id}` | Reemplaza; requiere `version` (409 si cambió) |
| DELETE | `/api/products/{id}` | Elimina (204) |
| PATCH | `/api/products/{id}/stock` | Suma o resta existencias: `{"delta": -3}` |
| POST | `/api/products/transfer` | Mueve unidades entre dos productos (transacción) |
| POST | `/api/products/import` | Importa un lote, todo o nada |
| GET | `/api/products/reports/stock-by-category` | Inventario por categoría |
| GET / POST | `/api/categories` | Lista y crea categorías |

Errores en formato Problem Details: 400 (validación), 404, 409 (SKU duplicado, versión desactualizada, stock insuficiente, restricción violada).

## Estructura

```text
src/main/java/com/juniverse/jdbccrud/
├── JdbcCrudApplication.java
├── model/        # Product, Category, PageResult, CategoryStock (records)
├── repository/   # ProductRepository (JdbcClient), ProductBatchRepository (JdbcTemplate), CategoryRepository (SimpleJdbcInsert)
├── service/      # ProductService (@Transactional)
├── controller/   # ProductController, CategoryController
└── exception/    # excepciones de negocio y ApiExceptionHandler
src/main/resources/ schema.sql, data.sql, application.properties
```

## Requisitos

- JDK 21, Maven 3.9 o superior y Docker.

## Ejecutar

```bash
docker compose up -d      # PostgreSQL 16 en localhost:5432
mvn spring-boot:run
```

```bash
curl "http://localhost:8080/api/products?sort=price&desc=true&size=3"
curl http://localhost:8080/api/products/1
curl -X POST http://localhost:8080/api/products -H "Content-Type: application/json" \
     -d '{"sku":"WEB-001","name":"Webcam HD","price":799,"stock":10,"categoryId":1}'
curl -X PUT http://localhost:8080/api/products/1 -H "Content-Type: application/json" \
     -d '{"version":0,"product":{"sku":"LAP-001","name":"Laptop 14 Pro","price":16999,"stock":12,"categoryId":1}}'
curl -X PATCH http://localhost:8080/api/products/1/stock -H "Content-Type: application/json" -d '{"delta":-2}'
curl -X POST http://localhost:8080/api/products/transfer -H "Content-Type: application/json" -d '{"fromId":3,"toId":4,"quantity":5}'
curl http://localhost:8080/api/products/reports/stock-by-category
```

Para ver la base: `docker exec -it jdbccrud-postgres psql -U jdbccrud -c "select id, sku, stock, version from products"`. Las credenciales se pueden sobrescribir con `DB_URL`, `DB_USER` y `DB_PASSWORD`.

## Pruebas

```bash
mvn verify
```

`JdbcCrudIT` (15 pruebas) usa un PostgreSQL real con Testcontainers (requiere Docker). Cubre: creación del esquema y datos de ejemplo; crear, leer, actualizar y borrar; versión desactualizada (409); SKU duplicado y categoría inexistente; la restricción `CHECK` de la base; búsqueda, orden y paginación; **inyección SQL** inofensiva; **20 salidas de stock simultáneas** (exactamente 10 tienen éxito y nunca baja de 0); transferencia con **rollback**; importación de 1.200 filas por lote y lote fallido que no deja nada; `SimpleJdbcInsert`; recorrido por filas; y el reporte.

## Buenas prácticas

- **Siempre parámetros**, nunca concatenar valores en el SQL. Lo único que se arma como texto (la columna de `ORDER BY`) sale de una lista blanca.
- Deja que la **base garantice la unicidad** (índice único) y traduce la excepción; comprobar antes con un `SELECT` falla bajo concurrencia.
- Para cambios numéricos concurrentes usa un `UPDATE` atómico (`stock = stock + :delta` con su condición), no leer-modificar-guardar.
- Pon `@Transactional` en la capa de servicio; las operaciones que deben ir juntas, en el mismo método. `readOnly = true` en las consultas.
- Para cargas masivas usa `batchUpdate`; para exportar tablas grandes recorre las filas con un `RowCallbackHandler`.
- Un `OFFSET` grande es lento; para paginar millones de filas usa paginación por clave (*keyset*).
- En producción gestiona el esquema con migraciones (Flyway o Liquibase) en lugar de `schema.sql`.

Fat jar ejecutable: `mvn clean package` y `java -jar target/sbj-jdbc-crud-0.0.1-SNAPSHOT.jar`.

## Licencia

Código de ejemplo de uso libre con fines educativos.
