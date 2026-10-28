CREATE TABLE products (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(120)  NOT NULL,
    sku            VARCHAR(40)   NOT NULL,
    description    VARCHAR(1000),
    price          NUMERIC(12, 2) NOT NULL,
    stock_quantity INTEGER       NOT NULL DEFAULT 0,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT products_sku_key UNIQUE (sku),
    CONSTRAINT products_price_check CHECK (price >= 0),
    CONSTRAINT products_stock_quantity_check CHECK (stock_quantity >= 0)
);
