-- Change-only sync for the native app (FB-033). updated_at keeps its meaning for the website ("last edited" and the
-- edit conflict check); sync_changed_at also moves when a block is deleted or restored.
alter table shift add column sync_changed_at timestamp(6) with time zone;

update shift set sync_changed_at = greatest(updated_at, coalesce(deleted_at, updated_at));

alter table shift alter column sync_changed_at set not null;

create index idx_shift_owner_sync on shift (owner_id, sync_changed_at);

-- When blocks were removed for good or replaced, so a phone that synced earlier reloads everything once.
alter table app_users add column sync_reset_at timestamp(6) with time zone;
