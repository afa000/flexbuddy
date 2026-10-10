-- The driver's chosen language for pages, email and reminders: 'en' or 'es'; null follows the device.
alter table app_users add column language varchar(10);
alter table app_users add constraint chk_app_users_language check (language is null or language in ('en', 'es'));
