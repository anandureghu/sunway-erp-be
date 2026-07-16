-- transfers had a global unique constraint on transfer_code and no company
-- relation at all, and was confirmed dead: no repository, service, or
-- controller anywhere in the codebase references it (only its own entity
-- file did). Remove the unused table rather than fix multitenancy on code
-- that isn't reachable.

DROP TABLE IF EXISTS transfers;
