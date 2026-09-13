package com.alwaysmoveforward.subscriptionrights.domainmodel;

import com.alwaysmoveforward.subscriptionrights.exceptions.DomainException;

import java.time.Instant;

/**
 * Aggregate root representing that a SubscriptionPlan grants a SubscriptionEntitlement,
 * within a single Application. Both the plan and the entitlement must belong to the same
 * Application -- that invariant is enforced here, at construction time, rather than
 * left to callers. A grant pins the exact plan VERSION that was current at the moment
 * it was created -- editing the plan afterward never changes what this grant refers to.
 */
public class SubscriptionPlanGrant {

    private final Long id;
    private final Long applicationId;
    private final Long subscriptionPlanId;
    private final int subscriptionPlanVersion;
    private final Long subscriptionEntitlementId;
    private final int value;
    private final Instant createdAt;

    private SubscriptionPlanGrant(Long id, Long applicationId, Long subscriptionPlanId, int subscriptionPlanVersion,
                                   Long subscriptionEntitlementId, int value, Instant createdAt) {
        this.id = id;
        this.applicationId = applicationId;
        this.subscriptionPlanId = subscriptionPlanId;
        this.subscriptionPlanVersion = subscriptionPlanVersion;
        this.subscriptionEntitlementId = subscriptionEntitlementId;
        this.value = value;
        this.createdAt = createdAt;
    }

    /**
     * Grants {@code entitlement} to {@code plan}, pinned at {@code plan}'s current version,
     * carrying {@code value}. The Application is derived from the plan; the entitlement must
     * belong to that same Application or the grant is rejected.
     */
    public static SubscriptionPlanGrant grant(SubscriptionPlan plan, SubscriptionEntitlement entitlement, int value) {
        if (plan == null) {
            throw new DomainException("A SubscriptionPlanGrant requires a SubscriptionPlan");
        }
        if (entitlement == null) {
            throw new DomainException("A SubscriptionPlanGrant requires a SubscriptionEntitlement");
        }
        if (!plan.getApplicationId().equals(entitlement.getApplicationId())) {
            throw new DomainException(
                    "Cannot grant entitlement '" + entitlement.getName() + "' (application " + entitlement.getApplicationId()
                            + ") to plan '" + plan.getName() + "' (application " + plan.getApplicationId()
                            + ") -- they belong to different applications");
        }
        entitlement.validateValue(value);
        return new SubscriptionPlanGrant(null, plan.getApplicationId(), plan.getId(), plan.getVersion(),
                entitlement.getId(), value, Instant.now());
    }

    /**
     * Reconstitutes a SubscriptionPlanGrant from persisted state. Only mappers should call this.
     */
    public static SubscriptionPlanGrant reconstitute(Long id, Long applicationId, Long subscriptionPlanId,
                                                      int subscriptionPlanVersion, Long subscriptionEntitlementId,
                                                      int value, Instant createdAt) {
        return new SubscriptionPlanGrant(id, applicationId, subscriptionPlanId, subscriptionPlanVersion,
                subscriptionEntitlementId, value, createdAt);
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
