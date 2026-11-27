alter table notification drop constraint notification_kind_check;

alter table notification
    add constraint notification_kind_check check (kind in ('DAILY', 'REMINDER', 'WEEKLY', 'ADDRESS'));
