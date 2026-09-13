package com.alwaysmoveforward.subscriptionrights.services;

import com.alwaysmoveforward.subscriptionrights.data.Entities.ApplicationEntity;
import com.alwaysmoveforward.subscriptionrights.data.dao.ApplicationDAO;
import com.alwaysmoveforward.subscriptionrights.data.dao.SubscriptionEntitlementDAO;
import com.alwaysmoveforward.subscriptionrights.data.dao.SubscriptionEntitlementLevelDAO;
import com.alwaysmoveforward.subscriptionrights.data.dao.SubscriptionPlanDAO;
import com.alwaysmoveforward.subscriptionrights.data.dao.SubscriptionPlanGrantDAO;
import com.alwaysmoveforward.subscriptionrights.data.dao.SubscriptionPlanIdSequenceDAO;
import com.alwaysmoveforward.subscriptionrights.data.mapper.ApplicationMapper;
import com.alwaysmoveforward.subscriptionrights.data.mapper.SubscriptionEntitlementMapper;
import com.alwaysmoveforward.subscriptionrights.data.mapper.SubscriptionPlanGrantMapper;
import com.alwaysmoveforward.subscriptionrights.data.mapper.SubscriptionPlanMapper;
import com.alwaysmoveforward.subscriptionrights.data.repositories.ApplicationRepository;
import com.alwaysmoveforward.subscriptionrights.data.repositories.SubscriptionEntitlementRepository;
import com.alwaysmoveforward.subscriptionrights.data.repositories.SubscriptionPlanGrantRepository;
import com.alwaysmoveforward.subscriptionrights.data.repositories.SubscriptionPlanRepository;
import com.alwaysmoveforward.subscriptionrights.domainmodel.EntitlementValueType;
import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionEntitlement;
import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionEntitlementLevel;
import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionPlan;
import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionPlanGrant;
import com.alwaysmoveforward.subscriptionrights.exceptions.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises SubscriptionPlanGrantService#replaceGrantsForPlan against a real (H2) JPA
 * metamodel, wiring the real repositories/services by hand (this is a service, not a JPA
 * repository, so plain @DataJpaTest doesn't provide it as a bean the way it does DAOs) --
 * specifically to settle whether createNewVersion=false genuinely avoids bumping the plan's
 * version, since that's exactly what's being reported as broken.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SubscriptionPlanGrantServiceTest {

    @Autowired
    private ApplicationDAO applicationDAO;
    @Autowired
    private SubscriptionPlanDAO subscriptionPlanDAO;
    @Autowired
    private SubscriptionPlanIdSequenceDAO subscriptionPlanIdSequenceDAO;
    @Autowired
    private SubscriptionEntitlementDAO subscriptionEntitlementDAO;
    @Autowired
    private SubscriptionEntitlementLevelDAO subscriptionEntitlementLevelDAO;
    @Autowired
    private SubscriptionPlanGrantDAO subscriptionPlanGrantDAO;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private SubscriptionPlanGrantService newGrantService() {
        ApplicationRepository applicationRepository = new ApplicationRepository(applicationDAO, new ApplicationMapper());
        SubscriptionPlanRepository subscriptionPlanRepository =
                new SubscriptionPlanRepository(subscriptionPlanDAO, subscriptionPlanIdSequenceDAO, new SubscriptionPlanMapper());
        SubscriptionEntitlementRepository subscriptionEntitlementRepository =
                new SubscriptionEntitlementRepository(subscriptionEntitlementDAO, subscriptionEntitlementLevelDAO, new SubscriptionEntitlementMapper());
        SubscriptionPlanGrantRepository subscriptionPlanGrantRepository =
                new SubscriptionPlanGrantRepository(subscriptionPlanGrantDAO, new SubscriptionPlanGrantMapper());

        SubscriptionPlanService subscriptionPlanService =
                new SubscriptionPlanService(subscriptionPlanRepository, applicationRepository, subscriptionPlanGrantRepository);
        SubscriptionEntitlementService subscriptionEntitlementService =
                new SubscriptionEntitlementService(subscriptionEntitlementRepository, applicationRepository);

        return new SubscriptionPlanGrantService(subscriptionPlanGrantRepository, subscriptionPlanService, subscriptionEntitlementService);
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
    void createNewVersionFalseUpdatesCurrentVersionInPlaceWithoutBumpingVersion() {
        SubscriptionPlanGrantService grantService = newGrantService();
        SubscriptionPlanRepository subscriptionPlanRepository = new SubscriptionPlanRepository(
                subscriptionPlanDAO, subscriptionPlanIdSequenceDAO, new SubscriptionPlanMapper());
        SubscriptionEntitlementRepository subscriptionEntitlementRepository =
                new SubscriptionEntitlementRepository(subscriptionEntitlementDAO, subscriptionEntitlementLevelDAO, new SubscriptionEntitlementMapper());

        Long applicationId = seedApplication();
        SubscriptionPlan plan = subscriptionPlanRepository.createNewPlan(applicationId, "Gold", "d");
        SubscriptionEntitlement entitlement = subscriptionEntitlementRepository.save(
                SubscriptionEntitlement.create(applicationId, "api-calls", "API Calls", EntitlementValueType.NUMERIC, List.of(), 0));
        entityManager.flush();
        entityManager.clear();

        assertThat(plan.getVersion()).isEqualTo(1);

        SubscriptionPlan result = grantService.replaceGrantsForPlan(applicationId, plan.getId(),
                List.of(new GrantValueInput(entitlement.getId(), 42)), false, null);
        entityManager.flush();
        entityManager.clear();

        assertThat(result.getVersion()).isEqualTo(1);

        List<SubscriptionPlan> allVersions = subscriptionPlanRepository.findAllVersions(plan.getId());
        assertThat(allVersions).as("no new version should have been created").hasSize(1);
        assertThat(allVersions.get(0).getVersion()).isEqualTo(1);

        List<SubscriptionPlanGrant> grants = subscriptionPlanGrantDAO.findByApplicationIdAndSubscriptionPlanId(applicationId, plan.getId())
                .stream().map(new SubscriptionPlanGrantMapper()::toDomainModel).toList();
        assertThat(grants).hasSize(1);
        assertThat(grants.get(0).getSubscriptionPlanVersion()).isEqualTo(1);
        assertThat(grants.get(0).getValue()).isEqualTo(42);
    }

    @Test
    void updatingInPlaceWithAnUnchangedGrantDoesNotViolateUniqueConstraints() {
        SubscriptionPlanGrantService grantService = newGrantService();
        SubscriptionPlanRepository subscriptionPlanRepository = new SubscriptionPlanRepository(
                subscriptionPlanDAO, subscriptionPlanIdSequenceDAO, new SubscriptionPlanMapper());
        SubscriptionEntitlementRepository subscriptionEntitlementRepository =
                new SubscriptionEntitlementRepository(subscriptionEntitlementDAO, subscriptionEntitlementLevelDAO, new SubscriptionEntitlementMapper());

        Long applicationId = seedApplication();
        SubscriptionPlan plan = subscriptionPlanRepository.createNewPlan(applicationId, "Gold", "d");
        SubscriptionEntitlement entitlement = subscriptionEntitlementRepository.save(
                SubscriptionEntitlement.create(applicationId, "api-calls", "API Calls", EntitlementValueType.NUMERIC, List.of(), 0));
        entityManager.flush();
        entityManager.clear();

        // First save establishes the (plan, version, entitlement) row that the second call
        // below will delete and re-insert -- this is exactly the scenario that trips Hibernate's
        // default insert-before-delete flush ordering if the service doesn't force a flush
        // between the two.
        grantService.replaceGrantsForPlan(applicationId, plan.getId(),
                List.of(new GrantValueInput(entitlement.getId(), 42)), false, null);
        entityManager.flush();
        entityManager.clear();

        SubscriptionPlan result = grantService.replaceGrantsForPlan(applicationId, plan.getId(),
                List.of(new GrantValueInput(entitlement.getId(), 99)), false, null);
        entityManager.flush();
        entityManager.clear();

        assertThat(result.getVersion()).isEqualTo(1);
        List<SubscriptionPlanGrant> grants = subscriptionPlanGrantDAO.findByApplicationIdAndSubscriptionPlanId(applicationId, plan.getId())
                .stream().map(new SubscriptionPlanGrantMapper()::toDomainModel).toList();
        assertThat(grants).hasSize(1);
        assertThat(grants.get(0).getValue()).isEqualTo(99);
    }

    @Test
    void createNewVersionTrueDoesBumpTheVersion() {
        SubscriptionPlanGrantService grantService = newGrantService();
        SubscriptionPlanRepository subscriptionPlanRepository = new SubscriptionPlanRepository(
                subscriptionPlanDAO, subscriptionPlanIdSequenceDAO, new SubscriptionPlanMapper());
        SubscriptionEntitlementRepository subscriptionEntitlementRepository =
                new SubscriptionEntitlementRepository(subscriptionEntitlementDAO, subscriptionEntitlementLevelDAO, new SubscriptionEntitlementMapper());

        Long applicationId = seedApplication();
        SubscriptionPlan plan = subscriptionPlanRepository.createNewPlan(applicationId, "Gold", "d");
        SubscriptionEntitlement entitlement = subscriptionEntitlementRepository.save(
                SubscriptionEntitlement.create(applicationId, "api-calls", "API Calls", EntitlementValueType.NUMERIC, List.of(), 0));
        entityManager.flush();
        entityManager.clear();

        SubscriptionPlan result = grantService.replaceGrantsForPlan(applicationId, plan.getId(),
                List.of(new GrantValueInput(entitlement.getId(), 42)), true, null);
        entityManager.flush();
        entityManager.clear();

        assertThat(result.getVersion()).isEqualTo(2);
        List<SubscriptionPlan> allVersions = subscriptionPlanRepository.findAllVersions(plan.getId());
        assertThat(allVersions).hasSize(2);
    }

    @Test
    void rejectsAnOutOfRangeValueForABooleanEntitlement() {
        SubscriptionPlanGrantService grantService = newGrantService();
        SubscriptionPlanRepository subscriptionPlanRepository = new SubscriptionPlanRepository(
                subscriptionPlanDAO, subscriptionPlanIdSequenceDAO, new SubscriptionPlanMapper());
        SubscriptionEntitlementRepository subscriptionEntitlementRepository =
                new SubscriptionEntitlementRepository(subscriptionEntitlementDAO, subscriptionEntitlementLevelDAO, new SubscriptionEntitlementMapper());

        Long applicationId = seedApplication();
        SubscriptionPlan plan = subscriptionPlanRepository.createNewPlan(applicationId, "Gold", "d");
        SubscriptionEntitlement entitlement = subscriptionEntitlementRepository.save(
                SubscriptionEntitlement.create(applicationId, "dark-mode", "Dark Mode", EntitlementValueType.BOOLEAN, List.of(), 0));
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> grantService.replaceGrantsForPlan(applicationId, plan.getId(),
                List.of(new GrantValueInput(entitlement.getId(), 2)), false, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void acceptsInRangeValuesForABooleanEntitlement() {
        SubscriptionPlanGrantService grantService = newGrantService();
        SubscriptionPlanRepository subscriptionPlanRepository = new SubscriptionPlanRepository(
                subscriptionPlanDAO, subscriptionPlanIdSequenceDAO, new SubscriptionPlanMapper());
        SubscriptionEntitlementRepository subscriptionEntitlementRepository =
                new SubscriptionEntitlementRepository(subscriptionEntitlementDAO, subscriptionEntitlementLevelDAO, new SubscriptionEntitlementMapper());

        Long applicationId = seedApplication();
        SubscriptionPlan plan = subscriptionPlanRepository.createNewPlan(applicationId, "Gold", "d");
        SubscriptionEntitlement entitlement = subscriptionEntitlementRepository.save(
                SubscriptionEntitlement.create(applicationId, "dark-mode", "Dark Mode", EntitlementValueType.BOOLEAN, List.of(), 0));
        entityManager.flush();
        entityManager.clear();

        SubscriptionPlan result = grantService.replaceGrantsForPlan(applicationId, plan.getId(),
                List.of(new GrantValueInput(entitlement.getId(), 1)), false, null);
        entityManager.flush();
        entityManager.clear();

        assertThat(result.getVersion()).isEqualTo(1);
    }

    @Test
    void rejectsAnOrdinalValueThatDoesNotMatchAnyDefinedLevel() {
        SubscriptionPlanGrantService grantService = newGrantService();
        SubscriptionPlanRepository subscriptionPlanRepository = new SubscriptionPlanRepository(
                subscriptionPlanDAO, subscriptionPlanIdSequenceDAO, new SubscriptionPlanMapper());
        SubscriptionEntitlementRepository subscriptionEntitlementRepository =
                new SubscriptionEntitlementRepository(subscriptionEntitlementDAO, subscriptionEntitlementLevelDAO, new SubscriptionEntitlementMapper());

        Long applicationId = seedApplication();
        SubscriptionPlan plan = subscriptionPlanRepository.createNewPlan(applicationId, "Gold", "d");
        SubscriptionEntitlement entitlement = subscriptionEntitlementRepository.save(
                SubscriptionEntitlement.create(applicationId, "automation-level", "Automation Level", EntitlementValueType.ORDINAL,
                        List.of(SubscriptionEntitlementLevel.of(0, "NONE", "None"), SubscriptionEntitlementLevel.of(1, "MANUAL", "Manual"),
                                SubscriptionEntitlementLevel.of(2, "AUTOMATED", "Automated")), 0));
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> grantService.replaceGrantsForPlan(applicationId, plan.getId(),
                List.of(new GrantValueInput(entitlement.getId(), 3)), false, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void acceptsAnOrdinalValueThatMatchesADefinedLevel() {
        SubscriptionPlanGrantService grantService = newGrantService();
        SubscriptionPlanRepository subscriptionPlanRepository = new SubscriptionPlanRepository(
                subscriptionPlanDAO, subscriptionPlanIdSequenceDAO, new SubscriptionPlanMapper());
        SubscriptionEntitlementRepository subscriptionEntitlementRepository =
                new SubscriptionEntitlementRepository(subscriptionEntitlementDAO, subscriptionEntitlementLevelDAO, new SubscriptionEntitlementMapper());

        Long applicationId = seedApplication();
        SubscriptionPlan plan = subscriptionPlanRepository.createNewPlan(applicationId, "Gold", "d");
        SubscriptionEntitlement entitlement = subscriptionEntitlementRepository.save(
                SubscriptionEntitlement.create(applicationId, "automation-level", "Automation Level", EntitlementValueType.ORDINAL,
                        List.of(SubscriptionEntitlementLevel.of(0, "NONE", "None"), SubscriptionEntitlementLevel.of(1, "MANUAL", "Manual"),
                                SubscriptionEntitlementLevel.of(2, "AUTOMATED", "Automated")), 0));
        entityManager.flush();
        entityManager.clear();

        SubscriptionPlan result = grantService.replaceGrantsForPlan(applicationId, plan.getId(),
                List.of(new GrantValueInput(entitlement.getId(), 2)), false, null);
        entityManager.flush();
        entityManager.clear();

        assertThat(result.getVersion()).isEqualTo(1);
    }
}
