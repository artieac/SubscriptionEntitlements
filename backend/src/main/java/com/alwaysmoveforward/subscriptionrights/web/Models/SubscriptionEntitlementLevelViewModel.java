package com.alwaysmoveforward.subscriptionrights.web.Models;

import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionEntitlementLevel;

public class SubscriptionEntitlementLevelViewModel {

    private final int ordinal;
    private final String label;

    public SubscriptionEntitlementLevelViewModel(int ordinal, String label) {
        this.ordinal = ordinal;
        this.label = label;
    }

    public static SubscriptionEntitlementLevelViewModel from(SubscriptionEntitlementLevel level) {
        return new SubscriptionEntitlementLevelViewModel(level.getOrdinal(), level.getLabel());
    }

    public int getOrdinal() {
        return ordinal;
    }

    public String getLabel() {
        return label;
    }
}
