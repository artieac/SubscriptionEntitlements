package com.alwaysmoveforward.subscriptionrights.domainmodel;

import com.alwaysmoveforward.subscriptionrights.exceptions.DomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Aggregate root for a named entitlement (permission/right) that belongs to one Application.
 *
 * SubscriptionEntitlementLevel has no repository of its own -- every read/write of an
 * entitlement's levels goes through this aggregate root, since "no duplicate ordinal" and
 * "levels only make sense for ORDINAL entitlements" can only be checked by seeing the whole
 * levels collection at once.
 */
public class SubscriptionEntitlement {

    private final Long id;
    private final Long applicationId;
    private String name;
    private String displayName;
    private EntitlementValueType valueType;
    private List<SubscriptionEntitlementLevel> levels;
    private final Instant createdAt;
    private Instant updatedAt;

    private SubscriptionEntitlement(Long id, Long applicationId, String name, String displayName,
                                     EntitlementValueType valueType, List<SubscriptionEntitlementLevel> levels,
                                     Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.applicationId = applicationId;
        this.name = name;
        this.displayName = displayName;
        this.valueType = valueType;
        this.levels = levels;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static SubscriptionEntitlement create(Long applicationId, String name, String displayName,
                                                  EntitlementValueType valueType, List<SubscriptionEntitlementLevel> levels) {
        Instant now = Instant.now();
        return new SubscriptionEntitlement(null, requireApplicationId(applicationId), requireName(name),
                requireDisplayName(displayName), requireValueType(valueType), requireValidLevels(valueType, levels), now, now);
    }

    /**
     * Reconstitutes a SubscriptionEntitlement from persisted state. Only mappers should call this.
     */
    public static SubscriptionEntitlement reconstitute(Long id, Long applicationId, String name, String displayName,
                                                        EntitlementValueType valueType, List<SubscriptionEntitlementLevel> levels,
                                                        Instant createdAt, Instant updatedAt) {
        return new SubscriptionEntitlement(id, applicationId, name, displayName, valueType, new ArrayList<>(levels),
                createdAt, updatedAt);
    }

    public void rename(String newName) {
        this.name = requireName(newName);
        this.updatedAt = Instant.now();
    }

    public void updateDisplayName(String newDisplayName) {
        this.displayName = requireDisplayName(newDisplayName);
        this.updatedAt = Instant.now();
    }

    public void redefineValueType(EntitlementValueType newValueType, List<SubscriptionEntitlementLevel> newLevels) {
        this.valueType = requireValueType(newValueType);
        this.levels = requireValidLevels(newValueType, newLevels);
        this.updatedAt = Instant.now();
    }

    /**
     * Checks {@code value} against this entitlement's valueType, throwing DomainException if it
     * doesn't fit -- BOOLEAN must be 0/1, ORDINAL must match one of this entitlement's levels.
     * NUMERIC is unrestricted, preserving whatever convention (e.g. an "unlimited" sentinel)
     * callers already use.
     */
    public void validateValue(int value) {
        switch (valueType) {
            case BOOLEAN -> {
                if (value != 0 && value != 1) {
                    throw new DomainException(
                            "Entitlement '" + name + "' is BOOLEAN -- value must be 0 or 1, got " + value);
                }
            }
            case ORDINAL -> {
                boolean matches = levels.stream().anyMatch(level -> level.getOrdinal() == value);
                if (!matches) {
                    throw new DomainException(
                            "Entitlement '" + name + "' is ORDINAL -- value " + value + " does not match any defined level");
                }
            }
            case NUMERIC -> {
                // unrestricted
            }
        }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException("SubscriptionEntitlement name must not be blank");
        }
        return name;
    }

    private static String requireDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new DomainException("SubscriptionEntitlement displayName must not be blank");
        }
        return displayName;
    }

    private static Long requireApplicationId(Long applicationId) {
        if (applicationId == null) {
            throw new DomainException("SubscriptionEntitlement must belong to an Application");
        }
        return applicationId;
    }

    private static EntitlementValueType requireValueType(EntitlementValueType valueType) {
        if (valueType == null) {
            throw new DomainException("SubscriptionEntitlement requires a valueType");
        }
        return valueType;
    }

    private static List<SubscriptionEntitlementLevel> requireValidLevels(EntitlementValueType valueType,
                                                                            List<SubscriptionEntitlementLevel> levels) {
        if (levels == null) {
            throw new DomainException("SubscriptionEntitlement levels must not be null");
        }
        if (valueType != EntitlementValueType.ORDINAL) {
            if (!levels.isEmpty()) {
                throw new DomainException("Only an ORDINAL entitlement may define levels");
            }
            return new ArrayList<>();
        }
        if (levels.isEmpty()) {
            throw new DomainException("An ORDINAL entitlement requires at least one level");
        }
        Set<Integer> seenOrdinals = new HashSet<>();
        for (SubscriptionEntitlementLevel level : levels) {
            if (!seenOrdinals.add(level.getOrdinal())) {
                throw new DomainException("SubscriptionEntitlement cannot assign ordinal " + level.getOrdinal()
                        + " to more than one level");
            }
        }
        return new ArrayList<>(levels);
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

    public EntitlementValueType getValueType() {
        return valueType;
    }

    public List<SubscriptionEntitlementLevel> getLevels() {
        return Collections.unmodifiableList(levels);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
