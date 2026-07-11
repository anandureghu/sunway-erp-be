-- The global employee-number counter is replaced by per-company sequences in
-- document_sequence (see V20260648). Drop the now-unused legacy table; its
-- startup re-creation (DatabaseInitRunner) has also been removed.
DROP TABLE IF EXISTS `employee_no_seq`;
