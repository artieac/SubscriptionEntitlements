package com.alwaysmoveforward.subscriptionrights.web.API;

import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionEntitlement;
import com.alwaysmoveforward.subscriptionrights.domainmodel.SubscriptionPlanGrant;
import com.alwaysmoveforward.subscriptionrights.services.ApplicationService;
import com.alwaysmoveforward.subscriptionrights.services.SubscriptionEntitlementService;
import com.alwaysmoveforward.subscriptionrights.services.SubscriptionPlanGrantService;
import com.alwaysmoveforward.subscriptionrights.web.Models.SubscriptionPlanGrantViewModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only mirror of the applicable {@link SubscriptionPlanGrantController} endpoints, for
 * API-token callers only, addressed by Application.ExternalId instead of the internal numeric Id
 * -- see com.alwaysmoveforward.subscriptionrights.security.apitoken.ExternalApiTokenAccessGuard.
 */
@RestController
@RequestMapping("/api/external/applications/{externalId}/subscription-plan-grants")
public class ExternalSubscriptionPlanGrantController {

    private final ApplicationService applicationService;
    private final SubscriptionPlanGrantService subscriptionPlanGrantService;
    private final SubscriptionEntitlementService subscriptionEntitlementService;

    public ExternalSubscriptionPlanGrantController(ApplicationService applicationService,
                                                     SubscriptionPlanGrantService subscriptionPlanGrantService,
                                                     SubscriptionEntitlementService subscriptionEntitlementService) {
        this.applicationService = applicationService;
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
    @PreAuthorize("@externalApiTokenAccessGuard.canAccess(#externalId)")
    public List<SubscriptionPlanGrantViewModel> list(@PathVariable String externalId,
                                                       @RequestParam(required = false) Long subscriptionPlanId,
                                                       @RequestParam(required = false, defaultValue = "false") boolean includeDefaults) {
        Long applicationId = applicationService.getApplicationByExternalId(externalId).getId();
        List<SubscriptionPlanGrant> grants = subscriptionPlanGrantService.listForApplication(applicationId, subscriptionPlanId);
        if (!includeDefaults) {
            return grants.stream().map(SubscriptionPlanGrantViewModel::from).toList();
        }
        List<SubscriptionEntitlement> entitlements = subscriptionEntitlementService.listForApplication(applicationId);
        return SubscriptionPlanGrantViewModel.listIncludingDefaults(applicationId, grants, entitlements);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@externalApiTokenAccessGuard.canAccess(#externalId)")
    public SubscriptionPlanGrantViewModel get(@PathVariable String externalId, @PathVariable Long id) {
        Long applicationId = applicationService.getApplicationByExternalId(externalId).getId();
        return SubscriptionPlanGrantViewModel.from(subscriptionPlanGrantService.getGrant(applicationId, id));
    }
}
