package com.alwaysmoveforward.subscriptionrights.data.mapper;

import com.alwaysmoveforward.subscriptionrights.data.Entities.SubscriptionEntitlementEntity;
import com.alwaysmoveforward.subscriptionrights.data.Entities.SubscriptionEntitlementLevelEntity;
import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionEntitlement;
import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionEntitlementLevel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps SubscriptionEntitlement <-> SubscriptionEntitlementEntity/SubscriptionEntitlementLevelEntity.
 * Used only by SubscriptionEntitlementRepository -- levels are part of the aggregate, not a
 * separately-mapped concern callers ever see.
 */
@Component
public class SubscriptionEntitlementMapper {

    public SubscriptionEntitlement toDomainModel(SubscriptionEntitlementEntity entity,
                                                  List<SubscriptionEntitlementLevelEntity> levelEntities) {
        if (entity == null) {
            return null;
        }
        List<SubscriptionEntitlementLevel> levels = levelEntities.stream()
                .map(this::toLevelDomainModel)
                .toList();
        return SubscriptionEntitlement.reconstitute(entity.getId(), entity.getApplicationId(), entity.getName(),
                entity.getDisplayName(), entity.getValueType(), levels, entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public SubscriptionEntitlementEntity toEntity(SubscriptionEntitlement domainModel) {
        SubscriptionEntitlementEntity entity = new SubscriptionEntitlementEntity();
        entity.setId(domainModel.getId());
        entity.setApplicationId(domainModel.getApplicationId());
        entity.setName(domainModel.getName());
        entity.setDisplayName(domainModel.getDisplayName());
        entity.setValueType(domainModel.getValueType());
        entity.setCreatedAt(domainModel.getCreatedAt());
        entity.setUpdatedAt(domainModel.getUpdatedAt());
        return entity;
    }

    public List<SubscriptionEntitlementLevelEntity> toLevelEntities(Long subscriptionEntitlementId, SubscriptionEntitlement domainModel) {
        return domainModel.getLevels().stream().map(level -> {
            SubscriptionEntitlementLevelEntity entity = new SubscriptionEntitlementLevelEntity();
            entity.setSubscriptionEntitlementId(subscriptionEntitlementId);
            entity.setOrdinal(level.getOrdinal());
            entity.setLabel(level.getLabel());
            return entity;
        }).toList();
    }

    private SubscriptionEntitlementLevel toLevelDomainModel(SubscriptionEntitlementLevelEntity entity) {
        return SubscriptionEntitlementLevel.of(entity.getOrdinal(), entity.getLabel());
    }
}
