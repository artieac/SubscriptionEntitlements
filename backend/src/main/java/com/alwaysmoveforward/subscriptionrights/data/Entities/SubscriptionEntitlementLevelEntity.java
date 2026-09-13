package com.alwaysmoveforward.subscriptionrights.data.Entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Owned entirely by the SubscriptionEntitlement aggregate -- only SubscriptionEntitlementRepository
 * (and its mapper) may reference this entity or its DAO. There is no
 * SubscriptionEntitlementLevelRepository; levels are never read or written except as part of
 * loading/saving the whole SubscriptionEntitlement they belong to.
 *
 * The unique constraint mirrors database/schema.sql exactly (also declared here, not just there,
 * so a ddl-auto=create-drop test schema enforces the same invariant a real database would).
 */
@Entity
@Table(name = "SubscriptionEntitlementLevels", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_SubscriptionEntitlementLevels_Entitlement_Ordinal",
                columnNames = {"SubscriptionEntitlementId", "Ordinal"})
})
public class SubscriptionEntitlementLevelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "SubscriptionEntitlementId", nullable = false)
    private Long subscriptionEntitlementId;

    @Column(name = "Ordinal", nullable = false)
    private Integer ordinal;

    @Column(name = "Label", nullable = false)
    private String label;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSubscriptionEntitlementId() {
        return subscriptionEntitlementId;
    }

    public void setSubscriptionEntitlementId(Long subscriptionEntitlementId) {
        this.subscriptionEntitlementId = subscriptionEntitlementId;
    }

    public Integer getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(Integer ordinal) {
        this.ordinal = ordinal;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
