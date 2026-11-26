create table problem (
    id         bigint generated always as identity primary key,
    slug       text        not null unique,
    title      text        not null,
    prompt     text        not null,
    difficulty smallint    not null check (difficulty between 1 and 3),
    active     boolean     not null default true,
    created_at timestamptz not null default now()
);

create table problem_topic (
    problem_id bigint not null references problem (id) on delete cascade,
    topic      text   not null,
    primary key (problem_id, topic)
);

create table key_point (
    id         bigint generated always as identity primary key,
    problem_id bigint  not null references problem (id),
    code       text    not null,
    dimension  text    not null,
    text       text    not null,
    active     boolean not null default true,
    unique (problem_id, code)
);

create index key_point_problem_idx on key_point (problem_id);
