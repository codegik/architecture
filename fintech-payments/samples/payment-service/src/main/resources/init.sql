-- Initialize payment database schema
-- Based on fintech payment platform architecture

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Payments table with indexing for high-performance batch inserts
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(100) NOT NULL,
    sender_account_id VARCHAR(100) NOT NULL,
    receiver_account_id VARCHAR(100) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL',
    status VARCHAR(20) NOT NULL, -- PENDING, PROCESSING, COMPLETED, FAILED
    idempotency_key VARCHAR(100) NOT NULL,
    payment_method VARCHAR(50) NOT NULL, -- BANK_TRANSFER, CREDIT_CARD, PIX
    fraud_score INTEGER DEFAULT 0,
    fraud_decision VARCHAR(20), -- ALLOW, CHALLENGE, BLOCK
    external_gateway_id VARCHAR(100),
    external_gateway_response TEXT,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,

    -- Constraints
    CONSTRAINT check_amount_positive CHECK (amount > 0),
    CONSTRAINT check_status_valid CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT check_fraud_score_range CHECK (fraud_score >= 0 AND fraud_score <= 100)
);

-- Indexes for high-performance queries
CREATE INDEX idx_payments_tenant_id ON payments(tenant_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_created_at ON payments(created_at);
CREATE INDEX idx_payments_idempotency_key ON payments(tenant_id, idempotency_key);
CREATE INDEX idx_payments_sender_account ON payments(tenant_id, sender_account_id);
CREATE INDEX idx_payments_receiver_account ON payments(tenant_id, receiver_account_id);

-- Unique constraint for idempotency
CREATE UNIQUE INDEX idx_payments_unique_idempotency ON payments(tenant_id, idempotency_key);

-- Payment audit log for compliance
CREATE TABLE payment_audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    payment_id UUID NOT NULL REFERENCES payments(id),
    tenant_id VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL, -- CREATED, VALIDATED, FRAUD_CHECK, GATEWAY_CALL, COMPLETED, FAILED
    details JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL
);

CREATE INDEX idx_audit_payment_id ON payment_audit_log(payment_id);
CREATE INDEX idx_audit_tenant_id ON payment_audit_log(tenant_id);
CREATE INDEX idx_audit_created_at ON payment_audit_log(created_at);

-- Batch processing statistics
CREATE TABLE batch_statistics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    batch_id VARCHAR(100) NOT NULL UNIQUE,
    tenant_id VARCHAR(100) NOT NULL,
    total_payments INTEGER NOT NULL,
    successful_payments INTEGER DEFAULT 0,
    failed_payments INTEGER DEFAULT 0,
    total_amount DECIMAL(15, 2) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,
    throughput_per_second DECIMAL(10, 2),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_batch_stats_tenant ON batch_statistics(tenant_id);
CREATE INDEX idx_batch_stats_start_time ON batch_statistics(start_time);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to auto-update updated_at
CREATE TRIGGER update_payments_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Sample data for testing (optional)
-- INSERT INTO payments (tenant_id, sender_account_id, receiver_account_id, amount, currency, status, idempotency_key, payment_method)
-- VALUES
--   ('tenant_001', 'acc_1001', 'acc_2001', 100.50, 'BRL', 'PENDING', 'test-key-001', 'PIX'),
--   ('tenant_001', 'acc_1002', 'acc_2002', 250.75, 'BRL', 'PENDING', 'test-key-002', 'BANK_TRANSFER');

COMMENT ON TABLE payments IS 'Main payments table - stores all payment transactions';
COMMENT ON TABLE payment_audit_log IS 'Audit trail for all payment operations - immutable log';
COMMENT ON TABLE batch_statistics IS 'Statistics for batch payment processing runs';

