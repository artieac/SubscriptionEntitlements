-- =============================================================================
-- Delta: adds SubscriptionEntitlements.DefaultValue, and splits
-- SubscriptionEntitlementLevels.Label into Name (a stable, code-facing identifier) and
-- DisplayName (the UI-facing text) -- the same Name/DisplayName split SubscriptionEntitlements
-- itself already has.
--
-- DefaultValue is the value a plan version is treated as granting for this entitlement when it
-- has no explicit SubscriptionPlanGrant row -- e.g. 0 for a BOOLEAN "off", 0 for a NUMERIC quota,
-- or a specific level's ordinal for an ORDINAL entitlement. It must fit valueType/levels the same
-- way any other grant value must (SubscriptionEntitlement#validateValue) -- enforced in
-- application code, not by a CHECK constraint here, the same way ValueType's rules are (see delta
-- 2's notes).
--
-- Name must be UPPER_SNAKE_CASE (SubscriptionEntitlementLevel's NAME_PATTERN: start with a
-- letter, then letters/digits/underscores) and unique within its entitlement -- enforced in
-- application code plus the UNIQUE constraint added below, the same way Ordinal's uniqueness
-- already is.
--
-- Run this against a database already created from schema.sql (and deltas 1-2). This script only
-- defines the change -- like schema.sql, it is not executed as part of any build/deploy step;
-- run it manually.
-- =============================================================================

USE subscriptionentitlements;

ALTER TABLE SubscriptionEntitlements ADD COLUMN DefaultValue INT NULL AFTER ValueType;

-- Backfills every existing row to 0. For a BOOLEAN or NUMERIC entitlement this is a valid,
-- least-surprising default ("off" / no quota). For an ORDINAL entitlement, 0 only fits if that
-- entitlement actually defines a level with ordinal 0 -- revisit any that don't, through the UI,
-- after running this script.
UPDATE SubscriptionEntitlements
SET DefaultValue = 0
WHERE DefaultValue IS NULL;

ALTER TABLE SubscriptionEntitlements MODIFY COLUMN DefaultValue INT NOT NULL;

-- Label becomes DisplayName (unchanged content, just renamed) -- and a new Name column is added
-- alongside it, following the same identifier vs. display split SubscriptionEntitlements has.
ALTER TABLE SubscriptionEntitlementLevels CHANGE COLUMN Label DisplayName VARCHAR(255) NOT NULL;
ALTER TABLE SubscriptionEntitlementLevels ADD COLUMN Name VARCHAR(255) NULL AFTER Ordinal;

-- Backfills every existing row's Name from its DisplayName (upper-cased, non-alphanumeric runs
-- collapsed to a single underscore). This is a best-effort derivation, not a guaranteed-valid
-- UPPER_SNAKE_CASE identifier -- e.g. a DisplayName starting with a digit produces a Name that
-- still starts with a digit, which the application's NAME_PATTERN rejects. Revisit any level
-- this doesn't produce a clean identifier for, through the UI, after running this script.
UPDATE SubscriptionEntitlementLevels
SET Name = UPPER(REGEXP_REPLACE(TRIM(DisplayName), '[^A-Za-z0-9]+', '_'))
WHERE Name IS NULL;

ALTER TABLE SubscriptionEntitlementLevels MODIFY COLUMN Name VARCHAR(255) NOT NULL;

ALTER TABLE SubscriptionEntitlementLevels
    ADD CONSTRAINT UQ_SubscriptionEntitlementLevels_Entitlement_Name UNIQUE (SubscriptionEntitlementId, Name);
