update shift
set station = coalesce(nullif(btrim(station), ''), 'Unknown station'),
    date = coalesce(date, (created_at at time zone 'UTC')::date, current_date),
    start_time = coalesce(start_time, end_time, time '00:00'),
    end_time = coalesce(end_time, start_time, time '00:00'),
    base_pay = coalesce(base_pay, 0.00),
    tips = coalesce(tips, 0.00)
where station is null or btrim(station) = ''
   or date is null or start_time is null or end_time is null
   or base_pay is null or tips is null;

alter table shift alter column station set not null;
alter table shift alter column date set not null;
alter table shift alter column start_time set not null;
alter table shift alter column end_time set not null;
alter table shift alter column base_pay set not null;
alter table shift alter column tips set not null;
