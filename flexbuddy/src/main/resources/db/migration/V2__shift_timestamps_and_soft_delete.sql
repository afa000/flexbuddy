alter table shift add column created_at timestamp(6) with time zone;
alter table shift add column updated_at timestamp(6) with time zone;
alter table shift add column deleted_at timestamp(6) with time zone;
alter table shift add column delete_batch varchar(36);

update shift
set created_at = now(), updated_at = now()
where created_at is null or updated_at is null;

alter table shift alter column created_at set not null;
alter table shift alter column updated_at set not null;

create index idx_shift_owner_deleted on shift (owner_id, deleted_at);
