alter table shift add column late_forfeit boolean not null default false;

alter table app_users add column forfeit_cutoff_minutes integer not null default 45;
alter table app_users add constraint chk_forfeit_cutoff_minutes
    check (forfeit_cutoff_minutes between 0 and 720);
