import { useEffect, useMemo, useState } from "react";
import { useOutletContext } from "react-router-dom";
import { SubscriptionPlanGrantRepository } from "../api/SubscriptionPlanGrantRepository";
import { SubscriptionPlanRepository } from "../api/SubscriptionPlanRepository";
import { SubscriptionEntitlementRepository } from "../api/SubscriptionEntitlementRepository";
import type { SubscriptionPlanDto } from "../models/SubscriptionPlanDto";
import type { SubscriptionEntitlementDto } from "../models/SubscriptionEntitlementDto";
import type { SubscriptionPlanGrantDto } from "../models/SubscriptionPlanGrantDto";
import { LoadingSpinner } from "../components/LoadingSpinner";
import { RoleGuard } from "../components/RoleGuard";
import { useAuth } from "../context/AuthContext";

// entitlementId -> draft value. Only entitlements the admin has explicitly added to the plan
// appear here -- there is no "not granted" sentinel; removing a grant removes the map entry.
type DraftValues = Map<number, number>;

function valuesForVersion(grants: SubscriptionPlanGrantDto[], version: number): DraftValues {
  return new Map(
    grants.filter((g) => g.subscriptionPlanVersion === version).map((g) => [g.subscriptionEntitlementId, g.value]),
  );
}

/** The editable control for one entitlement's grant value, shaped by its valueType. */
function renderValueInput(entitlement: SubscriptionEntitlementDto, value: number, onChange: (rawValue: string) => void) {
  if (entitlement.valueType === "BOOLEAN") {
    return <input type="checkbox" checked={value === 1} onChange={(e) => onChange(e.target.checked ? "1" : "0")} />;
  }
  if (entitlement.valueType === "ORDINAL") {
    return (
      <select value={value} onChange={(e) => onChange(e.target.value)}>
        {entitlement.levels.map((level) => (
          <option key={level.ordinal} value={level.ordinal}>
            {level.displayName}
          </option>
        ))}
      </select>
    );
  }
  return <input type="number" value={value} onChange={(e) => onChange(e.target.value)} />;
}

/** The read-only rendering of one entitlement's grant value, shaped by its valueType. */
function renderValueDisplay(entitlement: SubscriptionEntitlementDto, value: number) {
  if (entitlement.valueType === "BOOLEAN") {
    return value === 1 ? "Yes" : "No";
  }
  if (entitlement.valueType === "ORDINAL") {
    const level = entitlement.levels.find((l) => l.ordinal === value);
    return level ? level.displayName : value;
  }
  return value;
}

export function SubscriptionPlanGrantsTab() {
  const { applicationId } = useOutletContext<{ applicationId: number }>();
  const { user } = useAuth();
  const [plans, setPlans] = useState<SubscriptionPlanDto[]>([]);
  const [entitlements, setEntitlements] = useState<SubscriptionEntitlementDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedPlanId, setSelectedPlanId] = useState<number | "">("");
  const [versions, setVersions] = useState<SubscriptionPlanDto[]>([]);
  const [selectedVersion, setSelectedVersion] = useState<number | "">("");
  const [grantsForPlan, setGrantsForPlan] = useState<SubscriptionPlanGrantDto[]>([]);
  const [baseline, setBaseline] = useState<DraftValues>(new Map());
  const [draft, setDraft] = useState<DraftValues>(new Map());
  const [entitlementToAdd, setEntitlementToAdd] = useState<number | "">("");
  const [loadingGrants, setLoadingGrants] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [createNewVersion, setCreateNewVersion] = useState(true);

  const selectedPlan = useMemo(() => plans.find((plan) => plan.id === selectedPlanId) ?? null, [plans, selectedPlanId]);

  const grantedEntitlements = useMemo(() => {
    return Array.from(draft.keys())
      .map((id) => entitlements.find((e) => e.id === id))
      .filter((e): e is SubscriptionEntitlementDto => e !== undefined)
      .sort((a, b) => a.displayName.localeCompare(b.displayName));
  }, [draft, entitlements]);

  const availableToAdd = useMemo(() => {
    return entitlements.filter((e) => !draft.has(e.id)).sort((a, b) => a.displayName.localeCompare(b.displayName));
  }, [entitlements, draft]);

  async function loadPlansAndEntitlements() {
    setLoading(true);
    try {
      const [planList, entitlementList] = await Promise.all([
        SubscriptionPlanRepository.list(applicationId),
        SubscriptionEntitlementRepository.list(applicationId),
      ]);
      setPlans(planList);
      setEntitlements(entitlementList);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadPlansAndEntitlements();
    setSelectedPlanId("");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applicationId]);

  async function loadPlan(planId: number, versionToSelect?: number) {
    setLoadingGrants(true);
    setError(null);
    try {
      const [versionList, grantList] = await Promise.all([
        SubscriptionPlanRepository.getVersions(applicationId, planId),
        SubscriptionPlanGrantRepository.list(applicationId, planId),
      ]);
      setVersions(versionList);
      setGrantsForPlan(grantList);
      // getVersions returns newest first, so versionList[0] is the current version.
      let version: number | "" = "";
      if (versionToSelect !== undefined) {
        version = versionToSelect;
      } else if (versionList.length > 0) {
        version = versionList[0].version;
      }
      setSelectedVersion(version);
      const values: DraftValues = version === "" ? new Map() : valuesForVersion(grantList, version);
      setBaseline(values);
      setDraft(new Map(values));
      setEntitlementToAdd("");
    } finally {
      setLoadingGrants(false);
    }
  }

  function handleSelectPlan(planId: number | "") {
    setSelectedPlanId(planId);
    setError(null);
    if (planId === "") {
      setVersions([]);
      setSelectedVersion("");
      setGrantsForPlan([]);
      setBaseline(new Map());
      setDraft(new Map());
      setEntitlementToAdd("");
      return;
    }
    void loadPlan(planId);
  }

  function handleSelectVersion(version: number | "") {
    setSelectedVersion(version);
    setError(null);
    const values = version === "" ? new Map() : valuesForVersion(grantsForPlan, version);
    setBaseline(values);
    setDraft(new Map(values));
    setEntitlementToAdd("");
  }

  function handleValueChange(entitlementId: number, rawValue: string) {
    const parsed = Number(rawValue);
    if (rawValue === "" || Number.isNaN(parsed)) return;
    setDraft((previous) => {
      const next = new Map(previous);
      next.set(entitlementId, parsed);
      return next;
    });
  }

  function handleAddEntitlement() {
    if (entitlementToAdd === "") return;
    const entitlement = entitlements.find((e) => e.id === entitlementToAdd);
    if (!entitlement) return;
    setDraft((previous) => {
      const next = new Map(previous);
      next.set(entitlement.id, entitlement.defaultValue);
      return next;
    });
    setEntitlementToAdd("");
  }

  function handleRemoveEntitlement(entitlementId: number) {
    setDraft((previous) => {
      const next = new Map(previous);
      next.delete(entitlementId);
      return next;
    });
  }

  const isDirty = useMemo(() => {
    if (draft.size !== baseline.size) return true;
    for (const [entitlementId, value] of draft) {
      if (baseline.get(entitlementId) !== value) return true;
    }
    return false;
  }, [draft, baseline]);

  function handleDiscard() {
    setDraft(new Map(baseline));
    setEntitlementToAdd("");
    setError(null);
  }

  async function handleSave() {
    if (selectedPlanId === "" || selectedVersion === "") return;
    setSaving(true);
    setError(null);
    try {
      const items = Array.from(draft.entries()).map(([subscriptionEntitlementId, value]) => ({
        subscriptionEntitlementId,
        value,
      }));
      const updatedPlan = await SubscriptionPlanRepository.replaceGrants(applicationId, selectedPlanId, {
        items,
        createNewVersion,
        targetVersion: selectedVersion,
      });
      const refreshedPlans = await SubscriptionPlanRepository.list(applicationId);
      setPlans(refreshedPlans);
      // If a new version was created, switch to viewing it; otherwise stay on the version just updated.
      await loadPlan(updatedPlan.id, createNewVersion ? updatedPlan.version : selectedVersion);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <LoadingSpinner />;
  }

  const canEdit = Boolean(user?.isAdmin);
  const currentVersionNumber = versions[0]?.version;

  return (
    <div className="subscription-plan-grants-tab">
      <div className="page-header">
        <h2>Subscription Plan Grants</h2>
      </div>

      <label>
        Subscription Plan
        <select value={selectedPlanId} onChange={(e) => handleSelectPlan(e.target.value ? Number(e.target.value) : "")}>
          <option value="">Select a plan…</option>
          {plans.map((plan) => (
            <option key={plan.id} value={plan.id}>
              {plan.name}
            </option>
          ))}
        </select>
      </label>

      {selectedPlan && versions.length > 0 && (
        <label style={{ marginTop: "0.75rem" }}>
          Version
          <select
            value={selectedVersion}
            onChange={(e) => handleSelectVersion(e.target.value ? Number(e.target.value) : "")}
          >
            {versions.map((version) => (
              <option key={version.version} value={version.version}>
                v{version.version}
                {version.version === currentVersionNumber ? " (current)" : ""}
              </option>
            ))}
          </select>
        </label>
      )}

      {selectedPlan && (loadingGrants ? (
        <LoadingSpinner />
      ) : (
        <>
          <p className="form-hint" style={{ marginTop: "1.25rem" }}>
            Editing entitlements for "{selectedPlan.name}" v{selectedVersion}
            {selectedVersion !== currentVersionNumber ? " (not the current version)" : ""}.
          </p>

          <RoleGuard requireAdmin>
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={createNewVersion}
                onChange={(e) => setCreateNewVersion(e.target.checked)}
              />
              Create New Plan Version
            </label>
            <p className="form-hint">
              {createNewVersion
                ? "Saving creates a new plan version (on top of the current one) carrying this set of entitlement grants."
                : `Saving updates v${selectedVersion} in place -- no new version is created.`}
            </p>
          </RoleGuard>

          {error && <p className="form-error">{error}</p>}

          <table className="data-table">
            <thead>
              <tr>
                <th>Entitlement</th>
                <th>Value</th>
                {canEdit && <th></th>}
              </tr>
            </thead>
            <tbody>
              {grantedEntitlements.map((entitlement) => {
                const value = draft.get(entitlement.id)!;
                return (
                  <tr key={entitlement.id}>
                    <td>{entitlement.displayName}</td>
                    <td>
                      {canEdit ? (
                        renderValueInput(entitlement, value, (rawValue) => handleValueChange(entitlement.id, rawValue))
                      ) : (
                        renderValueDisplay(entitlement, value)
                      )}
                    </td>
                    {canEdit && (
                      <td>
                        <button type="button" onClick={() => handleRemoveEntitlement(entitlement.id)}>
                          Remove
                        </button>
                      </td>
                    )}
                  </tr>
                );
              })}
              {grantedEntitlements.length === 0 && (
                <tr>
                  <td colSpan={canEdit ? 3 : 2}>
                    <em>No entitlements granted on this version.</em>
                  </td>
                </tr>
              )}
            </tbody>
          </table>

          <RoleGuard requireAdmin>
            {availableToAdd.length > 0 && (
              <div className="add-entitlement-row" style={{ marginTop: "1rem" }}>
                <select
                  value={entitlementToAdd}
                  onChange={(e) => setEntitlementToAdd(e.target.value ? Number(e.target.value) : "")}
                >
                  <option value="">Add entitlement…</option>
                  {availableToAdd.map((entitlement) => (
                    <option key={entitlement.id} value={entitlement.id}>
                      {entitlement.displayName}
                    </option>
                  ))}
                </select>
                <button type="button" onClick={handleAddEntitlement} disabled={entitlementToAdd === ""}>
                  Add
                </button>
              </div>
            )}

            <div className="dialog-actions">
              <button type="button" onClick={handleDiscard} disabled={!isDirty || saving}>
                Discard Changes
              </button>
              <button type="button" onClick={handleSave} disabled={!isDirty || saving}>
                {saving ? "Saving…" : "Save"}
              </button>
            </div>
          </RoleGuard>
        </>
      ))}
    </div>
  );
}

function extractErrorMessage(error: unknown): string {
  if (
    typeof error === "object" &&
    error !== null &&
    "response" in error &&
    typeof (error as { response?: { data?: { message?: string } } }).response?.data?.message === "string"
  ) {
    return (error as { response: { data: { message: string } } }).response.data.message;
  }
  return "Something went wrong. Please try again.";
}
