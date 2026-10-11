CREATE TABLE budgets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    budget_month DATE NOT NULL,
    total_limit NUMERIC(19,2) NOT NULL,
    warning_threshold_percent INTEGER NOT NULL DEFAULT 80,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_budgets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_budgets_month CHECK (budget_month = date_trunc('month', budget_month::timestamp)::date),
    CONSTRAINT chk_budgets_total_limit CHECK (total_limit > 0),
    CONSTRAINT chk_budgets_warning_threshold CHECK (warning_threshold_percent BETWEEN 1 AND 100)
);
CREATE INDEX idx_budgets_user_id ON budgets (user_id);
CREATE INDEX idx_budgets_user_id_month ON budgets (user_id, budget_month);
CREATE INDEX idx_budgets_user_id_active ON budgets (user_id, active);
CREATE UNIQUE INDEX uq_active_budgets_user_name_month ON budgets (user_id, name, budget_month) WHERE active = TRUE;

CREATE TABLE budget_categories (
    budget_id UUID NOT NULL,
    category_id UUID NOT NULL,
    category_limit NUMERIC(19,2) NOT NULL,
    PRIMARY KEY (budget_id, category_id),
    CONSTRAINT fk_budget_categories_budget FOREIGN KEY (budget_id) REFERENCES budgets (id) ON DELETE CASCADE,
    CONSTRAINT fk_budget_categories_category FOREIGN KEY (category_id) REFERENCES transaction_categories (id),
    CONSTRAINT chk_budget_categories_limit CHECK (category_limit > 0)
);

CREATE TABLE alerts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    alert_type VARCHAR(40) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(180) NOT NULL,
    message VARCHAR(500) NOT NULL,
    related_budget_id UUID NULL,
    related_transaction_id UUID NULL,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alerts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_alerts_budget FOREIGN KEY (related_budget_id) REFERENCES budgets (id) ON DELETE SET NULL,
    CONSTRAINT fk_alerts_transaction FOREIGN KEY (related_transaction_id) REFERENCES transactions (id) ON DELETE SET NULL
);
CREATE INDEX idx_alerts_user_id_read ON alerts (user_id, read);
CREATE INDEX idx_alerts_user_id_created_at ON alerts (user_id, created_at DESC);
CREATE INDEX idx_alerts_alert_type ON alerts (alert_type);
CREATE UNIQUE INDEX uq_budget_threshold_alert ON alerts (related_budget_id, alert_type)
    WHERE alert_type IN ('BUDGET_WARNING', 'BUDGET_EXCEEDED');
