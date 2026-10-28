-- Payment method, discount and cash change. total_amount stays what the customer pays (after discount), so
-- reports and the dashboard keep reading the same column.
ALTER TABLE sales
    ADD COLUMN subtotal_amount NUMERIC(12, 2),
    ADD COLUMN discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN payment_method  VARCHAR(20),
    ADD COLUMN amount_received NUMERIC(12, 2),
    ADD COLUMN change_amount   NUMERIC(12, 2);

-- Sales before this had no discount; their payment method was never recorded and stays unknown (NULL).
UPDATE sales SET subtotal_amount = total_amount;

ALTER TABLE sales
    ALTER COLUMN subtotal_amount SET NOT NULL,
    ADD CONSTRAINT sales_discount_check CHECK (discount_amount >= 0 AND discount_amount <= subtotal_amount),
    ADD CONSTRAINT sales_total_after_discount_check CHECK (total_amount = subtotal_amount - discount_amount),
    ADD CONSTRAINT sales_payment_method_check
        CHECK (payment_method IN ('PIX', 'CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'ON_ACCOUNT')),
    ADD CONSTRAINT sales_on_account_customer_check CHECK (payment_method <> 'ON_ACCOUNT' OR customer_id IS NOT NULL),
    ADD CONSTRAINT sales_cash_change_check CHECK (
        (amount_received IS NULL AND change_amount IS NULL)
        OR (payment_method = 'CASH' AND amount_received >= total_amount
            AND change_amount = amount_received - total_amount));

CREATE INDEX sales_payment_method_idx ON sales (payment_method, sale_date);
