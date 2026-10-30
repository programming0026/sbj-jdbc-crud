insert into categories (name) values ('Cómputo'), ('Accesorios'), ('Monitores') on conflict (name) do nothing;

insert into products (sku, name, description, price, stock, category_id)
select v.sku, v.name, v.description, v.price, v.stock, c.id
from (values
    ('LAP-001', 'Laptop 14 pulgadas', 'Procesador de 8 núcleos, 16 GB de RAM', 15999.00, 12, 'Cómputo'),
    ('LAP-002', 'Laptop 16 pulgadas', 'Pantalla de alta resolución', 21999.00, 5, 'Cómputo'),
    ('MOU-001', 'Mouse inalámbrico', 'Ergonómico y recargable', 349.90, 80, 'Accesorios'),
    ('KEY-001', 'Teclado mecánico', 'Switches rojos', 1299.00, 35, 'Accesorios'),
    ('MON-001', 'Monitor 27 pulgadas', 'Panel IPS de 144 Hz', 5499.00, 9, 'Monitores')
) as v(sku, name, description, price, stock, category)
join categories c on c.name = v.category
on conflict (sku) do nothing;
