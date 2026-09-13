import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { useOutletContext } from "react-router-dom";
import { SubscriptionEntitlementRepository } from "../api/SubscriptionEntitlementRepository";
import type {
  EntitlementValueType,
  SubscriptionEntitlementDto,
  SubscriptionEntitlementLevelDto,
  SubscriptionEntitlementRequest,
} from "../models/SubscriptionEntitlementDto";
import { DataTable } from "../components/DataTable";
import { Modal } from "../components/Modal";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { LoadingSpinner } from "../components/LoadingSpinner";
import { RoleGuard } from "../components/RoleGuard";

const emptyForm: SubscriptionEntitlementRequest = {
  name: "",
  displayName: "",
  valueType: "NUMERIC",
  levels: [],
};

interface LevelRow {
  key: number;
  ordinal: number | "";
  label: string;
}

let levelRowKeySeq = 0;
function newLevelRow(): LevelRow {
  return { key: ++levelRowKeySeq, ordinal: "", label: "" };
}

function rowsFromLevels(levels: SubscriptionEntitlementLevelDto[]): LevelRow[] {
  if (levels.length === 0) return [newLevelRow()];
  return levels.map((level) => ({ key: ++levelRowKeySeq, ordinal: level.ordinal, label: level.label }));
}

function SubscriptionEntitlementForm({
  initial,
  onSubmit,
  onCancel,
}: {
  initial: SubscriptionEntitlementRequest;
  onSubmit: (request: SubscriptionEntitlementRequest) => Promise<void>;
  onCancel: () => void;
}) {
  const [name, setName] = useState(initial.name);
  const [displayName, setDisplayName] = useState(initial.displayName);
  const [valueType, setValueType] = useState<EntitlementValueType>(initial.valueType);
  const [levelRows, setLevelRows] = useState<LevelRow[]>(() => rowsFromLevels(initial.levels));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function updateLevelRow(index: number, patch: Partial<LevelRow>) {
    setLevelRows((current) => current.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  }

  function addLevelRow() {
    setLevelRows((current) => [...current, newLevelRow()]);
  }

  function removeLevelRow(index: number) {
    setLevelRows((current) => current.filter((_, i) => i !== index));
  }

  function validateLevels(): string | null {
    if (valueType !== "ORDINAL") return null;
    const complete = levelRows.filter((row) => row.ordinal !== "" && row.label.trim() !== "");
    if (complete.length !== levelRows.length) {
      return "Every level needs an ordinal and a label (or remove the incomplete row).";
    }
    if (complete.length === 0) {
      return "An Ordinal Levels entitlement needs at least one level.";
    }
    const ordinals = complete.map((row) => row.ordinal);
    if (new Set(ordinals).size !== ordinals.length) {
      return "Two levels share the same ordinal — each level needs a distinct ordinal.";
    }
    return null;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    const validationError = validateLevels();
    if (validationError) {
      setError(validationError);
      return;
    }
    const levels: SubscriptionEntitlementLevelDto[] =
      valueType === "ORDINAL"
        ? levelRows.map((row) => ({ ordinal: row.ordinal as number, label: row.label.trim() }))
        : [];
    setSaving(true);
    try {
      await onSubmit({ name, displayName, valueType, levels });
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
        Display Name
        <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required />
      </label>
      <label>
        Value Type
        <select value={valueType} onChange={(e) => setValueType(e.target.value as EntitlementValueType)}>
          <option value="BOOLEAN">Boolean (0/1)</option>
          <option value="NUMERIC">Numeric</option>
          <option value="ORDINAL">Ordinal Levels</option>
        </select>
      </label>

      {valueType === "ORDINAL" && (
        <>
          <p className="form-hint">Levels:</p>
          {levelRows.map((row, index) => (
            <div className="item-builder-row" key={row.key}>
              <input
                type="number"
                placeholder="Ordinal"
                value={row.ordinal}
                onChange={(e) => updateLevelRow(index, { ordinal: e.target.value ? Number(e.target.value) : "" })}
                required
              />
              <input
                type="text"
                placeholder="Label"
                value={row.label}
                onChange={(e) => updateLevelRow(index, { label: e.target.value })}
                required
              />
              <button type="button" onClick={() => removeLevelRow(index)} disabled={levelRows.length === 1}>
                Remove
              </button>
            </div>
          ))}
          <button type="button" onClick={addLevelRow}>
            Add Level
          </button>
        </>
      )}

      {error && <p className="form-error">{error}</p>}

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

export function SubscriptionEntitlementsTab() {
  const { applicationId } = useOutletContext<{ applicationId: number }>();
  const [entitlements, setEntitlements] = useState<SubscriptionEntitlementDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<SubscriptionEntitlementDto | null>(null);
  const [deleting, setDeleting] = useState<SubscriptionEntitlementDto | null>(null);

  async function load() {
    setLoading(true);
    try {
      setEntitlements(await SubscriptionEntitlementRepository.list(applicationId));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applicationId]);

  async function handleCreate(request: SubscriptionEntitlementRequest) {
    await SubscriptionEntitlementRepository.create(applicationId, request);
    setShowCreate(false);
    await load();
  }

  async function handleUpdate(request: SubscriptionEntitlementRequest) {
    if (!editing) return;
    await SubscriptionEntitlementRepository.update(applicationId, editing.id, request);
    setEditing(null);
    await load();
  }

  async function handleDelete() {
    if (!deleting) return;
    await SubscriptionEntitlementRepository.remove(applicationId, deleting.id);
    setDeleting(null);
    await load();
  }

  if (loading) {
    return <LoadingSpinner />;
  }

  return (
    <div className="subscription-entitlements-tab">
      <div className="page-header">
        <h2>Subscription Entitlements</h2>
        <RoleGuard requireAdmin>
          <button type="button" onClick={() => setShowCreate(true)}>
            Create Entitlement
          </button>
        </RoleGuard>
      </div>

      <DataTable
        rows={entitlements}
        rowKey={(row) => row.id}
        columns={[
          { header: "Name", render: (row) => row.name },
          { header: "Display Name", render: (row) => row.displayName },
          { header: "Value Type", render: (row) => row.valueType },
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
        <Modal title="Create Subscription Entitlement" onClose={() => setShowCreate(false)}>
          <SubscriptionEntitlementForm
            initial={emptyForm}
            onSubmit={handleCreate}
            onCancel={() => setShowCreate(false)}
          />
        </Modal>
      )}

      {editing && (
        <Modal title="Edit Subscription Entitlement" onClose={() => setEditing(null)}>
          <SubscriptionEntitlementForm
            initial={{
              name: editing.name,
              displayName: editing.displayName,
              valueType: editing.valueType,
              levels: editing.levels,
            }}
            onSubmit={handleUpdate}
            onCancel={() => setEditing(null)}
          />
        </Modal>
      )}

      {deleting && (
        <ConfirmDialog
          title="Delete Subscription Entitlement"
          message={`Delete "${deleting.name}"? This cannot be undone.`}
          onConfirm={handleDelete}
          onCancel={() => setDeleting(null)}
        />
      )}
    </div>
  );
}
