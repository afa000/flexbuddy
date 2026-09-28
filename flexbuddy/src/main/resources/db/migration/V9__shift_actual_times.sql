alter table shift add column actual_start time(0);
alter table shift add column actual_end time(0);
alter table shift add constraint chk_shift_actual_times
    check (actual_end is null or actual_start is not null);
