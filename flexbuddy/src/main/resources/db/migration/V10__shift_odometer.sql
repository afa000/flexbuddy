alter table shift add column odometer_start numeric(9,1);
alter table shift add column odometer_end numeric(9,1);
alter table shift add constraint chk_shift_odometer
    check ((odometer_start is null or odometer_start >= 0)
        and (odometer_end is null or odometer_end >= 0)
        and (odometer_start is null or odometer_end is null or odometer_end >= odometer_start));
