-- Company HR policy: ERP UI session idle timeout (minutes). Session security only —
-- does NOT drive attendance auto check-out (see max_shift_checkout_grace_minutes).
-- NULL/0 = disabled (Off). Allowed UI values: 15, 20, or 30.
ALTER TABLE `companies`
  ADD COLUMN `session_idle_timeout_minutes` INT NULL DEFAULT NULL;
