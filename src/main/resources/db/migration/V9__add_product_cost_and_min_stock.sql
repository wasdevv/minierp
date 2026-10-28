-- Cost price (optional: unknown cost means unknown margin, never zero), a minimum stock per product, and the
-- cost each sale item had, so later cost changes do not rewrite past profit.
ALTER TABLE products
    ADD COLUMN cost_price NUMERIC(12, 2),
    ADD COLUMN min_stock  INTEGER NOT NULL DEFAULT 10,
    ADD CONSTRAINT products_cost_price_check CHECK (cost_price >= 0),
    ADD CONSTRAINT products_min_stock_check CHECK (min_stock >= 0);

ALTER TABLE sale_items
    ADD COLUMN unit_cost NUMERIC(12, 2),
    ADD CONSTRAINT sale_items_unit_cost_check CHECK (unit_cost >= 0);
