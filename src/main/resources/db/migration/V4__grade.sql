create table grade (
    id                bigint generated always as identity primary key,
    answer_id         bigint      not null unique references answer (id) on delete cascade,
    status            text        not null check (status in ('OK', 'FAILED')),
    overall           numeric(5, 2),
    summary           text,
    strengths         jsonb       not null default '[]',
    improvements      jsonb       not null default '[]',
    follow_ups        jsonb       not null default '[]',
    unverified_quotes integer     not null default 0,
    model             text,
    prompt_tokens     integer     not null default 0,
    completion_tokens integer     not null default 0,
    latency_ms        bigint      not null default 0,
    attempts          integer     not null default 0,
    failure_reason    text,
    failure_detail    text,
    created_at        timestamptz not null default now()
);

create table dimension_score (
    grade_id          bigint   not null references grade (id) on delete cascade,
    dimension         text     not null,
    score             smallint not null check (score between 0 and 4),
    evidence          text     not null,
    evidence_verified boolean  not null,
    missing           jsonb    not null default '[]',
    primary key (grade_id, dimension)
);

create table key_point_hit (
    grade_id     bigint  not null references grade (id) on delete cascade,
    key_point_id bigint  not null references key_point (id),
    covered      boolean not null,
    primary key (grade_id, key_point_id)
);
