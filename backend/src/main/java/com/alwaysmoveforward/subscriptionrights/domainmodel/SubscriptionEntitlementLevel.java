package com.alwaysmoveforward.subscriptionrights.domainmodel;

import com.alwaysmoveforward.subscriptionrights.exceptions.DomainException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One named level of an ORDINAL SubscriptionEntitlement (e.g. ordinal 2, name "AUTOMATED",
 * displayName "Automated"). A value object -- it has no identity of its own outside the
 * SubscriptionEntitlement aggregate that owns it, and carries no persistence id at the domain level.
 */
public class SubscriptionEntitlementLevel {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    private final int ordinal;
    private final String name;
    private final String displayName;

    private SubscriptionEntitlementLevel(int ordinal, String name, String displayName) {
        this.ordinal = ordinal;
        this.name = name;
        this.displayName = displayName;
    }

    public static SubscriptionEntitlementLevel of(int ordinal, String name, String displayName) {
        if (ordinal < 0) {
            throw new DomainException("SubscriptionEntitlementLevel ordinal must not be negative");
        }
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new DomainException(
                    "SubscriptionEntitlementLevel name must be UPPER_SNAKE_CASE (start with a letter, then letters/digits/underscores), got '"
                            + name + "'");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new DomainException("SubscriptionEntitlementLevel displayName must not be blank");
        }
        return new SubscriptionEntitlementLevel(ordinal, name, displayName);
    }

    public int getOrdinal() {
        return ordinal;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
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
        return ordinal == that.ordinal && Objects.equals(name, that.name) && Objects.equals(displayName, that.displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ordinal, name, displayName);
    }
}
