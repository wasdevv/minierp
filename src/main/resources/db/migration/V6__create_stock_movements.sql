-- Every change to a product's stock, with who, when, why and the balance it left. products.stock_quantity
-- stays the source of truth for the current balance; this is its ledger.
CREATE TABLE stock_movements (
    id            BIGSERIAL PRIMARY KEY,
    product_id    BIGINT       NOT NULL REFERENCES products (id),
    type          VARCHAR(20)  NOT NULL,
    quantity      INTEGER      NOT NULL,
    balance_after INTEGER      NOT NULL,
    reason        VARCHAR(200),
    sale_id       BIGINT REFERENCES sales (id),
    created_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(60)  NOT NULL,
    CONSTRAINT stock_movements_type_check
        CHECK (type IN ('INITIAL', 'RECEIPT', 'ADJUSTMENT', 'SALE', 'CANCELLATION')),
    CONSTRAINT stock_movements_quantity_check CHECK (quantity <> 0),
    CONSTRAINT stock_movements_balance_check CHECK (balance_after >= 0),
    CONSTRAINT stock_movements_sale_check CHECK ((type IN ('SALE', 'CANCELLATION')) = (sale_id IS NOT NULL))
);

CREATE INDEX stock_movements_product_idx ON stock_movements (product_id, created_at DESC);
CREATE INDEX stock_movements_created_at_idx ON stock_movements (created_at DESC);

-- Products that already exist start their ledger with what they hold now. Earlier sales are not replayed:
-- there was no ledger then, and inventing one would be a guess.
INSERT INTO stock_movements (product_id, type, quantity, balance_after, reason, created_at, created_by)
SELECT id, 'INITIAL', stock_quantity, stock_quantity, 'Saldo existente ao ativar o histórico', now(), 'sistema'
FROM products
WHERE stock_quantity > 0;
