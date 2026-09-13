-- =============================================================================
-- Delta: adds SubscriptionEntitlements.ValueType and SubscriptionEntitlementLevels
--
-- ValueType records how to interpret SubscriptionPlanGrants.Value for a given entitlement:
--   BOOLEAN -- value must be 0 or 1
--   NUMERIC -- unrestricted int, whatever convention (e.g. an "unlimited" sentinel) callers
--             already use -- this is the pre-existing, unchanged behavior
--   ORDINAL -- value must match one of that entitlement's SubscriptionEntitlementLevels
-- Format/allowed-values validation is enforced in application code (SubscriptionEntitlement
-- domain model / SubscriptionEntitlementService), not by a CHECK constraint here, the same
-- way schema.sql already leaves Name's "non-blank" rule to application code.
--
-- SubscriptionEntitlementLevels only has rows for ORDINAL entitlements -- application code
-- enforces that BOOLEAN/NUMERIC entitlements have none, the same way schema.sql's other
-- cross-row invariants (no overlapping SubscriptionPlanSet date ranges, etc.) are enforced in
-- code rather than in the database.
--
-- Run this against a database already created from schema.sql (and delta 1). This script only
-- defines the change -- like schema.sql, it is not executed as part of any build/deploy step;
-- run it manually.
-- =============================================================================

USE subscriptionentitlements;

ALTER TABLE SubscriptionEntitlements ADD COLUMN ValueType VARCHAR(20) NULL AFTER DisplayName;

-- Backfills every existing row to NUMERIC, the least-restrictive type -- no existing row
-- anywhere records whether it was actually meant as a boolean or an ordinal level, so this
-- preserves current behavior exactly. Retype individual entitlements afterward through the UI.
UPDATE SubscriptionEntitlements
SET ValueType = 'NUMERIC'
WHERE ValueType IS NULL;

ALTER TABLE SubscriptionEntitlements MODIFY COLUMN ValueType VARCHAR(20) NOT NULL;

CREATE TABLE SubscriptionEntitlementLevels
(
    Id                        BIGINT       NOT NULL AUTO_INCREMENT,
    SubscriptionEntitlementId BIGINT       NOT NULL,
    Ordinal                   INT          NOT NULL,
    Label                     VARCHAR(255) NOT NULL,

    PRIMARY KEY (Id),
    CONSTRAINT FK_SubscriptionEntitlementLevels_SubscriptionEntitlement
        FOREIGN KEY (SubscriptionEntitlementId) REFERENCES SubscriptionEntitlements (Id),
    CONSTRAINT UQ_SubscriptionEntitlementLevels_Entitlement_Ordinal
        UNIQUE (SubscriptionEntitlementId, Ordinal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_SubscriptionEntitlementLevels_SubscriptionEntitlementId
    ON SubscriptionEntitlementLevels (SubscriptionEntitlementId);
