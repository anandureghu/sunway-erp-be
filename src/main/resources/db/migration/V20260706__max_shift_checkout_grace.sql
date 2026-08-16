-- Company HR policy: grace minutes after max shift (standard hours + OT cap)
-- before automatic attendance check-out. NULL/0 = check out at the cap with no grace.
-- Allowed UI values: 15, 20, or 30.
ALTER TABLE `companies`
  ADD COLUMN `max_shift_checkout_grace_minutes` INT NULL DEFAULT NULL;
