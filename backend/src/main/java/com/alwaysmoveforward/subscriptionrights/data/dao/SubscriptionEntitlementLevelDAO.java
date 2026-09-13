package com.alwaysmoveforward.subscriptionrights.data.dao;

import com.alwaysmoveforward.subscriptionrights.data.Entities.SubscriptionEntitlementLevelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Internal to the data layer -- used only by SubscriptionEntitlementRepository. There is no
 * SubscriptionEntitlementLevelRepository; levels are owned entirely by the SubscriptionEntitlement
 * aggregate root.
 */
public interface SubscriptionEntitlementLevelDAO extends JpaRepository<SubscriptionEntitlementLevelEntity, Long> {

    List<SubscriptionEntitlementLevelEntity> findAllBySubscriptionEntitlementId(Long subscriptionEntitlementId);

    void deleteAllBySubscriptionEntitlementId(Long subscriptionEntitlementId);
}
