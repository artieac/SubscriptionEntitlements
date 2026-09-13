package com.alwaysmoveforward.subscriptionrights.web.Models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class SubscriptionEntitlementLevelRequest {

    @NotNull
    @PositiveOrZero
    private Integer ordinal;

    @NotBlank
    private String label;

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
