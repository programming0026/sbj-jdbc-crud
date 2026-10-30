package com.juniverse.jdbccrud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.juniverse.jdbccrud.model.Category;
import com.juniverse.jdbccrud.repository.CategoryRepository;
import com.juniverse.jdbccrud.repository.ProductBatchRepository;
import com.juniverse.jdbccrud.service.ProductService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** CRUD con Spring JDBC contra un PostgreSQL real que levanta Testcontainers (requiere Docker). */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("CRUD con Spring JDBC y PostgreSQL")
class JdbcCrudIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private WebApplicationContext context;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductService service;
    @Autowired private ProductBatchRepository batchRepository;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mvc;
    private long categoryId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        Category category = categories.insert("cat-" + UUID.randomUUID()); // categoría propia por prueba: no se mezclan datos
        categoryId = category.id();
    }

    private static String sku() {
        return "T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String productJson(String sku, String name, String price, int stock) {
        return "{\"sku\":\"" + sku + "\",\"name\":\"" + name + "\",\"description\":\"d\",\"price\":" + price + ",\"stock\":" + stock + ",\"categoryId\":" + categoryId + "}";
    }

    private long createProduct(String sku, String name, String price, int stock) throws Exception {
        String body = mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(productJson(sku, name, price, stock)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    @DisplayName("el esquema y los datos de ejemplo se cargaron al arrancar")
    void schemaAndSeedData() {
        assertEquals(5, jdbc.queryForObject("select count(*) from products where sku in ('LAP-001','LAP-002','MOU-001','KEY-001','MON-001')", Long.class));
    }

    @Test
    @DisplayName("CREATE: 201, Location, id generado por PostgreSQL y versión 0")
    void create() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(productJson(sku(), "Webcam", "799.00", 10)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/products/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("READ: por id (con el nombre de su categoría) y 404 si no existe")
    void read() throws Exception {
        long id = createProduct(sku(), "Bocina", "250.00", 3);
        mvc.perform(get("/api/products/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Bocina"))
                .andExpect(jsonPath("$.categoryName").value(org.hamcrest.Matchers.startsWith("cat-")));
        mvc.perform(get("/api/products/999999999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("UPDATE: reemplaza y sube la versión; con una versión vieja -> 409; inexistente -> 404")
    void updateWithOptimisticLocking() throws Exception {
        String sku = sku();
        long id = createProduct(sku, "Original", "100.00", 5);
        String update = "{\"version\":0,\"product\":" + productJson(sku, "Cambiado", "120.00", 6) + "}";

        mvc.perform(put("/api/products/" + id).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Cambiado")).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put("/api/products/" + id).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isConflict()); // la misma versión 0 ya no es la actual
        mvc.perform(put("/api/products/999999999").contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE: 204 y luego 404")
    void deleteProduct() throws Exception {
        long id = createProduct(sku(), "Temporal", "1.00", 1);
        mvc.perform(delete("/api/products/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/products/" + id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("restricciones: SKU duplicado -> 409 (DuplicateKeyException), categoría inexistente -> 404, datos inválidos -> 400")
    void constraints() throws Exception {
        String sku = sku();
        createProduct(sku, "Uno", "10.00", 1);
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(productJson(sku, "Dos", "10.00", 1)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("Valor duplicado"));
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku() + "\",\"name\":\"x\",\"price\":1,\"stock\":1,\"categoryId\":999999999}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"mal sku\",\"name\":\"\",\"price\":-1,\"stock\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.length()").value(5));
    }

    @Test
    @DisplayName("la restricción CHECK de la base también protege (Spring la traduce a DataIntegrityViolationException)")
    void checkConstraint() {
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("insert into products (sku, name, price, stock, category_id) values (?, 'x', -5, 1, ?)", sku(), categoryId));
    }

    @Test
    @DisplayName("búsqueda: texto, categoría, orden por precio y paginación")
    void searchSortAndPaginate() throws Exception {
        createProduct(sku(), "Alfa", "30.00", 1);
        createProduct(sku(), "Beta", "10.00", 1);
        createProduct(sku(), "Gamma", "20.00", 1);

        mvc.perform(get("/api/products").param("categoryId", String.valueOf(categoryId)).param("sort", "price"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].name").value("Beta"))
                .andExpect(jsonPath("$.content[2].name").value("Alfa"));
        mvc.perform(get("/api/products").param("categoryId", String.valueOf(categoryId)).param("sort", "price").param("desc", "true").param("size", "2"))
                .andExpect(jsonPath("$.content[0].name").value("Alfa")).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/products").param("categoryId", String.valueOf(categoryId)).param("q", "GAM"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("seguridad: el texto de búsqueda y el campo de orden no permiten inyección SQL")
    void sqlInjectionIsHarmless() throws Exception {
        mvc.perform(get("/api/products").param("q", "'; drop table products; --")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/products").param("sort", "name; drop table products")).andExpect(status().isOk()); // campo no permitido: se usa el orden por defecto
        assertTrue(jdbc.queryForObject("select count(*) from products", Long.class) >= 5, "la tabla sigue intacta");
    }

    @Test
    @DisplayName("stock atómico: 20 salidas simultáneas de 10 sobre un stock de 100 -> exactamente 10 tienen éxito y nunca baja de 0")
    void concurrentStockAdjustments() throws Exception {
        long id = createProduct(sku(), "Concurrente", "5.00", 100);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(pool.submit(() -> {
                try {
                    service.adjustStock(id, -10);
                    ok.incrementAndGet();
                } catch (com.juniverse.jdbccrud.exception.InsufficientStockException e) {
                    rejected.incrementAndGet();
                }
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        assertEquals(10, ok.get());
        assertEquals(10, rejected.get());
        assertEquals(0, service.get(id).stock());
    }

    @Test
    @DisplayName("PATCH stock: entrada, salida y 409 si no alcanza")
    void stockEndpoint() throws Exception {
        long id = createProduct(sku(), "Stock", "5.00", 5);
        mvc.perform(patch("/api/products/" + id + "/stock").contentType(MediaType.APPLICATION_JSON).content("{\"delta\":10}"))
                .andExpect(jsonPath("$.stock").value(15));
        mvc.perform(patch("/api/products/" + id + "/stock").contentType(MediaType.APPLICATION_JSON).content("{\"delta\":-16}")).andExpect(status().isConflict());
        mvc.perform(patch("/api/products/999999999/stock").contentType(MediaType.APPLICATION_JSON).content("{\"delta\":1}")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("transacción: transferir stock mueve las unidades; si el destino no existe, la salida del origen se DESHACE")
    void transferIsAtomic() throws Exception {
        long a = createProduct(sku(), "Origen", "5.00", 20);
        long b = createProduct(sku(), "Destino", "5.00", 0);

        mvc.perform(post("/api/products/transfer").contentType(MediaType.APPLICATION_JSON).content("{\"fromId\":" + a + ",\"toId\":" + b + ",\"quantity\":8}"))
                .andExpect(status().isNoContent());
        assertEquals(12, service.get(a).stock());
        assertEquals(8, service.get(b).stock());

        mvc.perform(post("/api/products/transfer").contentType(MediaType.APPLICATION_JSON).content("{\"fromId\":" + a + ",\"toId\":999999999,\"quantity\":5}"))
                .andExpect(status().isNotFound());
        assertEquals(12, service.get(a).stock(), "rollback: la salida del origen no quedó aplicada");
    }

    @Test
    @DisplayName("lote: importa muchos productos con batchUpdate; si una fila falla no se inserta ninguna (rollback)")
    void batchImportIsAllOrNothing() throws Exception {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 1200; i++) {
            rows.add("{\"sku\":\"B-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase() + "\",\"name\":\"Lote " + i + "\",\"price\":1.50,\"stock\":1,\"categoryId\":" + categoryId + "}");
        }
        mvc.perform(post("/api/products/import").contentType(MediaType.APPLICATION_JSON).content("{\"products\":[" + String.join(",", rows) + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inserted").value(1200));
        assertEquals(1200, batchRepository.countByCategory(categoryId));

        long other = categories.insert("otra-" + UUID.randomUUID()).id();
        String dup = "B-DUP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String body = "{\"products\":[{\"sku\":\"" + dup + "\",\"name\":\"a\",\"price\":1,\"stock\":1,\"categoryId\":" + other + "},"
                + "{\"sku\":\"" + dup + "\",\"name\":\"b\",\"price\":1,\"stock\":1,\"categoryId\":" + other + "}]}";
        mvc.perform(post("/api/products/import").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        assertEquals(0, batchRepository.countByCategory(other), "ninguna fila del lote fallido quedó guardada");
    }

    @Test
    @DisplayName("SimpleJdbcInsert: crea categorías con su id; el nombre repetido da 409; el recorrido por filas no carga todo en memoria")
    void categoriesAndStreaming() throws Exception {
        String name = "nueva-" + UUID.randomUUID();
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber());
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}")).andExpect(status().isConflict());
        mvc.perform(get("/api/categories")).andExpect(status().isOk());

        createProduct("STREAM-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(), "S", "1.00", 1);
        List<String> skus = new ArrayList<>();
        batchRepository.forEachSku(skus::add);
        assertTrue(skus.size() >= 6);
        assertEquals(skus.stream().sorted().toList(), skus, "las filas llegan ordenadas por SKU");
    }

    @Test
    @DisplayName("reporte: agregación por categoría mapeada a un record")
    void stockReport() throws Exception {
        createProduct(sku(), "R1", "10.00", 4);
        createProduct(sku(), "R2", "20.00", 2);
        String body = mvc.perform(get("/api/products/reports/stock-by-category")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<java.util.Map<String, Object>> report = JsonPath.read(body, "$[?(@.category =~ /cat-.*/ && @.products == 2)]");
        assertTrue(report.stream().anyMatch(r -> ((Number) r.get("totalStock")).intValue() == 6
                && new BigDecimal(r.get("inventoryValue").toString()).compareTo(new BigDecimal("80.00")) == 0));
    }
}
