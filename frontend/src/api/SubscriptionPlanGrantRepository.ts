import { RestClient } from "./RestClient";
import type {
  SubscriptionPlanGrantDto,
  SubscriptionPlanGrantRequest,
} from "../models/SubscriptionPlanGrantDto";

export const SubscriptionPlanGrantRepository = {
  async list(
    applicationId: number,
    subscriptionPlanId?: number,
  ): Promise<SubscriptionPlanGrantDto[]> {
    const response = await RestClient.get<SubscriptionPlanGrantDto[]>(
      `/api/applications/${applicationId}/subscription-plan-grants`,
      { params: { subscriptionPlanId } },
    );
    return response.data;
  },

  async create(
    applicationId: number,
    request: SubscriptionPlanGrantRequest,
  ): Promise<SubscriptionPlanGrantDto> {
    const response = await RestClient.post<SubscriptionPlanGrantDto>(
      `/api/applications/${applicationId}/subscription-plan-grants`,
      request,
    );
    return response.data;
  },

  async remove(applicationId: number, id: number): Promise<void> {
    await RestClient.delete(`/api/applications/${applicationId}/subscription-plan-grants/${id}`);
  },
};
