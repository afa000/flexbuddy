alter table shift add column stop_count integer;
alter table shift add column package_count integer;
alter table shift add column return_count integer;
alter table shift add constraint chk_shift_route_counts
    check ((stop_count is null or stop_count >= 0)
        and (package_count is null or package_count >= 0)
        and (return_count is null or return_count >= 0)
        and (return_count is null or package_count is null or return_count <= package_count));
