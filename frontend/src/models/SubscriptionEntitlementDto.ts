export type EntitlementValueType = "BOOLEAN" | "NUMERIC" | "ORDINAL";

export interface SubscriptionEntitlementLevelDto {
  ordinal: number;
  name: string;
  displayName: string;
}

export interface SubscriptionEntitlementDto {
  id: number;
  applicationId: number;
  name: string;
  displayName: string;
  valueType: EntitlementValueType;
  levels: SubscriptionEntitlementLevelDto[];
  defaultValue: number;
  createdAt: string;
  updatedAt: string;
}

export interface SubscriptionEntitlementRequest {
  name: string;
  displayName: string;
  valueType: EntitlementValueType;
  levels: SubscriptionEntitlementLevelDto[];
  defaultValue: number;
}
