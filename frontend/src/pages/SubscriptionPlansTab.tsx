import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { useOutletContext } from "react-router-dom";
import { SubscriptionPlanRepository } from "../api/SubscriptionPlanRepository";
import { SubscriptionPlanGrantRepository } from "../api/SubscriptionPlanGrantRepository";
import { SubscriptionEntitlementRepository } from "../api/SubscriptionEntitlementRepository";
import type { SubscriptionPlanDto, SubscriptionPlanRequest } from "../models/SubscriptionPlanDto";
import type { SubscriptionPlanGrantDto } from "../models/SubscriptionPlanGrantDto";
import type { SubscriptionEntitlementDto } from "../models/SubscriptionEntitlementDto";
import { DataTable } from "../components/DataTable";
import { Modal } from "../components/Modal";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { LoadingSpinner } from "../components/LoadingSpinner";
import { RoleGuard } from "../components/RoleGuard";

const emptyForm: SubscriptionPlanRequest = { name: "", description: "" };

function SubscriptionPlanForm({
  initial,
  onSubmit,
  onCancel,
}: {
  initial: SubscriptionPlanRequest;
  onSubmit: (request: SubscriptionPlanRequest) => Promise<void>;
  onCancel: () => void;
}) {
  const [form, setForm] = useState(initial);
  const [saving, setSaving] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    try {
      await onSubmit(form);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <label>
        Name
        <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
      </label>
      <label>
        Description
        <textarea
          value={form.description ?? ""}
          onChange={(e) => setForm({ ...form, description: e.target.value })}
        />
      </label>
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

function ViewGrantsModal({
  applicationId,
  plan,
  entitlements,
  onClose,
}: {
  applicationId: number;
  plan: SubscriptionPlanDto;
  entitlements: SubscriptionEntitlementDto[];
  onClose: () => void;
}) {
  const [versions, setVersions] = useState<SubscriptionPlanDto[]>([]);
  const [grants, setGrants] = useState<SubscriptionPlanGrantDto[]>([]);
  const [selectedVersion, setSelectedVersion] = useState<number | "">("");
  const [loading, setLoading] = useState(true);

  const entitlementById = useMemo(() => new Map(entitlements.map((e) => [e.id, e])), [entitlements]);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      SubscriptionPlanRepository.getVersions(applicationId, plan.id),
      SubscriptionPlanGrantRepository.list(applicationId, plan.id),
    ])
      .then(([versionList, grantList]) => {
        setVersions(versionList);
        setGrants(grantList);
        // getVersions returns newest first, so versionList[0] is the current version.
        setSelectedVersion(versionList[0]?.version ?? "");
      })
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applicationId, plan.id]);

  const grantsForSelectedVersion =
    selectedVersion === "" ? [] : grants.filter((g) => g.subscriptionPlanVersion === selectedVersion);
  const currentVersionNumber = versions[0]?.version;

  return (
    <Modal title={`Grants for "${plan.name}"`} onClose={onClose}>
      {loading ? (
        <LoadingSpinner />
      ) : (
        <>
          <label>
            Version
            <select
              value={selectedVersion}
              onChange={(e) => setSelectedVersion(e.target.value ? Number(e.target.value) : "")}
            >
              {versions.map((version) => (
                <option key={version.version} value={version.version}>
                  v{version.version}
                  {version.version === currentVersionNumber ? " (current)" : ""}
                </option>
              ))}
            </select>
          </label>

          <table className="data-table" style={{ marginTop: "1rem" }}>
            <thead>
              <tr>
                <th>Entitlement</th>
                <th>Value</th>
              </tr>
            </thead>
            <tbody>
              {grantsForSelectedVersion.length === 0 ? (
                <tr>
                  <td colSpan={2}>No entitlements granted in this version.</td>
                </tr>
              ) : (
                grantsForSelectedVersion.map((grant) => (
                  <tr key={grant.id ?? `default-${grant.subscriptionEntitlementId}`}>
                    <td>{entitlementById.get(grant.subscriptionEntitlementId)?.displayName ?? grant.subscriptionEntitlementId}</td>
                    <td>{grant.value}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </>
      )}
    </Modal>
  );
}

export function SubscriptionPlansTab() {
  const { applicationId } = useOutletContext<{ applicationId: number }>();
  const [plans, setPlans] = useState<SubscriptionPlanDto[]>([]);
  const [entitlements, setEntitlements] = useState<SubscriptionEntitlementDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<SubscriptionPlanDto | null>(null);
  const [deleting, setDeleting] = useState<SubscriptionPlanDto | null>(null);
  const [viewingGrantsFor, setViewingGrantsFor] = useState<SubscriptionPlanDto | null>(null);

  async function load() {
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
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applicationId]);

  async function handleCreate(request: SubscriptionPlanRequest) {
    await SubscriptionPlanRepository.create(applicationId, request);
    setShowCreate(false);
    await load();
  }

  async function handleUpdate(request: SubscriptionPlanRequest) {
    if (!editing) return;
    await SubscriptionPlanRepository.update(applicationId, editing.id, request);
    setEditing(null);
    await load();
  }

  async function handleDelete() {
    if (!deleting) return;
    await SubscriptionPlanRepository.remove(applicationId, deleting.id);
    setDeleting(null);
    await load();
  }

  if (loading) {
    return <LoadingSpinner />;
  }

  return (
    <div className="subscription-plans-tab">
      <div className="page-header">
        <h2>Subscription Plans</h2>
        <RoleGuard requireAdmin>
          <button type="button" onClick={() => setShowCreate(true)}>
            Create Plan
          </button>
        </RoleGuard>
      </div>

      <DataTable
        rows={plans}
        rowKey={(row) => row.id}
        columns={[
          { header: "Name", render: (row) => row.name },
          { header: "Version", render: (row) => row.version },
          { header: "Description", render: (row) => row.description ?? "" },
          {
            header: "Actions",
            render: (row) => (
              <span className="row-actions">
                <button type="button" onClick={() => setViewingGrantsFor(row)}>
                  View Grants
                </button>
                <RoleGuard requireAdmin>
                  <button type="button" onClick={() => setEditing(row)}>
                    Edit
                  </button>
                  <button type="button" className="danger" onClick={() => setDeleting(row)}>
                    Delete
                  </button>
                </RoleGuard>
              </span>
            ),
          },
        ]}
      />

      {showCreate && (
        <Modal title="Create Subscription Plan" onClose={() => setShowCreate(false)}>
          <SubscriptionPlanForm initial={emptyForm} onSubmit={handleCreate} onCancel={() => setShowCreate(false)} />
        </Modal>
      )}

      {editing && (
        <Modal title="Edit Subscription Plan" onClose={() => setEditing(null)}>
          <p className="form-hint">Saving creates a new version (currently v{editing.version}).</p>
          <SubscriptionPlanForm
            initial={{ name: editing.name, description: editing.description }}
            onSubmit={handleUpdate}
            onCancel={() => setEditing(null)}
          />
        </Modal>
      )}

      {deleting && (
        <ConfirmDialog
          title="Delete Subscription Plan"
          message={`Delete "${deleting.name}"? This cannot be undone.`}
          onConfirm={handleDelete}
          onCancel={() => setDeleting(null)}
        />
      )}

      {viewingGrantsFor && (
        <ViewGrantsModal
          applicationId={applicationId}
          plan={viewingGrantsFor}
          entitlements={entitlements}
          onClose={() => setViewingGrantsFor(null)}
        />
      )}
    </div>
  );
}
