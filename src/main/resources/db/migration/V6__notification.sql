create table notification (
    id              bigint generated always as identity primary key,
    kind            text        not null check (kind in ('DAILY', 'REMINDER', 'WEEKLY')),
    dedupe_key      text        not null unique,
    payload         text        not null,
    status          text        not null default 'PENDING' check (status in ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    attempts        integer     not null default 0,
    next_attempt_at timestamptz not null default now(),
    claimed_at      timestamptz,
    last_error      text,
    sent_at         timestamptz,
    created_at      timestamptz not null default now()
);

create index notification_due_idx on notification (next_attempt_at) where status = 'PENDING';
