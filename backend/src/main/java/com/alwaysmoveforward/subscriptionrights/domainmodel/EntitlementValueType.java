package com.alwaysmoveforward.subscriptionrights.domainmodel;

/**
 * What kind of value a SubscriptionEntitlement's grants carry, and therefore how to interpret
 * SubscriptionPlanGrant#getValue for that entitlement.
 */
public enum EntitlementValueType {
    /** value must be 0 or 1. */
    BOOLEAN,
    /** value is an unrestricted int -- whatever convention (e.g. an "unlimited" sentinel) callers already use. */
    NUMERIC,
    /** value must match one of the entitlement's SubscriptionEntitlementLevel ordinals. */
    ORDINAL
}
