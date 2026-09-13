package com.alwaysmoveforward.subscriptionrights.domainmodel;

import com.alwaysmoveforward.subscriptionrights.exceptions.DomainException;

import java.util.Objects;

/**
 * One named level of an ORDINAL SubscriptionEntitlement (e.g. ordinal 2, label "Automated").
 * A value object -- it has no identity of its own outside the SubscriptionEntitlement aggregate
 * that owns it, and carries no persistence id at the domain level.
 */
public class SubscriptionEntitlementLevel {

    private final int ordinal;
    private final String label;

    private SubscriptionEntitlementLevel(int ordinal, String label) {
        this.ordinal = ordinal;
        this.label = label;
    }

    public static SubscriptionEntitlementLevel of(int ordinal, String label) {
        if (ordinal < 0) {
            throw new DomainException("SubscriptionEntitlementLevel ordinal must not be negative");
        }
        if (label == null || label.isBlank()) {
            throw new DomainException("SubscriptionEntitlementLevel label must not be blank");
        }
        return new SubscriptionEntitlementLevel(ordinal, label);
    }

    public int getOrdinal() {
        return ordinal;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SubscriptionEntitlementLevel)) {
            return false;
        }
        SubscriptionEntitlementLevel that = (SubscriptionEntitlementLevel) o;
        return ordinal == that.ordinal && Objects.equals(label, that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ordinal, label);
    }
}
