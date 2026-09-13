package com.alwaysmoveforward.subscriptionrights.web.Models;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class SubscriptionEntitlementRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String displayName;

    @NotBlank
    private String valueType;

    @NotNull
    @Valid
    private List<SubscriptionEntitlementLevelRequest> levels;

    @NotNull
    private Integer defaultValue;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public List<SubscriptionEntitlementLevelRequest> getLevels() {
        return levels;
    }

    public void setLevels(List<SubscriptionEntitlementLevelRequest> levels) {
        this.levels = levels;
    }

    public Integer getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Integer defaultValue) {
        this.defaultValue = defaultValue;
    }
}
