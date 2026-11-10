-- Create product table
CREATE TABLE product (
                         id BIGSERIAL PRIMARY KEY,
                         description VARCHAR(255) NOT NULL
);

-- Join table modelling which products appear in which order (many-to-many)
CREATE TABLE order_product (
                               order_id BIGINT NOT NULL,
                               product_id BIGINT NOT NULL,
                               CONSTRAINT pk_order_product PRIMARY KEY (order_id, product_id),
                               CONSTRAINT fk_order_product_order FOREIGN KEY (order_id) REFERENCES "order" (id),
                               CONSTRAINT fk_order_product_product FOREIGN KEY (product_id) REFERENCES product (id)
);

-- The FK column is not automatically indexed in PostgreSQL; the customer->orders fetch join
-- and any order lookup by customer need it
CREATE INDEX idx_order_customer_id ON "order" (customer_id);

-- Product -> orders lookups (GET /products returns order IDs per product)
CREATE INDEX idx_order_product_product_id ON order_product (product_id);

-- Case-insensitive substring search on customer names (GET /customer?query=...) cannot use a
-- plain btree index; the trigram GIN index makes ILIKE '%term%' style predicates index-assisted.
-- Requires a privileged role to create the extension (the dockerised 'admin' user is a superuser).
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_customer_name_trgm ON customer USING gin (lower(name) gin_trgm_ops);
