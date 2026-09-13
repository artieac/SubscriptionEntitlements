package com.alwaysmoveforward.subscriptionrights.web.Models;

import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionEntitlementLevel;

public class SubscriptionEntitlementLevelViewModel {

    private final int ordinal;
    private final String name;
    private final String displayName;

    public SubscriptionEntitlementLevelViewModel(int ordinal, String name, String displayName) {
        this.ordinal = ordinal;
        this.name = name;
        this.displayName = displayName;
    }

    public static SubscriptionEntitlementLevelViewModel from(SubscriptionEntitlementLevel level) {
        return new SubscriptionEntitlementLevelViewModel(level.getOrdinal(), level.getName(), level.getDisplayName());
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
}
