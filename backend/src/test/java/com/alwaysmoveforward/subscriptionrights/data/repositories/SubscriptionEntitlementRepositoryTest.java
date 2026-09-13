package com.alwaysmoveforward.subscriptionrights.data.repositories;

import com.alwaysmoveforward.subscriptionrights.data.Entities.ApplicationEntity;
import com.alwaysmoveforward.subscriptionrights.data.dao.SubscriptionEntitlementDAO;
import com.alwaysmoveforward.subscriptionrights.data.dao.SubscriptionEntitlementLevelDAO;
import com.alwaysmoveforward.subscriptionrights.data.mapper.SubscriptionEntitlementMapper;
import com.alwaysmoveforward.subscriptionrights.domainmodel.EntitlementValueType;
import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionEntitlement;
import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionEntitlementLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises SubscriptionEntitlementRepository's save/findById against a real (H2) JPA
 * metamodel. In particular this checks that redefining an ORDINAL entitlement's levels --
 * which deletes all existing level rows then inserts a fresh set -- does not trip the
 * (entitlement, ordinal) unique constraint when a level is unchanged across the edit, the same
 * flush-ordering hazard SubscriptionPlanSetRepositoryTest exercises for plan set items.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SubscriptionEntitlementRepositoryTest {

    @Autowired
    private SubscriptionEntitlementDAO subscriptionEntitlementDAO;

    @Autowired
    private SubscriptionEntitlementLevelDAO subscriptionEntitlementLevelDAO;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private SubscriptionEntitlementRepository newRepository() {
        return new SubscriptionEntitlementRepository(subscriptionEntitlementDAO, subscriptionEntitlementLevelDAO,
                new SubscriptionEntitlementMapper());
    }

    private Long seedApplication() {
        ApplicationEntity application = new ApplicationEntity();
        application.setName("Test App " + System.nanoTime());
        application.setExternalId("ext-" + (System.nanoTime() % 1_000_000L));
        application.setDescription("d");
        application.setCreatedAt(Instant.now());
        entityManager.persist(application);
        entityManager.flush();
        return application.getId();
    }

    @Test
    void redefiningLevelsWithAnUnchangedOrdinalDoesNotViolateUniqueConstraints() {
        SubscriptionEntitlementRepository repository = newRepository();
        Long applicationId = seedApplication();

        SubscriptionEntitlement created = SubscriptionEntitlement.create(applicationId, "automation-level",
                "Automation Level", EntitlementValueType.ORDINAL,
                List.of(SubscriptionEntitlementLevel.of(0, "None"), SubscriptionEntitlementLevel.of(1, "Manual")));
        SubscriptionEntitlement saved = repository.save(created);
        entityManager.flush();
        entityManager.clear();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getLevels()).hasSize(2);

        // Redefine: ordinal 0 stays exactly the same; only ordinal 1's label changes and a new
        // ordinal 2 is added. If save() lets Hibernate flush the re-insert of (entitlement, 0)
        // before the delete of the old (entitlement, 0) row actually lands, this throws a unique
        // constraint violation.
        SubscriptionEntitlement reloaded = repository.findById(saved.getId()).orElseThrow();
        reloaded.redefineValueType(EntitlementValueType.ORDINAL, List.of(
                SubscriptionEntitlementLevel.of(0, "None"),
                SubscriptionEntitlementLevel.of(1, "Automated"),
                SubscriptionEntitlementLevel.of(2, "AI Automated")));
        SubscriptionEntitlement updated = repository.save(reloaded);
        entityManager.flush();
        entityManager.clear();

        assertThat(updated.getLevels()).hasSize(3);
        SubscriptionEntitlement reloadedAgain = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloadedAgain.getLevels()).extracting(SubscriptionEntitlementLevel::getLabel)
                .containsExactlyInAnyOrder("None", "Automated", "AI Automated");
    }

    @Test
    void switchingFromOrdinalToNumericClearsLevels() {
        SubscriptionEntitlementRepository repository = newRepository();
        Long applicationId = seedApplication();

        SubscriptionEntitlement created = SubscriptionEntitlement.create(applicationId, "automation-level",
                "Automation Level", EntitlementValueType.ORDINAL, List.of(SubscriptionEntitlementLevel.of(0, "None")));
        SubscriptionEntitlement saved = repository.save(created);
        entityManager.flush();
        entityManager.clear();

        SubscriptionEntitlement reloaded = repository.findById(saved.getId()).orElseThrow();
        reloaded.redefineValueType(EntitlementValueType.NUMERIC, List.of());
        SubscriptionEntitlement updated = repository.save(reloaded);
        entityManager.flush();
        entityManager.clear();

        assertThat(updated.getLevels()).isEmpty();
        assertThat(repository.findById(saved.getId()).orElseThrow().getLevels()).isEmpty();
    }

    @Test
    void deleteRemovesEntitlementAndItsLevels() {
        SubscriptionEntitlementRepository repository = newRepository();
        Long applicationId = seedApplication();

        SubscriptionEntitlement created = SubscriptionEntitlement.create(applicationId, "automation-level",
                "Automation Level", EntitlementValueType.ORDINAL, List.of(SubscriptionEntitlementLevel.of(0, "None")));
        SubscriptionEntitlement saved = repository.save(created);
        entityManager.flush();
        entityManager.clear();

        repository.deleteById(saved.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findById(saved.getId())).isEmpty();
        assertThat(subscriptionEntitlementLevelDAO.findAllBySubscriptionEntitlementId(saved.getId())).isEmpty();
    }
}
