CREATE TABLE transaction_categories (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category_type VARCHAR(30) NOT NULL,
    system_defined BOOLEAN NOT NULL DEFAULT FALSE,
    user_id UUID NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transaction_categories_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_transaction_categories_ownership
        CHECK ((system_defined = TRUE AND user_id IS NULL) OR (system_defined = FALSE AND user_id IS NOT NULL))
);

CREATE INDEX idx_transaction_categories_user_id ON transaction_categories (user_id);
CREATE INDEX idx_transaction_categories_category_type ON transaction_categories (category_type);
CREATE UNIQUE INDEX uq_system_transaction_category_name_type
    ON transaction_categories (name, category_type)
    WHERE system_defined = TRUE AND user_id IS NULL;
CREATE UNIQUE INDEX uq_user_transaction_category_name_type
    ON transaction_categories (user_id, name, category_type)
    WHERE system_defined = FALSE AND user_id IS NOT NULL;

INSERT INTO transaction_categories (id, name, category_type, system_defined)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'FOOD', 'EXPENSE', TRUE),
    ('00000000-0000-0000-0000-000000000002', 'GROCERIES', 'EXPENSE', TRUE),
    ('00000000-0000-0000-0000-000000000003', 'TRANSPORT', 'EXPENSE', TRUE),
    ('00000000-0000-0000-0000-000000000004', 'RENT', 'EXPENSE', TRUE),
    ('00000000-0000-0000-0000-000000000005', 'UTILITIES', 'EXPENSE', TRUE),
    ('00000000-0000-0000-0000-000000000006', 'HEALTHCARE', 'EXPENSE', TRUE),
    ('00000000-0000-0000-0000-000000000007', 'SHOPPING', 'EXPENSE', TRUE),
    ('00000000-0000-0000-0000-000000000008', 'ENTERTAINMENT', 'EXPENSE', TRUE),
    ('00000000-0000-0000-0000-000000000009', 'EDUCATION', 'EXPENSE', TRUE),
    ('00000000-0000-0000-0000-000000000010', 'TRAVEL', 'EXPENSE', TRUE),
    ('00000000-0000-0000-0000-000000000011', 'INSURANCE', 'EXPENSE', TRUE),
    ('00000000-0000-0000-0000-000000000012', 'INVESTMENT', 'EXPENSE', TRUE),
    ('00000000-0000-0000-0000-000000000013', 'OTHER_EXPENSE', 'EXPENSE', TRUE),
    ('00000000-0000-0000-0000-000000000014', 'SALARY', 'INCOME', TRUE),
    ('00000000-0000-0000-0000-000000000015', 'FREELANCE', 'INCOME', TRUE),
    ('00000000-0000-0000-0000-000000000016', 'BUSINESS', 'INCOME', TRUE),
    ('00000000-0000-0000-0000-000000000017', 'INTEREST', 'INCOME', TRUE),
    ('00000000-0000-0000-0000-000000000018', 'DIVIDEND', 'INCOME', TRUE),
    ('00000000-0000-0000-0000-000000000019', 'REFUND', 'INCOME', TRUE),
    ('00000000-0000-0000-0000-000000000020', 'OTHER_INCOME', 'INCOME', TRUE)
ON CONFLICT DO NOTHING;

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    category_id UUID NULL,
    transaction_type VARCHAR(30) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    merchant VARCHAR(180) NULL,
    description VARCHAR(500) NULL,
    payment_method VARCHAR(40) NULL,
    transaction_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL,
    reference VARCHAR(120) NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transactions_account
        FOREIGN KEY (account_id) REFERENCES financial_accounts (id),
    CONSTRAINT fk_transactions_category
        FOREIGN KEY (category_id) REFERENCES transaction_categories (id)
);

CREATE INDEX idx_transactions_account_id ON transactions (account_id);
CREATE INDEX idx_transactions_category_id ON transactions (category_id);
CREATE INDEX idx_transactions_transaction_time ON transactions (transaction_time);
CREATE INDEX idx_transactions_transaction_type ON transactions (transaction_type);
CREATE INDEX idx_transactions_status ON transactions (status);
CREATE INDEX idx_transactions_account_id_transaction_time ON transactions (account_id, transaction_time);
