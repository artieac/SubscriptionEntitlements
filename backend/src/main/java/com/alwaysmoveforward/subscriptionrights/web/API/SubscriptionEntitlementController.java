package com.alwaysmoveforward.subscriptionrights.web.API;

import com.alwaysmoveforward.subscriptionrights.services.SubscriptionEntitlementLevelInput;
import com.alwaysmoveforward.subscriptionrights.services.SubscriptionEntitlementService;
import com.alwaysmoveforward.subscriptionrights.web.Models.SubscriptionEntitlementLevelRequest;
import com.alwaysmoveforward.subscriptionrights.web.Models.SubscriptionEntitlementRequest;
import com.alwaysmoveforward.subscriptionrights.web.Models.SubscriptionEntitlementViewModel;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications/{applicationId}/subscription-entitlements")
public class SubscriptionEntitlementController {

    private final SubscriptionEntitlementService subscriptionEntitlementService;

    public SubscriptionEntitlementController(SubscriptionEntitlementService subscriptionEntitlementService) {
        this.subscriptionEntitlementService = subscriptionEntitlementService;
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public List<SubscriptionEntitlementViewModel> list(@PathVariable Long applicationId) {
        return subscriptionEntitlementService.listForApplication(applicationId).stream()
                .map(SubscriptionEntitlementViewModel::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public SubscriptionEntitlementViewModel get(@PathVariable Long applicationId, @PathVariable Long id) {
        return SubscriptionEntitlementViewModel.from(subscriptionEntitlementService.getEntitlement(applicationId, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionEntitlementViewModel create(@PathVariable Long applicationId,
                                                    @Valid @RequestBody SubscriptionEntitlementRequest request) {
        return SubscriptionEntitlementViewModel.from(
                subscriptionEntitlementService.createEntitlement(applicationId, request.getName(), request.getDisplayName(),
                        request.getValueType(), toLevelInputs(request.getLevels())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionEntitlementViewModel update(@PathVariable Long applicationId, @PathVariable Long id,
                                                    @Valid @RequestBody SubscriptionEntitlementRequest request) {
        return SubscriptionEntitlementViewModel.from(
                subscriptionEntitlementService.updateEntitlement(applicationId, id, request.getName(), request.getDisplayName(),
                        request.getValueType(), toLevelInputs(request.getLevels())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long applicationId, @PathVariable Long id) {
        subscriptionEntitlementService.deleteEntitlement(applicationId, id);
    }

    private List<SubscriptionEntitlementLevelInput> toLevelInputs(List<SubscriptionEntitlementLevelRequest> levels) {
        return levels.stream()
                .map(level -> new SubscriptionEntitlementLevelInput(level.getOrdinal(), level.getLabel()))
                .toList();
    }
}
