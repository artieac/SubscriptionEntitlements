package com.alwaysmoveforward.subscriptionrights.data.repositories;

import com.alwaysmoveforward.subscriptionrights.data.Entities.SubscriptionEntitlementEntity;
import com.alwaysmoveforward.subscriptionrights.data.Entities.SubscriptionEntitlementLevelEntity;
import com.alwaysmoveforward.subscriptionrights.data.dao.SubscriptionEntitlementDAO;
import com.alwaysmoveforward.subscriptionrights.data.dao.SubscriptionEntitlementLevelDAO;
import com.alwaysmoveforward.subscriptionrights.data.mapper.SubscriptionEntitlementMapper;
import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionEntitlement;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * The only layer allowed to touch SubscriptionEntitlementEntity/SubscriptionEntitlementLevelEntity.
 * Levels are always read/written as part of the whole SubscriptionEntitlement aggregate -- there
 * is no separate levels repository.
 */
@Repository
public class SubscriptionEntitlementRepository {

    private final SubscriptionEntitlementDAO subscriptionEntitlementDAO;
    private final SubscriptionEntitlementLevelDAO subscriptionEntitlementLevelDAO;
    private final SubscriptionEntitlementMapper subscriptionEntitlementMapper;

    public SubscriptionEntitlementRepository(SubscriptionEntitlementDAO subscriptionEntitlementDAO,
                                              SubscriptionEntitlementLevelDAO subscriptionEntitlementLevelDAO,
                                              SubscriptionEntitlementMapper subscriptionEntitlementMapper) {
        this.subscriptionEntitlementDAO = subscriptionEntitlementDAO;
        this.subscriptionEntitlementLevelDAO = subscriptionEntitlementLevelDAO;
        this.subscriptionEntitlementMapper = subscriptionEntitlementMapper;
    }

    public List<SubscriptionEntitlement> findByApplicationId(Long applicationId) {
        return subscriptionEntitlementDAO.findByApplicationId(applicationId).stream()
                .map(this::assemble).toList();
    }

    public Optional<SubscriptionEntitlement> findById(Long id) {
        return subscriptionEntitlementDAO.findById(id).map(this::assemble);
    }

    /**
     * Upserts {@code subscriptionEntitlement}: inserts a new row (plus its level rows) if it has
     * no id, otherwise updates the existing row and replaces its level rows wholesale
     * (delete-all-then-insert, rather than diffing -- this is a low-frequency admin operation,
     * not a hot path).
     *
     * The delete is explicitly flushed before the inserts are queued: Hibernate's default flush
     * ordering runs entity insertions before deletions, so without forcing this delete to land
     * first, re-inserting an unchanged (entitlement, ordinal) pair trips the unique constraint.
     */
    @Transactional
    public SubscriptionEntitlement save(SubscriptionEntitlement subscriptionEntitlement) {
        SubscriptionEntitlementEntity saved = subscriptionEntitlementDAO.save(subscriptionEntitlementMapper.toEntity(subscriptionEntitlement));

        subscriptionEntitlementLevelDAO.deleteAllBySubscriptionEntitlementId(saved.getId());
        subscriptionEntitlementLevelDAO.flush();
        List<SubscriptionEntitlementLevelEntity> levelEntities =
                subscriptionEntitlementMapper.toLevelEntities(saved.getId(), subscriptionEntitlement);
        subscriptionEntitlementLevelDAO.saveAll(levelEntities);

        return assemble(saved);
    }

    @Transactional
    public void deleteById(Long id) {
        subscriptionEntitlementLevelDAO.deleteAllBySubscriptionEntitlementId(id);
        subscriptionEntitlementDAO.deleteById(id);
    }

    private SubscriptionEntitlement assemble(SubscriptionEntitlementEntity entity) {
        List<SubscriptionEntitlementLevelEntity> levelEntities =
                subscriptionEntitlementLevelDAO.findAllBySubscriptionEntitlementId(entity.getId());
        return subscriptionEntitlementMapper.toDomainModel(entity, levelEntities);
    }
}
