CREATE TABLE transaction_fraud_assessments (
 id UUID PRIMARY KEY,
 event_id UUID NOT NULL UNIQUE,
 transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 risk_score INTEGER NOT NULL,
 risk_level VARCHAR(10) NOT NULL,
 suspicious BOOLEAN NOT NULL,
 reasons TEXT NOT NULL,
 evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 rules_version VARCHAR(30) NOT NULL
);
CREATE INDEX idx_fraud_assessments_user_evaluated ON transaction_fraud_assessments(user_id,evaluated_at DESC);
CREATE INDEX idx_fraud_assessments_transaction ON transaction_fraud_assessments(transaction_id);
CREATE INDEX idx_fraud_assessments_level ON transaction_fraud_assessments(risk_level);
