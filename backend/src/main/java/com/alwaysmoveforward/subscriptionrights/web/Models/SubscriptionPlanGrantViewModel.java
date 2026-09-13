package com.alwaysmoveforward.subscriptionrights.web.Models;

import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionEntitlement;
import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionPlanGrant;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SubscriptionPlanGrantViewModel {

    private final Long id;
    private final Long applicationId;
    private final Long subscriptionPlanId;
    private final int subscriptionPlanVersion;
    private final Long subscriptionEntitlementId;
    private final int value;
    private final boolean defaulted;
    private final Instant createdAt;

    public SubscriptionPlanGrantViewModel(Long id, Long applicationId, Long subscriptionPlanId,
                                           int subscriptionPlanVersion, Long subscriptionEntitlementId, int value,
                                           boolean defaulted, Instant createdAt) {
        this.id = id;
        this.applicationId = applicationId;
        this.subscriptionPlanId = subscriptionPlanId;
        this.subscriptionPlanVersion = subscriptionPlanVersion;
        this.subscriptionEntitlementId = subscriptionEntitlementId;
        this.value = value;
        this.defaulted = defaulted;
        this.createdAt = createdAt;
    }

    public static SubscriptionPlanGrantViewModel from(SubscriptionPlanGrant grant) {
        return new SubscriptionPlanGrantViewModel(grant.getId(), grant.getApplicationId(),
                grant.getSubscriptionPlanId(), grant.getSubscriptionPlanVersion(), grant.getSubscriptionEntitlementId(),
                grant.getValue(), false, grant.getCreatedAt());
    }

    /**
     * {@code grants} as view models, plus one synthesized (defaulted=true, id=null) entry per
     * entitlement that has no explicit grant at a (plan, version) pair actually present among
     * {@code grants} -- carrying that entitlement's configured {@link SubscriptionEntitlement#getDefaultValue()}.
     * A (plan, version) pair with zero explicit grants at all never appears here, since there is
     * nothing in {@code grants} to key the synthesis off of.
     */
    public static List<SubscriptionPlanGrantViewModel> listIncludingDefaults(Long applicationId,
                                                                              List<SubscriptionPlanGrant> grants,
                                                                              List<SubscriptionEntitlement> entitlements) {
        record PlanVersion(Long subscriptionPlanId, int version) {
        }

        List<SubscriptionPlanGrantViewModel> result = new ArrayList<>();
        Map<PlanVersion, Set<Long>> grantedEntitlementIdsByPlanVersion = new HashMap<>();
        for (SubscriptionPlanGrant grant : grants) {
            result.add(from(grant));
            grantedEntitlementIdsByPlanVersion
                    .computeIfAbsent(new PlanVersion(grant.getSubscriptionPlanId(), grant.getSubscriptionPlanVersion()),
                            key -> new HashSet<>())
                    .add(grant.getSubscriptionEntitlementId());
        }
        for (Map.Entry<PlanVersion, Set<Long>> entry : grantedEntitlementIdsByPlanVersion.entrySet()) {
            for (SubscriptionEntitlement entitlement : entitlements) {
                if (!entry.getValue().contains(entitlement.getId())) {
                    result.add(new SubscriptionPlanGrantViewModel(null, applicationId, entry.getKey().subscriptionPlanId(),
                            entry.getKey().version(), entitlement.getId(), entitlement.getDefaultValue(), true, null));
                }
            }
        }
        return result;
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Long getSubscriptionPlanId() {
        return subscriptionPlanId;
    }

    public int getSubscriptionPlanVersion() {
        return subscriptionPlanVersion;
    }

    public Long getSubscriptionEntitlementId() {
        return subscriptionEntitlementId;
    }

    public int getValue() {
        return value;
    }

    public boolean isDefaulted() {
        return defaulted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
