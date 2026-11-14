-- Native app expenses and payouts (NA-06).

-- Change-only expense sync: like shift.sync_changed_at, this also moves when an expense is deleted or restored.
alter table expense add column sync_changed_at timestamp(6) with time zone;

update expense set sync_changed_at = greatest(updated_at, coalesce(deleted_at, updated_at));

alter table expense alter column sync_changed_at set not null;

create index idx_expense_owner_sync on expense (owner_id, sync_changed_at);

-- A receipt now points at a block, an expense or a payout date: exactly one of them. Still no foreign keys, so a
-- delayed retry after permanent deletion can never recreate the record, and still no request body.
alter table api_write_receipt alter column shift_id drop not null;
alter table api_write_receipt add column expense_id bigint;
alter table api_write_receipt add column payout_date date;
alter table api_write_receipt add constraint chk_api_write_receipt_target check (
    (case when shift_id is null then 0 else 1 end)
    + (case when expense_id is null then 0 else 1 end)
    + (case when payout_date is null then 0 else 1 end) = 1);
