alter table app_users add column setup_dismissed_at timestamp(6) with time zone;
-- Drivers who already logged blocks are set up; only accounts without any see the card.
update app_users set setup_dismissed_at = now()
where exists (select 1 from shift where shift.owner_id = app_users.id);
