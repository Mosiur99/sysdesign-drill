alter table grade
    add column bonus             numeric(5, 2),
    add column follow_up_results jsonb not null default '[]';

alter table drill
    add column final_score numeric(5, 2);
