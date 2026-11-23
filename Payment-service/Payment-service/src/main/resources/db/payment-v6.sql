BEGIN;

ALTER TABLE public.payments
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_idempotency_key
    ON public.payments (idempotency_key);

CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_one_succeeded_order
    ON public.payments (order_id) WHERE status = 'SUCCEEDED';

CREATE TABLE IF NOT EXISTS public.payment_outbox_events (
    id UUID PRIMARY KEY,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_payment_outbox_pending
    ON public.payment_outbox_events (sent_at, next_attempt_at);

COMMIT;
