CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_email_verification_tokens_user_created
    ON email_verification_tokens(user_id, created_at DESC);
CREATE INDEX idx_email_verification_tokens_active
    ON email_verification_tokens(token_hash, expires_at)
    WHERE used_at IS NULL;

CREATE TABLE login_audit_events (
    id UUID PRIMARY KEY,
    user_id UUID NULL REFERENCES users(id) ON DELETE SET NULL,
    email_attempted VARCHAR(255) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    successful BOOLEAN NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    failure_reason VARCHAR(40) NULL
);

CREATE INDEX idx_login_audit_events_occurred_at ON login_audit_events(occurred_at DESC);
CREATE INDEX idx_login_audit_events_user_occurred_at ON login_audit_events(user_id, occurred_at DESC);
CREATE INDEX idx_login_audit_events_success_occurred_at ON login_audit_events(successful, occurred_at DESC);
