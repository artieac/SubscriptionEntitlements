export interface SubscriptionPlanGrantDto {
  id: number | null;
  applicationId: number;
  subscriptionPlanId: number;
  subscriptionPlanVersion: number;
  subscriptionEntitlementId: number;
  value: number;
  defaulted: boolean;
  createdAt: string | null;
}

export interface SubscriptionPlanGrantRequest {
  subscriptionPlanId: number;
  subscriptionEntitlementId: number;
  value: number;
}

export interface SubscriptionPlanGrantItemRequest {
  subscriptionEntitlementId: number;
  value: number;
}

export interface ReplaceSubscriptionPlanGrantsRequest {
  items: SubscriptionPlanGrantItemRequest[];
  createNewVersion: boolean;
  targetVersion: number;
}
