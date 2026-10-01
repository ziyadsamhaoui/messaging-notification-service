CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL,
    type         VARCHAR(20) NOT NULL,
    content      TEXT NOT NULL,
    source_type  VARCHAR(20) NOT NULL,
    source_id    VARCHAR(200) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at TIMESTAMPTZ,
    is_read      BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT notifications_type_check CHECK (type IN ('MESSAGE', 'REACTION', 'INVITATION', 'SYSTEM')),
    CONSTRAINT notifications_source_type_check CHECK (source_type IN ('MESSAGE', 'REACTION', 'INVITATION', 'SYSTEM'))
);

CREATE UNIQUE INDEX ux_notifications_user_source ON notifications (user_id, source_type, source_id);
CREATE INDEX ix_notifications_user_created ON notifications (user_id, created_at DESC, id DESC);
CREATE INDEX ix_notifications_user_unread ON notifications (user_id) WHERE is_read = FALSE;

CREATE TABLE push_subscriptions (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL,
    endpoint     TEXT NOT NULL,
    p256dh_key   VARCHAR(255) NOT NULL,
    auth_key     VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,

    CONSTRAINT ux_push_subscriptions_endpoint UNIQUE (endpoint)
);

CREATE INDEX ix_push_subscriptions_user ON push_subscriptions (user_id);

CREATE TABLE notification_preferences (
    user_id      UUID PRIMARY KEY,
    muted_types  VARCHAR(255) NOT NULL DEFAULT '',
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE room_membership_cache (
    room_id     VARCHAR(100) NOT NULL,
    user_id     UUID NOT NULL,
    is_muted    BOOLEAN NOT NULL DEFAULT FALSE,
    muted_until TIMESTAMPTZ,

    CONSTRAINT room_membership_cache_pkey PRIMARY KEY (room_id, user_id)
);

CREATE INDEX ix_room_membership_cache_user ON room_membership_cache (user_id);

CREATE TABLE message_sender_cache (
    message_id VARCHAR(100) PRIMARY KEY,
    room_id    VARCHAR(100) NOT NULL,
    sender_id  UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_message_sender_cache_created_at ON message_sender_cache (created_at);

CREATE TABLE pending_push_deliveries (
    id              UUID PRIMARY KEY,
    notification_id UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at    TIMESTAMPTZ,
    attempts        INT NOT NULL DEFAULT 0,

    CONSTRAINT fk_pending_push_notification FOREIGN KEY (notification_id)
        REFERENCES notifications (id) ON DELETE CASCADE
);

CREATE INDEX ix_pending_push_undelivered ON pending_push_deliveries (created_at) WHERE delivered_at IS NULL;
