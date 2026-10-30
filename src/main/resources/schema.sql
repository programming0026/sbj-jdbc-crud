create table if not exists categories (
    id   bigserial primary key,
    name varchar(80) not null unique
);

create table if not exists products (
    id          bigserial primary key,
    sku         varchar(30)    not null unique,
    name        varchar(120)   not null,
    description varchar(500),
    price       numeric(12, 2) not null check (price >= 0),
    stock       int            not null check (stock >= 0),
    category_id bigint         not null references categories (id),
    created_at  timestamptz    not null default now(),
    updated_at  timestamptz    not null default now(),
    version     int            not null default 0
);

create index if not exists idx_products_category on products (category_id);
create index if not exists idx_products_name_lower on products (lower(name));
