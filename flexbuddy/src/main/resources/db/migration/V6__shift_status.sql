alter table shift add column status varchar(20) not null default 'COMPLETED';
alter table shift add column status_changed_at timestamp(6) with time zone;
alter table shift add constraint chk_shift_status
    check (status in ('SCHEDULED', 'COMPLETED', 'CANCELLED', 'FORFEITED'));

create index idx_shift_owner_status_date on shift (owner_id, status, date);
