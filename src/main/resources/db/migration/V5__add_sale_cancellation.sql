-- README rule 7: a cancelled sale puts its items back in stock and leaves the reports. It is kept, not
-- deleted, so the history still shows what was sold and why it was undone.
ALTER TABLE sales
    ADD COLUMN status        VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED',
    ADD COLUMN cancelled_at  TIMESTAMPTZ,
    ADD COLUMN cancel_reason VARCHAR(200),
    ADD CONSTRAINT sales_status_check CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    ADD CONSTRAINT sales_cancellation_check CHECK (
        (status = 'CANCELLED') = (cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL));

CREATE INDEX sales_status_sale_date_idx ON sales (status, sale_date);
