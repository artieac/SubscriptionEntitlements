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
    private int defaultValue;
    private final Instant createdAt;
    private Instant updatedAt;

    private SubscriptionEntitlement(Long id, Long applicationId, String name, String displayName,
                                     EntitlementValueType valueType, List<SubscriptionEntitlementLevel> levels,
                                     int defaultValue, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.applicationId = applicationId;
        this.name = name;
        this.displayName = displayName;
        this.valueType = valueType;
        this.levels = levels;
        this.defaultValue = defaultValue;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * @param defaultValue the value an ungranted plan version should be treated as carrying for
     *                      this entitlement -- must fit valueType/levels the same way any other
     *                      grant value must (see {@link #validateValue}).
     */
    public static SubscriptionEntitlement create(Long applicationId, String name, String displayName,
                                                  EntitlementValueType valueType, List<SubscriptionEntitlementLevel> levels,
                                                  int defaultValue) {
        Instant now = Instant.now();
        EntitlementValueType validValueType = requireValueType(valueType);
        List<SubscriptionEntitlementLevel> validLevels = requireValidLevels(validValueType, levels);
        requireValidValue(validValueType, validLevels, name, defaultValue);
        return new SubscriptionEntitlement(null, requireApplicationId(applicationId), requireName(name),
                requireDisplayName(displayName), validValueType, validLevels, defaultValue, now, now);
    }

    /**
     * Reconstitutes a SubscriptionEntitlement from persisted state. Only mappers should call this.
     */
    public static SubscriptionEntitlement reconstitute(Long id, Long applicationId, String name, String displayName,
                                                        EntitlementValueType valueType, List<SubscriptionEntitlementLevel> levels,
                                                        int defaultValue, Instant createdAt, Instant updatedAt) {
        return new SubscriptionEntitlement(id, applicationId, name, displayName, valueType, new ArrayList<>(levels),
                defaultValue, createdAt, updatedAt);
    }

    public void rename(String newName) {
        this.name = requireName(newName);
        this.updatedAt = Instant.now();
    }

    public void updateDisplayName(String newDisplayName) {
        this.displayName = requireDisplayName(newDisplayName);
        this.updatedAt = Instant.now();
    }

    /**
     * @param newDefaultValue must fit newValueType/newLevels, the same way any other grant value must.
     */
    public void redefineValueType(EntitlementValueType newValueType, List<SubscriptionEntitlementLevel> newLevels,
                                   int newDefaultValue) {
        EntitlementValueType validValueType = requireValueType(newValueType);
        List<SubscriptionEntitlementLevel> validLevels = requireValidLevels(validValueType, newLevels);
        requireValidValue(validValueType, validLevels, name, newDefaultValue);
        this.valueType = validValueType;
        this.levels = validLevels;
        this.defaultValue = newDefaultValue;
        this.updatedAt = Instant.now();
    }

    /**
     * Checks {@code value} against this entitlement's valueType, throwing DomainException if it
     * doesn't fit -- BOOLEAN must be 0/1, ORDINAL must match one of this entitlement's levels.
     * NUMERIC is unrestricted, preserving whatever convention (e.g. an "unlimited" sentinel)
     * callers already use.
     */
    public void validateValue(int value) {
        requireValidValue(valueType, levels, name, value);
    }

    private static void requireValidValue(EntitlementValueType valueType, List<SubscriptionEntitlementLevel> levels,
                                           String name, int value) {
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
        Set<String> seenNames = new HashSet<>();
        for (SubscriptionEntitlementLevel level : levels) {
            if (!seenOrdinals.add(level.getOrdinal())) {
                throw new DomainException("SubscriptionEntitlement cannot assign ordinal " + level.getOrdinal()
                        + " to more than one level");
            }
            if (!seenNames.add(level.getName())) {
                throw new DomainException("SubscriptionEntitlement cannot assign name '" + level.getName()
                        + "' to more than one level");
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

    /** The value an ungranted plan version should be treated as carrying for this entitlement. */
    public int getDefaultValue() {
        return defaultValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
