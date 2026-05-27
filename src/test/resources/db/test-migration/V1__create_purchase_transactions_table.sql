CREATE TABLE purchase_transactions (
    id UUID PRIMARY KEY,
    description VARCHAR(50) NOT NULL,
    transaction_date DATE NOT NULL,
    purchase_amount NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_purchase_transactions_description_length CHECK (CHAR_LENGTH(description) <= 50),
    CONSTRAINT ck_purchase_transactions_purchase_amount_positive CHECK (purchase_amount > 0)
);

CREATE INDEX idx_purchase_transactions_transaction_date
    ON purchase_transactions (transaction_date);

CREATE INDEX idx_purchase_transactions_created_at
    ON purchase_transactions (created_at);
