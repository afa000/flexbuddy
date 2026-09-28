alter table app_users add column payout_days varchar(80) not null default 'TUESDAY,FRIDAY';
alter table app_users add column payout_lag_days integer not null default 1;
alter table app_users add constraint chk_payout_lag_days check (payout_lag_days between 0 and 14);
