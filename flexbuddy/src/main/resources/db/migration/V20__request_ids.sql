alter table shift add column create_request_id varchar(36);
alter table shift add column last_request_id varchar(36);
alter table shift add constraint uk_shift_owner_create_request unique (owner_id, create_request_id);

alter table expense add column create_request_id varchar(36);
alter table expense add constraint uk_expense_owner_create_request unique (owner_id, create_request_id);
