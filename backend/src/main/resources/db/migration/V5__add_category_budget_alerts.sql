ALTER TABLE alerts
    ADD COLUMN related_category_id UUID NULL,
    ADD CONSTRAINT fk_alerts_category
        FOREIGN KEY (related_category_id) REFERENCES transaction_categories (id) ON DELETE SET NULL;

CREATE INDEX idx_alerts_budget_category_type
    ON alerts (related_budget_id, related_category_id, alert_type);

CREATE UNIQUE INDEX uq_category_budget_threshold_alert
    ON alerts (related_budget_id, related_category_id, alert_type)
    WHERE alert_type IN ('CATEGORY_BUDGET_WARNING', 'CATEGORY_BUDGET_EXCEEDED');
