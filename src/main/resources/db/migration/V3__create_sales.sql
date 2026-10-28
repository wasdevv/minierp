CREATE TABLE sales (
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT REFERENCES customers (id),
    sale_date    TIMESTAMPTZ    NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    CONSTRAINT sales_total_amount_check CHECK (total_amount >= 0)
);

CREATE INDEX sales_sale_date_idx ON sales (sale_date);
CREATE INDEX sales_customer_id_idx ON sales (customer_id);

CREATE TABLE sale_items (
    id         BIGSERIAL PRIMARY KEY,
    sale_id    BIGINT         NOT NULL REFERENCES sales (id) ON DELETE CASCADE,
    product_id BIGINT         NOT NULL REFERENCES products (id),
    quantity   INTEGER        NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    subtotal   NUMERIC(12, 2) NOT NULL,
    CONSTRAINT sale_items_quantity_check CHECK (quantity > 0),
    CONSTRAINT sale_items_unit_price_check CHECK (unit_price >= 0),
    CONSTRAINT sale_items_subtotal_check CHECK (subtotal = unit_price * quantity),
    CONSTRAINT sale_items_sale_product_key UNIQUE (sale_id, product_id)
);

CREATE INDEX sale_items_product_id_idx ON sale_items (product_id);
