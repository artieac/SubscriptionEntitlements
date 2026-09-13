package com.alwaysmoveforward.subscriptionrights.services;

import com.alwaysmoveforward.subscriptionrights.data.repositories.SubscriptionEntitlementRepository;
import com.alwaysmoveforward.subscriptionrights.data.repositories.ApplicationRepository;
import com.alwaysmoveforward.subscriptionrights.domainmodel.EntitlementValueType;
import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionEntitlement;
import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionEntitlementLevel;
import com.alwaysmoveforward.subscriptionrights.exceptions.DomainException;
import com.alwaysmoveforward.subscriptionrights.exceptions.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SubscriptionEntitlementService {

    private final SubscriptionEntitlementRepository subscriptionEntitlementRepository;
    private final ApplicationRepository applicationRepository;

    public SubscriptionEntitlementService(SubscriptionEntitlementRepository subscriptionEntitlementRepository,
                                           ApplicationRepository applicationRepository) {
        this.subscriptionEntitlementRepository = subscriptionEntitlementRepository;
        this.applicationRepository = applicationRepository;
    }

    public List<SubscriptionEntitlement> listForApplication(Long applicationId) {
        requireApplication(applicationId);
        return subscriptionEntitlementRepository.findByApplicationId(applicationId);
    }

    public SubscriptionEntitlement getEntitlement(Long applicationId, Long entitlementId) {
        SubscriptionEntitlement entitlement = subscriptionEntitlementRepository.findById(entitlementId)
                .orElseThrow(() -> new NotFoundException("SubscriptionEntitlement " + entitlementId + " not found"));
        requireBelongsToApplication(entitlement, applicationId);
        return entitlement;
    }

    @Transactional
    public SubscriptionEntitlement createEntitlement(Long applicationId, String name, String displayName,
                                                       String valueType, List<SubscriptionEntitlementLevelInput> levelInputs,
                                                       int defaultValue) {
        requireApplication(applicationId);
        EntitlementValueType parsedValueType = parseValueType(valueType);
        List<SubscriptionEntitlementLevel> levels = resolveLevels(levelInputs);
        return subscriptionEntitlementRepository.save(
                SubscriptionEntitlement.create(applicationId, name, displayName, parsedValueType, levels, defaultValue));
    }

    @Transactional
    public SubscriptionEntitlement updateEntitlement(Long applicationId, Long entitlementId, String name, String displayName,
                                                       String valueType, List<SubscriptionEntitlementLevelInput> levelInputs,
                                                       int defaultValue) {
        SubscriptionEntitlement entitlement = getEntitlement(applicationId, entitlementId);
        EntitlementValueType parsedValueType = parseValueType(valueType);
        List<SubscriptionEntitlementLevel> levels = resolveLevels(levelInputs);
        entitlement.rename(name);
        entitlement.updateDisplayName(displayName);
        entitlement.redefineValueType(parsedValueType, levels, defaultValue);
        return subscriptionEntitlementRepository.save(entitlement);
    }

    @Transactional
    public void deleteEntitlement(Long applicationId, Long entitlementId) {
        getEntitlement(applicationId, entitlementId);
        subscriptionEntitlementRepository.deleteById(entitlementId);
    }

    private EntitlementValueType parseValueType(String valueType) {
        try {
            return EntitlementValueType.valueOf(valueType);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomainException("Unknown entitlement valueType '" + valueType + "'");
        }
    }

    private List<SubscriptionEntitlementLevel> resolveLevels(List<SubscriptionEntitlementLevelInput> levelInputs) {
        return levelInputs.stream()
                .map(input -> SubscriptionEntitlementLevel.of(input.ordinal(), input.name(), input.displayName()))
                .toList();
    }

    private void requireApplication(Long applicationId) {
        if (!applicationRepository.existsById(applicationId)) {
            throw new NotFoundException("Application " + applicationId + " not found");
        }
    }

    private void requireBelongsToApplication(SubscriptionEntitlement entitlement, Long applicationId) {
        if (!entitlement.getApplicationId().equals(applicationId)) {
            throw new NotFoundException("SubscriptionEntitlement " + entitlement.getId() + " not found for application " + applicationId);
        }
    }
}
