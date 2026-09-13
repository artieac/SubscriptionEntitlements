package com.alwaysmoveforward.subscriptionrights.web.Models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public class SubscriptionEntitlementLevelRequest {

    @NotNull
    @PositiveOrZero
    private Integer ordinal;

    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "must be UPPER_SNAKE_CASE (start with a letter, then letters/digits/underscores)")
    private String name;

    @NotBlank
    private String displayName;

    public Integer getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(Integer ordinal) {
        this.ordinal = ordinal;
    }

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
}
