-- Drivers can turn off the Home prompts for blocks without miles; everyone keeps them until they choose.
alter table app_users add column ask_missing_miles boolean not null default true;
