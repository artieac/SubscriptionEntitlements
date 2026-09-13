package com.alwaysmoveforward.subscriptionrights.services;

/**
 * Plain input carrier for one level in a SubscriptionEntitlementRequest -- the controller maps
 * request level DTOs into these before calling SubscriptionEntitlementService, which is
 * responsible for turning them into validated SubscriptionEntitlementLevel domain objects.
 */
public record SubscriptionEntitlementLevelInput(int ordinal, String label) {
}
