CREATE TABLE IF NOT EXISTS public.auth_outbox_events (
    id UUID PRIMARY KEY,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_auth_outbox_pending
    ON public.auth_outbox_events (sent_at, next_attempt_at);

GRANT SELECT, INSERT, UPDATE ON public.auth_outbox_events TO auth_app;
