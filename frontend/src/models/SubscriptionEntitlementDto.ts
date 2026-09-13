export type EntitlementValueType = "BOOLEAN" | "NUMERIC" | "ORDINAL";

export interface SubscriptionEntitlementLevelDto {
  ordinal: number;
  label: string;
}

export interface SubscriptionEntitlementDto {
  id: number;
  applicationId: number;
  name: string;
  displayName: string;
  valueType: EntitlementValueType;
  levels: SubscriptionEntitlementLevelDto[];
  createdAt: string;
  updatedAt: string;
}

export interface SubscriptionEntitlementRequest {
  name: string;
  displayName: string;
  valueType: EntitlementValueType;
  levels: SubscriptionEntitlementLevelDto[];
}
