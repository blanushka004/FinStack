CREATE TABLE financial_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    account_type VARCHAR(40) NOT NULL,
    institution_name VARCHAR(150) NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    current_balance NUMERIC(19,2) NOT NULL DEFAULT 0,
    credit_limit NUMERIC(19,2) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_financial_accounts_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_financial_accounts_user_id ON financial_accounts (user_id);
CREATE INDEX idx_financial_accounts_user_id_active ON financial_accounts (user_id, active);
CREATE INDEX idx_financial_accounts_account_type ON financial_accounts (account_type);
