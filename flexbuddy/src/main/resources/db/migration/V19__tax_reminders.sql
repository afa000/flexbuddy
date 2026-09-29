alter table app_users add column remind_tax boolean not null default false;

create table tax_reminder_log (
    owner_id bigint not null references app_users(id) on delete cascade,
    due_date date not null,
    kind varchar(16) not null,
    sent_at timestamp(6) with time zone not null,
    primary key (owner_id, due_date, kind),
    constraint chk_tax_reminder_log_kind check (kind in ('WEEK_BEFORE', 'DUE_DAY'))
);
