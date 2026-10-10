-- The Google account linked to a FlexBuddy account (its stable "sub" ID), and whether the driver has a password they
-- know. Accounts created with Google get an unusable random password hash and password_set = false.
alter table app_users add column google_subject varchar(255);
alter table app_users add constraint uk_app_users_google_subject unique (google_subject);
alter table app_users add column password_set boolean not null default true;

-- Emailed codes can confirm an account deletion for drivers without a password.
alter table email_code drop constraint chk_email_code_purpose;
alter table email_code add constraint chk_email_code_purpose
    check (purpose in ('VERIFY_EMAIL', 'SIGN_IN', 'CONFIRM_DELETE'));
