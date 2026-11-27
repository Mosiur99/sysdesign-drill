create table answer (
    id            bigint generated always as identity primary key,
    drill_id      bigint      not null references drill (id),
    round         smallint    not null check (round in (1, 2)),
    sections      jsonb       not null,
    word_count    integer     not null default 0,
    seconds_spent integer,
    draft         boolean     not null default true,
    updated_at    timestamptz not null,
    unique (drill_id, round)
);
