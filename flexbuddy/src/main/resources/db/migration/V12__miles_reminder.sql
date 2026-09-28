alter table app_users add column remind_miles boolean not null default false;

alter table reminder_log drop constraint chk_reminder_log_kind;
alter table reminder_log add constraint chk_reminder_log_kind
    check (kind in ('BEFORE_START', 'CONFIRM', 'MILES'));
