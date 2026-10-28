CREATE TABLE customers (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    email      VARCHAR(254),
    document   VARCHAR(14),
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT customers_document_key UNIQUE (document),
    CONSTRAINT customers_document_check CHECK (document ~ '^([0-9]{11}|[0-9]{14})$')
);
