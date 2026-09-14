import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { useOutletContext } from "react-router-dom";
import { SubscriptionPlanSetRepository } from "../api/SubscriptionPlanSetRepository";
import { SubscriptionPlanRepository } from "../api/SubscriptionPlanRepository";
import { SubscriptionPlanGrantRepository } from "../api/SubscriptionPlanGrantRepository";
import { SubscriptionEntitlementRepository } from "../api/SubscriptionEntitlementRepository";
import type {
  SubscriptionPlanSetDto,
  SubscriptionPlanSetItemRequest,
  SubscriptionPlanSetRequest,
} from "../models/SubscriptionPlanSetDto";
import type { SubscriptionPlanDto } from "../models/SubscriptionPlanDto";
import type { SubscriptionPlanGrantDto } from "../models/SubscriptionPlanGrantDto";
import type { SubscriptionEntitlementDto } from "../models/SubscriptionEntitlementDto";
import { DataTable } from "../components/DataTable";
import { Modal } from "../components/Modal";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { LoadingSpinner } from "../components/LoadingSpinner";
import { RoleGuard } from "../components/RoleGuard";

function extractErrorMessage(error: unknown): string {
  if (typeof error === "object" && error !== null && "response" in error) {
    const response = (error as { response?: { data?: unknown } }).response;
    const data = response?.data;
    if (typeof data === "string" && data.trim()) return data;
    if (data && typeof data === "object") {
      const message = (data as { message?: unknown }).message;
      if (typeof message === "string" && message.trim()) return message;
    }
  }
  return "Something went wrong. Please check for overlapping date ranges and try again.";
}

/** Day before {@code dateStr} (an ISO "YYYY-MM-DD" date), computed in UTC to avoid local-timezone drift. */
function dayBefore(dateStr: string): string {
  const [year, month, day] = dateStr.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  date.setUTCDate(date.getUTCDate() - 1);
  return date.toISOString().slice(0, 10);
}

/** Mirrors SubscriptionPlanSet#overlaps on the backend: a null end date means open-ended. */
function rangesOverlap(aStart: string, aEnd: string | null, bStart: string, bEnd: string | null): boolean {
  const aStartsBeforeBEnds = bEnd === null || aStart <= bEnd;
  const bStartsBeforeAEnds = aEnd === null || bStart <= aEnd;
  return aStartsBeforeBEnds && bStartsBeforeAEnds;
}

function findOverlappingSet(
  sets: SubscriptionPlanSetDto[],
  request: SubscriptionPlanSetRequest,
): SubscriptionPlanSetDto | undefined {
  return sets.find((existing) =>
    rangesOverlap(request.effectiveStartDate, request.effectiveEndDate, existing.effectiveStartDate, existing.effectiveEndDate),
  );
}

interface ItemRow {
  key: number;
  subscriptionPlanId: number | "";
  subscriptionPlanVersion: number | "";
  tier: number | "";
  versions: SubscriptionPlanDto[];
  loadingVersions: boolean;
}

let itemRowKeySeq = 0;
function newItemRow(): ItemRow {
  return {
    key: ++itemRowKeySeq,
    subscriptionPlanId: "",
    subscriptionPlanVersion: "",
    tier: "",
    versions: [],
    loadingVersions: false,
  };
}

function rowsFromSet(set: SubscriptionPlanSetDto | null, plans: SubscriptionPlanDto[]): ItemRow[] {
  if (!set || set.items.length === 0) return [newItemRow()];
  return set.items.map((item) => {
    const row = newItemRow();
    row.subscriptionPlanId = item.subscriptionPlanId;
    row.subscriptionPlanVersion = item.subscriptionPlanVersion;
    row.tier = item.tier;
    // Seed with at least the currently-assigned plan so the select has something to show
    // before its full version history loads.
    const known = plans.find((plan) => plan.id === item.subscriptionPlanId);
    row.versions = known ? [known] : [];
    return row;
  });
}

const ENTITLEMENT_TYPE_LABELS: Record<SubscriptionEntitlementDto["valueType"], string> = {
  BOOLEAN: "Boolean",
  NUMERIC: "Numeric",
  ORDINAL: "Ordinal",
};

/** Mirrors the read-only rendering in SubscriptionPlanGrantsTab: shape the value by the entitlement's valueType. */
function formatGrantValue(entitlement: SubscriptionEntitlementDto, value: number): string {
  if (entitlement.valueType === "BOOLEAN") {
    return value === 1 ? "Yes" : "No";
  }
  if (entitlement.valueType === "ORDINAL") {
    const level = entitlement.levels.find((l) => l.ordinal === value);
    return level ? level.displayName : String(value);
  }
  return String(value);
}

function PlanVersionGrantsModal({
  applicationId,
  planId,
  planName,
  version,
  entitlements,
  onClose,
}: {
  applicationId: number;
  planId: number;
  planName: string;
  version: number;
  entitlements: SubscriptionEntitlementDto[];
  onClose: () => void;
}) {
  const [grants, setGrants] = useState<SubscriptionPlanGrantDto[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    SubscriptionPlanGrantRepository.list(applicationId, planId)
      .then(setGrants)
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applicationId, planId]);

  const entitlementById = new Map(entitlements.map((e) => [e.id, e]));
  const grantsForVersion = grants.filter((g) => g.subscriptionPlanVersion === version);

  return (
    <Modal title={`Grants for "${planName}" v${version}`} onClose={onClose}>
      {loading ? (
        <LoadingSpinner />
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Entitlement</th>
              <th>Type</th>
              <th>Value</th>
            </tr>
          </thead>
          <tbody>
            {grantsForVersion.length === 0 ? (
              <tr>
                <td colSpan={3}>No entitlements granted in this version.</td>
              </tr>
            ) : (
              grantsForVersion.map((grant) => {
                const entitlement = entitlementById.get(grant.subscriptionEntitlementId);
                return (
                  <tr key={grant.id}>
                    <td>{entitlement?.displayName ?? grant.subscriptionEntitlementId}</td>
                    <td>{entitlement ? ENTITLEMENT_TYPE_LABELS[entitlement.valueType] : ""}</td>
                    <td>{entitlement ? formatGrantValue(entitlement, grant.value) : grant.value}</td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      )}
    </Modal>
  );
}

function SubscriptionPlanSetForm({
  applicationId,
  initialSet,
  plans,
  entitlements,
  onSubmit,
  onCancel,
}: {
  applicationId: number;
  initialSet: SubscriptionPlanSetDto | null;
  plans: SubscriptionPlanDto[];
  entitlements: SubscriptionEntitlementDto[];
  onSubmit: (request: SubscriptionPlanSetRequest) => Promise<void>;
  onCancel: () => void;
}) {
  const [name, setName] = useState(initialSet?.name ?? "");
  const [startDate, setStartDate] = useState(initialSet?.effectiveStartDate ?? "");
  const [endDate, setEndDate] = useState(initialSet?.effectiveEndDate ?? "");
  const [rows, setRows] = useState<ItemRow[]>(() => rowsFromSet(initialSet, plans));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [viewingGrantsFor, setViewingGrantsFor] = useState<{ planId: number; version: number } | null>(null);

  useEffect(() => {
    // Refresh version options for rows seeded from an existing set, now that we can call the API.
    rows.forEach((row, index) => {
      if (row.subscriptionPlanId !== "" && row.versions.length <= 1 && !row.loadingVersions) {
        void loadVersionsForRow(index, row.subscriptionPlanId);
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function updateRow(index: number, patch: Partial<ItemRow>) {
    setRows((current) => current.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  }

  async function loadVersionsForRow(index: number, planId: number) {
    updateRow(index, { loadingVersions: true });
    try {
      const versions = await SubscriptionPlanRepository.getVersions(applicationId, planId);
      updateRow(index, { versions, loadingVersions: false });
    } catch {
      updateRow(index, { versions: [], loadingVersions: false });
    }
  }

  function handlePlanChange(index: number, planId: number | "") {
    updateRow(index, { subscriptionPlanId: planId, subscriptionPlanVersion: "", versions: [] });
    if (planId !== "") {
      void loadVersionsForRow(index, planId);
    }
  }

  function addRow() {
    setRows((current) => [...current, newItemRow()]);
  }

  function removeRow(index: number) {
    setRows((current) => current.filter((_, i) => i !== index));
  }

  function validateRows(): string | null {
    const complete = rows.filter(
      (row) => row.subscriptionPlanId !== "" && row.subscriptionPlanVersion !== "" && row.tier !== "",
    );
    if (complete.length !== rows.length) {
      return "Every row needs a plan, a version, and a tier (or remove the incomplete row).";
    }
    if (complete.length === 0) {
      return "Add at least one plan to this set.";
    }
    const planIds = complete.map((row) => row.subscriptionPlanId);
    if (new Set(planIds).size !== planIds.length) {
      return "The same plan is included more than once — each plan may only appear once per set.";
    }
    const tiers = complete.map((row) => row.tier);
    if (new Set(tiers).size !== tiers.length) {
      return "Two rows share the same Tier — each plan in a set needs a distinct tier.";
    }
    return null;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    const validationError = validateRows();
    if (validationError) {
      setError(validationError);
      return;
    }
    const items: SubscriptionPlanSetItemRequest[] = rows.map((row) => ({
      subscriptionPlanId: row.subscriptionPlanId as number,
      subscriptionPlanVersion: row.subscriptionPlanVersion as number,
      tier: row.tier as number,
    }));
    setSaving(true);
    try {
      await onSubmit({
        name,
        effectiveStartDate: startDate,
        effectiveEndDate: endDate || null,
        items,
      });
    } catch (submitError) {
      setError(extractErrorMessage(submitError));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <label>
        Name
        <input value={name} onChange={(e) => setName(e.target.value)} required />
      </label>
      <label>
        Effective Start Date
        <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} required />
      </label>
      <label>
        Effective End Date (leave blank for open-ended)
        <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
      </label>

      <p className="form-hint">Plans included in this set:</p>
      {rows.map((row, index) => (
        <div className="item-builder-row" key={row.key}>
          <select
            value={row.subscriptionPlanId}
            onChange={(e) => handlePlanChange(index, e.target.value ? Number(e.target.value) : "")}
            required
          >
            <option value="">Select a plan…</option>
            {plans.map((plan) => (
              <option key={plan.id} value={plan.id}>
                {plan.name}
              </option>
            ))}
          </select>
          <select
            value={row.subscriptionPlanVersion}
            onChange={(e) =>
              updateRow(index, { subscriptionPlanVersion: e.target.value ? Number(e.target.value) : "" })
            }
            disabled={row.subscriptionPlanId === "" || row.loadingVersions}
            required
          >
            <option value="">{row.loadingVersions ? "Loading versions…" : "Select a version…"}</option>
            {row.versions.map((version) => (
              <option key={version.version} value={version.version}>
                v{version.version} ({new Date(version.createdAt).toLocaleDateString()})
              </option>
            ))}
          </select>
          <input
            type="number"
            placeholder="Tier"
            value={row.tier}
            onChange={(e) => updateRow(index, { tier: e.target.value ? Number(e.target.value) : "" })}
            required
          />
          <button
            type="button"
            disabled={row.subscriptionPlanId === "" || row.subscriptionPlanVersion === ""}
            onClick={() =>
              setViewingGrantsFor({
                planId: row.subscriptionPlanId as number,
                version: row.subscriptionPlanVersion as number,
              })
            }
          >
            View Grants
          </button>
          <button type="button" onClick={() => removeRow(index)} disabled={rows.length === 1}>
            Remove
          </button>
        </div>
      ))}
      <button type="button" onClick={addRow}>
        Add Plan
      </button>

      {error && <p className="form-error">{error}</p>}

      {viewingGrantsFor && (
        <PlanVersionGrantsModal
          applicationId={applicationId}
          planId={viewingGrantsFor.planId}
          planName={plans.find((plan) => plan.id === viewingGrantsFor.planId)?.name ?? ""}
          version={viewingGrantsFor.version}
          entitlements={entitlements}
          onClose={() => setViewingGrantsFor(null)}
        />
      )}

      <div className="dialog-actions">
        <button type="button" onClick={onCancel}>
          Cancel
        </button>
        <button type="submit" disabled={saving}>
          {saving ? "Saving…" : "Save"}
        </button>
      </div>
    </form>
  );
}

function EndOverlappingSetDialog({
  overlappingSet,
  newSetStartDate,
  onConfirm,
  onCancel,
}: {
  overlappingSet: SubscriptionPlanSetDto;
  newSetStartDate: string;
  onConfirm: () => Promise<void>;
  onCancel: () => void;
}) {
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const truncatedEndDate = dayBefore(newSetStartDate);

  async function handleConfirm() {
    setProcessing(true);
    setError(null);
    try {
      await onConfirm();
    } catch (confirmError) {
      setError(extractErrorMessage(confirmError));
      setProcessing(false);
    }
  }

  return (
    <Modal title="Overlapping Plan Set" onClose={onCancel}>
      <p>
        This date range overlaps the existing plan set "{overlappingSet.name}" (
        {overlappingSet.effectiveStartDate} – {overlappingSet.effectiveEndDate ?? "open-ended"}).
      </p>
      <p>
        End "{overlappingSet.name}" on {truncatedEndDate} — the day before the new set starts — and save the new
        set?
      </p>
      {error && <p className="form-error">{error}</p>}
      <div className="dialog-actions">
        <button type="button" onClick={onCancel} disabled={processing}>
          No, let me edit
        </button>
        <button type="button" onClick={() => void handleConfirm()} disabled={processing}>
          {processing ? "Saving…" : "Yes, end it and save"}
        </button>
      </div>
    </Modal>
  );
}

export function SubscriptionPlanSetsTab() {
  const { applicationId } = useOutletContext<{ applicationId: number }>();
  const [sets, setSets] = useState<SubscriptionPlanSetDto[]>([]);
  const [plans, setPlans] = useState<SubscriptionPlanDto[]>([]);
  const [entitlements, setEntitlements] = useState<SubscriptionEntitlementDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<SubscriptionPlanSetDto | null>(null);
  const [deleting, setDeleting] = useState<SubscriptionPlanSetDto | null>(null);
  const [pendingOverlap, setPendingOverlap] = useState<{
    request: SubscriptionPlanSetRequest;
    overlappingSet: SubscriptionPlanSetDto;
  } | null>(null);

  const [findDate, setFindDate] = useState("");
  const [findResult, setFindResult] = useState<SubscriptionPlanSetDto | null>(null);
  const [findSearched, setFindSearched] = useState(false);
  const [findLoading, setFindLoading] = useState(false);

  async function load() {
    setLoading(true);
    try {
      const [setList, planList, entitlementList] = await Promise.all([
        SubscriptionPlanSetRepository.list(applicationId),
        SubscriptionPlanRepository.list(applicationId),
        SubscriptionEntitlementRepository.list(applicationId),
      ]);
      setSets(setList);
      setPlans(planList);
      setEntitlements(entitlementList);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applicationId]);

  async function handleCreate(request: SubscriptionPlanSetRequest) {
    const overlapping = findOverlappingSet(sets, request);
    if (overlapping) {
      setPendingOverlap({ request, overlappingSet: overlapping });
      return;
    }
    await SubscriptionPlanSetRepository.create(applicationId, request);
    setShowCreate(false);
    await load();
  }

  async function handleConfirmEndOverlapping() {
    if (!pendingOverlap) return;
    const { request, overlappingSet } = pendingOverlap;
    await SubscriptionPlanSetRepository.update(applicationId, overlappingSet.id, {
      name: overlappingSet.name,
      effectiveStartDate: overlappingSet.effectiveStartDate,
      effectiveEndDate: dayBefore(request.effectiveStartDate),
      items: overlappingSet.items,
    });
    await SubscriptionPlanSetRepository.create(applicationId, request);
    setPendingOverlap(null);
    setShowCreate(false);
    await load();
  }

  async function handleUpdate(request: SubscriptionPlanSetRequest) {
    if (!editing) return;
    await SubscriptionPlanSetRepository.update(applicationId, editing.id, request);
    setEditing(null);
    await load();
  }

  async function handleDelete() {
    if (!deleting) return;
    await SubscriptionPlanSetRepository.remove(applicationId, deleting.id);
    setDeleting(null);
    await load();
  }

  async function handleFindActive(event: FormEvent) {
    event.preventDefault();
    if (!findDate) return;
    setFindLoading(true);
    try {
      const result = await SubscriptionPlanSetRepository.getActiveOnDate(applicationId, findDate);
      setFindResult(result);
      setFindSearched(true);
    } finally {
      setFindLoading(false);
    }
  }

  if (loading) {
    return <LoadingSpinner />;
  }

  return (
    <div className="subscription-plan-sets-tab">
      <div className="page-header">
        <h2>Subscription Plan Sets</h2>
        <RoleGuard requireAdmin>
          <button type="button" onClick={() => setShowCreate(true)} disabled={plans.length === 0}>
            Create Plan Set
          </button>
        </RoleGuard>
      </div>

      <form className="active-set-finder" onSubmit={handleFindActive}>
        <label>
          Find the set active on:{" "}
          <input type="date" value={findDate} onChange={(e) => setFindDate(e.target.value)} required />
        </label>
        <button type="submit" disabled={findLoading}>
          {findLoading ? "Searching…" : "Find"}
        </button>
        {findSearched && (
          <span>{findResult ? `Active: "${findResult.name}"` : "No active set for that date."}</span>
        )}
      </form>

      <DataTable
        rows={sets}
        rowKey={(row) => row.id}
        emptyMessage="No subscription plan sets yet. Create subscription plans first, then build a set here."
        columns={[
          { header: "Name", render: (row) => row.name },
          { header: "Start Date", render: (row) => row.effectiveStartDate },
          { header: "End Date", render: (row) => row.effectiveEndDate ?? "Open-ended" },
          { header: "Plans", render: (row) => row.items.length },
          {
            header: "Actions",
            render: (row) => (
              <RoleGuard requireAdmin>
                <span className="row-actions">
                  <button type="button" onClick={() => setEditing(row)}>
                    Edit
                  </button>
                  <button type="button" className="danger" onClick={() => setDeleting(row)}>
                    Delete
                  </button>
                </span>
              </RoleGuard>
            ),
          },
        ]}
      />

      {showCreate && (
        <Modal title="Create Subscription Plan Set" onClose={() => setShowCreate(false)}>
          <SubscriptionPlanSetForm
            applicationId={applicationId}
            initialSet={null}
            plans={plans}
            entitlements={entitlements}
            onSubmit={handleCreate}
            onCancel={() => setShowCreate(false)}
          />
        </Modal>
      )}

      {editing && (
        <Modal title="Edit Subscription Plan Set" onClose={() => setEditing(null)}>
          <SubscriptionPlanSetForm
            applicationId={applicationId}
            initialSet={editing}
            plans={plans}
            entitlements={entitlements}
            onSubmit={handleUpdate}
            onCancel={() => setEditing(null)}
          />
        </Modal>
      )}

      {deleting && (
        <ConfirmDialog
          title="Delete Subscription Plan Set"
          message={`Delete "${deleting.name}"? This cannot be undone.`}
          onConfirm={handleDelete}
          onCancel={() => setDeleting(null)}
        />
      )}

      {pendingOverlap && (
        <EndOverlappingSetDialog
          overlappingSet={pendingOverlap.overlappingSet}
          newSetStartDate={pendingOverlap.request.effectiveStartDate}
          onConfirm={handleConfirmEndOverlapping}
          onCancel={() => setPendingOverlap(null)}
        />
      )}
    </div>
  );
}
