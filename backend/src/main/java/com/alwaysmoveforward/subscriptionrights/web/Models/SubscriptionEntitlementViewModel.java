package com.alwaysmoveforward.subscriptionrights.web.Models;

import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionEntitlement;

import java.time.Instant;
import java.util.List;

public class SubscriptionEntitlementViewModel {

    private final Long id;
    private final Long applicationId;
    private final String name;
    private final String displayName;
    private final String valueType;
    private final List<SubscriptionEntitlementLevelViewModel> levels;
    private final int defaultValue;
    private final Instant createdAt;
    private final Instant updatedAt;

    public SubscriptionEntitlementViewModel(Long id, Long applicationId, String name, String displayName,
                                             String valueType, List<SubscriptionEntitlementLevelViewModel> levels,
                                             int defaultValue, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.applicationId = applicationId;
        this.name = name;
        this.displayName = displayName;
        this.valueType = valueType;
        this.levels = levels;
        this.defaultValue = defaultValue;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static SubscriptionEntitlementViewModel from(SubscriptionEntitlement entitlement) {
        List<SubscriptionEntitlementLevelViewModel> levels = entitlement.getLevels().stream()
                .map(SubscriptionEntitlementLevelViewModel::from).toList();
        return new SubscriptionEntitlementViewModel(entitlement.getId(), entitlement.getApplicationId(), entitlement.getName(),
                entitlement.getDisplayName(), entitlement.getValueType().name(), levels, entitlement.getDefaultValue(),
                entitlement.getCreatedAt(), entitlement.getUpdatedAt());
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getValueType() {
        return valueType;
    }

    public List<SubscriptionEntitlementLevelViewModel> getLevels() {
        return levels;
    }

    public int getDefaultValue() {
        return defaultValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
