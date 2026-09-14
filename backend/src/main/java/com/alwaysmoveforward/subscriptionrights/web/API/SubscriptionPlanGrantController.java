package com.alwaysmoveforward.subscriptionrights.web.API;

import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionEntitlement;
import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionPlanGrant;
import com.alwaysmoveforward.subscriptionrights.services.SubscriptionEntitlementService;
import com.alwaysmoveforward.subscriptionrights.services.SubscriptionPlanGrantService;
import com.alwaysmoveforward.subscriptionrights.web.Models.SubscriptionPlanGrantRequest;
import com.alwaysmoveforward.subscriptionrights.web.Models.SubscriptionPlanGrantViewModel;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications/{applicationId}/subscription-plan-grants")
public class SubscriptionPlanGrantController {

    private final SubscriptionPlanGrantService subscriptionPlanGrantService;
    private final SubscriptionEntitlementService subscriptionEntitlementService;

    public SubscriptionPlanGrantController(SubscriptionPlanGrantService subscriptionPlanGrantService,
                                            SubscriptionEntitlementService subscriptionEntitlementService) {
        this.subscriptionPlanGrantService = subscriptionPlanGrantService;
        this.subscriptionEntitlementService = subscriptionEntitlementService;
    }

    /**
     * @param includeDefaults false (the default) returns only grants that were actually created;
     *                         true also synthesizes one defaulted=true entry per entitlement
     *                         missing a grant at a (plan, version) pair otherwise present in the
     *                         results -- see {@link SubscriptionPlanGrantViewModel#listIncludingDefaults}.
     */
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public List<SubscriptionPlanGrantViewModel> list(@PathVariable Long applicationId,
                                                       @RequestParam(required = false) Long subscriptionPlanId,
                                                       @RequestParam(required = false, defaultValue = "false") boolean includeDefaults) {
        List<SubscriptionPlanGrant> grants = subscriptionPlanGrantService.listForApplication(applicationId, subscriptionPlanId);
        if (!includeDefaults) {
            return grants.stream().map(SubscriptionPlanGrantViewModel::from).toList();
        }
        List<SubscriptionEntitlement> entitlements = subscriptionEntitlementService.listForApplication(applicationId);
        return SubscriptionPlanGrantViewModel.listIncludingDefaults(applicationId, grants, entitlements);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public SubscriptionPlanGrantViewModel get(@PathVariable Long applicationId, @PathVariable Long id) {
        return SubscriptionPlanGrantViewModel.from(subscriptionPlanGrantService.getGrant(applicationId, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionPlanGrantViewModel create(@PathVariable Long applicationId,
                                                  @Valid @RequestBody SubscriptionPlanGrantRequest request) {
        return SubscriptionPlanGrantViewModel.from(subscriptionPlanGrantService.createGrant(
                applicationId, request.getSubscriptionPlanId(), request.getSubscriptionEntitlementId(), request.getValue()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long applicationId, @PathVariable Long id) {
        subscriptionPlanGrantService.deleteGrant(applicationId, id);
    }
}
