create table drill (
    id            bigint generated always as identity primary key,
    problem_id    bigint      not null references problem (id),
    scheduled_for date        not null unique,
    kind          text        not null check (kind in ('NEW', 'RETRY')),
    status        text        not null check (status in
                      ('ASSIGNED', 'IN_PROGRESS', 'GRADING', 'GRADED', 'FOLLOW_UP', 'DONE', 'SKIPPED')),
    started_at    timestamptz,
    submitted_at  timestamptz,
    version       integer     not null default 0,
    created_at    timestamptz not null default now()
);

create index drill_problem_idx on drill (problem_id);

create table problem_progress (
    problem_id        bigint  primary key references problem (id),
    attempts          integer not null default 0 check (attempts >= 0),
    best_score        numeric(5, 2),
    last_score        numeric(5, 2),
    last_attempted_on date,
    next_due          date,
    retired           boolean not null default false
);
