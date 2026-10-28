-- Phone / WhatsApp, digits only with area code (10 digits landline, 11 mobile).
ALTER TABLE customers
    ADD COLUMN phone VARCHAR(11),
    ADD CONSTRAINT customers_phone_check CHECK (phone ~ '^[0-9]{10,11}$');
