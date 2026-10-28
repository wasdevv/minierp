-- Optimistic lock: sales decrement stock, and the product form must not write back a stock it read earlier.
ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
