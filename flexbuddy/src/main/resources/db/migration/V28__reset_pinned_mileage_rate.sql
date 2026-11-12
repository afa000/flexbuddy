-- The Vehicle costs form used to save the rate it displayed, so any driver who saved it had the default pinned as their
-- own rate. 0.70 was the only default ever shipped, so a stored 0.700 is that pinned default, not a rate the driver
-- chose. Reset it to follow the IRS rate for each block's date (owner decision, 2026-10-30). A driver who really wants
-- 0.70 can set it again.
update app_users set mileage_rate = null where mileage_rate = 0.700;
