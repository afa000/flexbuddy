alter table app_users add column weekly_goal numeric(10,2);
alter table app_users add column monthly_goal numeric(10,2);
alter table app_users add column goal_basis varchar(10) not null default 'GROSS';
alter table app_users add constraint chk_app_users_goals
    check ((weekly_goal is null or weekly_goal > 0)
        and (monthly_goal is null or monthly_goal > 0)
        and goal_basis in ('GROSS', 'NET'));
