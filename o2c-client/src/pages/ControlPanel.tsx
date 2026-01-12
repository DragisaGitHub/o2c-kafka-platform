/*
Manual E2E test scenario:
1) Login as SUPER_ADMIN -> open Control Panel -> create user -> assign roles -> disable -> enable -> verify list updates.
2) Login as ADMIN -> open Control Panel -> can view users but cannot mutate.
3) Login as USER -> navigate to /control-panel -> see 403 Forbidden (no login redirect).
*/

import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ApiError, AdminUserDetails, AdminUserSummary, CreateAdminUserRequest } from '../types';
import { useAuth } from '../auth/AuthContext';
import { adminUsersService } from '../api/adminUsersService';
import { adminTotpService } from '../api/adminTotpService';
import { ErrorBanner } from '../components/ErrorBanner';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { Forbidden } from './Forbidden';

const ALL_ROLES = ['USER', 'ADMIN', 'SUPER_ADMIN'] as const;

type Role = (typeof ALL_ROLES)[number];

function hasAnyRole(roles: string[] | undefined, allowed: Role[]): boolean {
  const set = new Set(roles || []);
  return allowed.some((r) => set.has(r));
}

function normalizeRoles(roles: string[]): Role[] {
  const set = new Set(roles);
  return ALL_ROLES.filter((r) => set.has(r));
}

function RolePills({ roles }: { roles: string[] }) {
  const normalized = normalizeRoles(roles);
  if (normalized.length === 0) {
    return <span className="text-gray-500">—</span>;
  }

  return (
    <div className="flex flex-wrap gap-2">
      {normalized.map((r) => (
        <span
          key={r}
          className="inline-flex items-center rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-800"
        >
          {r}
        </span>
      ))}
    </div>
  );
}

function EnabledBadge({ enabled }: { enabled: boolean }) {
  return enabled ? (
    <span className="inline-flex items-center rounded-full bg-green-100 px-2 py-0.5 text-xs text-green-800">
      Enabled
    </span>
  ) : (
    <span className="inline-flex items-center rounded-full bg-gray-200 px-2 py-0.5 text-xs text-gray-800">
      Disabled
    </span>
  );
}

function ModalShell({
  title,
  children,
  onClose,
}: {
  title: string;
  children: React.ReactNode;
  onClose: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative w-full max-w-lg mx-4 bg-white rounded-lg shadow-lg border border-gray-200">
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200">
          <h2 className="text-gray-900 text-lg">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            className="text-gray-600 hover:text-gray-900"
            aria-label="Close"
          >
            ×
          </button>
        </div>
        <div className="p-5">{children}</div>
      </div>
    </div>
  );
}

function DrawerShell({
  title,
  children,
  onClose,
}: {
  title: string;
  children: React.ReactNode;
  onClose: () => void;
}) {
  return (
    <div className="fixed inset-0 z-40">
      <div className="absolute inset-0 bg-black/30" onClick={onClose} />
      <div className="absolute right-0 top-0 h-full w-full max-w-xl bg-white shadow-xl border-l border-gray-200">
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200">
          <h2 className="text-gray-900 text-lg">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            className="text-gray-600 hover:text-gray-900"
            aria-label="Close"
          >
            ×
          </button>
        </div>
        <div className="p-5 overflow-y-auto h-[calc(100%-57px)]">{children}</div>
      </div>
    </div>
  );
}

function RoleMultiSelect({
  value,
  onChange,
  disabled,
}: {
  value: Role[];
  onChange: (roles: Role[]) => void;
  disabled?: boolean;
}) {
  const selected = new Set(value);

  const toggle = (r: Role) => {
    const next = new Set(selected);
    if (next.has(r)) next.delete(r);
    else next.add(r);
    onChange(ALL_ROLES.filter((x) => next.has(x)));
  };

  return (
    <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
      {ALL_ROLES.map((r) => (
        <label
          key={r}
          className={`flex items-center gap-2 rounded-md border px-3 py-2 text-sm ${
            disabled
              ? 'bg-gray-50 text-gray-500 border-gray-200'
              : 'bg-white text-gray-900 border-gray-300'
          }`}
        >
          <input
            type="checkbox"
            checked={selected.has(r)}
            onChange={() => toggle(r)}
            disabled={disabled}
          />
          {r}
        </label>
      ))}
    </div>
  );
}

function CreateUserModal({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: () => Promise<void>;
}) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [roles, setRoles] = useState<Role[]>(['USER']);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const validationError = useMemo(() => {
    if (!username.trim()) return 'Username is required.';
    if (!password.trim()) return 'Password is required.';
    if (roles.length === 0) return 'Select at least one role.';
    return null;
  }, [username, password, roles]);

  const submit = async () => {
    if (validationError) {
      setError({ code: 'VALIDATION', message: validationError });
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const payload: CreateAdminUserRequest = {
        username: username.trim(),
        password,
        roles,
      };
      await adminUsersService.createUser(payload);
      await onCreated();
      onClose();
    } catch (e) {
      const err = e as ApiError;
      if (err.code === 'DUPLICATE') {
        setError({ code: 'DUPLICATE', message: 'Username already exists.' });
      } else {
        setError(err);
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ModalShell title="Create User" onClose={onClose}>
      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      <div className="space-y-4">
        <div>
          <label className="block text-sm text-gray-700 mb-2">Username</label>
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            placeholder="e.g. alice"
            autoFocus
          />
        </div>

        <div>
          <label className="block text-sm text-gray-700 mb-2">Password</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            placeholder="Set an initial password"
          />
        </div>

        <div>
          <div className="flex items-center justify-between mb-2">
            <label className="block text-sm text-gray-700">Roles</label>
            <span className="text-xs text-gray-500">At least one</span>
          </div>
          <RoleMultiSelect value={roles} onChange={setRoles} />
        </div>

        <div className="flex items-center justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-md text-gray-700 hover:bg-gray-100"
          >
            Cancel
          </button>
          <button
            type="button"
            disabled={submitting}
            onClick={submit}
            className="px-4 py-2 rounded-md bg-blue-600 hover:bg-blue-700 text-white disabled:opacity-60"
          >
            {submitting ? 'Creating…' : 'Create'}
          </button>
        </div>
      </div>
    </ModalShell>
  );
}

function UserDetailsDrawer({
  username,
  canMutate,
  onClose,
  onAfterMutation,
}: {
  username: string;
  canMutate: boolean;
  onClose: () => void;
  onAfterMutation: () => Promise<void>;
}) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [user, setUser] = useState<AdminUserDetails | null>(null);

  const [enabledDraft, setEnabledDraft] = useState<boolean | null>(null);
  const [rolesDraft, setRolesDraft] = useState<Role[]>([]);
  const [saving, setSaving] = useState(false);

  const [password, setPassword] = useState('');
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [passwordNote, setPasswordNote] = useState<string | null>(null);

  const [totpSetupId, setTotpSetupId] = useState<string | null>(null);
  const [totpExpiresAt, setTotpExpiresAt] = useState<string | null>(null);
  const [totpCode, setTotpCode] = useState('');
  const [totpQrUrl, setTotpQrUrl] = useState<string | null>(null);
  const [totpBusy, setTotpBusy] = useState(false);
  const [totpNote, setTotpNote] = useState<string | null>(null);
  const [totpError, setTotpError] = useState<ApiError | null>(null);

  const setTotpDigits = (value: string) => {
    const digitsOnly = value.replace(/\D/g, '').slice(0, 6);
    setTotpCode(digitsOnly);
  };

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const details = await adminUsersService.getUser(username);
      setUser(details);
      setEnabledDraft(details.enabled);
      setRolesDraft(normalizeRoles(details.roles));
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setLoading(false);
    }
  }, [username]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    let cancelled = false;
    let objectUrl: string | null = null;

    const fetchQr = async () => {
      if (!user || !totpSetupId) {
        setTotpQrUrl(null);
        return;
      }

      try {
        const res = await fetch(
          `/api/admin/users/${encodeURIComponent(user.username)}/mfa/totp/qr?setupId=${encodeURIComponent(totpSetupId)}`,
          { method: 'GET', credentials: 'include' }
        );

        if (!res.ok) {
          throw { code: `HTTP_${res.status}`, message: res.statusText } as ApiError;
        }

        const blob = await res.blob();
        objectUrl = URL.createObjectURL(blob);
        if (!cancelled) {
          setTotpQrUrl(objectUrl);
        }
      } catch (e) {
        if (!cancelled) {
          setTotpQrUrl(null);
        }
      }
    };

    void fetchQr();

    return () => {
      cancelled = true;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [user, totpSetupId]);

  const saveMutations = async () => {
    if (!user) return;

    setSaving(true);
    setError(null);
    try {
      const tasks: Promise<void>[] = [];
      if (enabledDraft !== null && enabledDraft !== user.enabled) {
        tasks.push(adminUsersService.setEnabled(user.username, enabledDraft));
      }
      const newRoles = rolesDraft;
      const oldRoles = normalizeRoles(user.roles);
      if (newRoles.join('|') !== oldRoles.join('|')) {
        tasks.push(adminUsersService.setRoles(user.username, newRoles));
      }

      if (tasks.length > 0) {
        await Promise.all(tasks);
      }

      await onAfterMutation();
      await load();
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setSaving(false);
    }
  };

  const submitPasswordReset = async () => {
    if (!user) return;

    if (!password.trim()) {
      setPasswordNote('Password is required.');
      return;
    }

    setPasswordSaving(true);
    setPasswordNote(null);
    try {
      await adminUsersService.resetPassword(user.username, password);
      setPassword('');
      setPasswordNote('Password updated.');
    } catch (e) {
      const err = e as ApiError;
      if (err.code === 'NOT_FOUND') {
        setPasswordNote('Password reset is not supported by this server.');
      } else {
        setPasswordNote(err.message);
      }
    } finally {
      setPasswordSaving(false);
    }
  };

  const startTotpEnrollment = async () => {
    if (!user) return;

    setTotpBusy(true);
    setTotpError(null);
    setTotpNote(null);
    try {
      const res = await adminTotpService.enrollTotp(user.username);
      setTotpSetupId(res.setupId);
      setTotpExpiresAt(res.expiresAt);
      setTotpCode('');
      setTotpQrUrl(null);
      setTotpNote('Scan the QR code, then enter the 6-digit code to confirm.');
    } catch (e) {
      setTotpError(e as ApiError);
    } finally {
      setTotpBusy(false);
    }
  };

  const confirmTotpEnrollment = async () => {
    if (!user || !totpSetupId) return;

    if (totpCode.trim().length !== 6) {
      setTotpNote('Please enter the 6-digit code.');
      return;
    }

    setTotpBusy(true);
    setTotpError(null);
    setTotpNote(null);
    try {
      await adminTotpService.confirmTotp(user.username, totpSetupId, totpCode);
      setTotpSetupId(null);
      setTotpExpiresAt(null);
      setTotpCode('');
      setTotpQrUrl(null);
      setTotpNote('TOTP enabled for this user.');
    } catch (e) {
      const err = e as ApiError;
      if (err.code === 'NOT_FOUND') {
        setTotpSetupId(null);
        setTotpExpiresAt(null);
        setTotpCode('');
        setTotpQrUrl(null);
        setTotpNote('Setup expired. Start enrollment again.');
      } else {
        setTotpError(err);
      }
    } finally {
      setTotpBusy(false);
    }
  };

  const title = user ? `User: ${user.username}` : `User: ${username}`;

  return (
    <DrawerShell title={title} onClose={onClose}>
      {loading ? (
        <div className="py-10">
          <LoadingSpinner size="lg" />
        </div>
      ) : error ? (
        <>
          {error.code === 'FORBIDDEN' ? (
            <div className="bg-white">
              <h3 className="text-gray-900">You don’t have permission</h3>
              <p className="mt-2 text-gray-600">
                You are authenticated, but not allowed to view this user.
              </p>
            </div>
          ) : (
            <ErrorBanner error={error} onDismiss={() => setError(null)} />
          )}
        </>
      ) : !user ? (
        <div className="text-gray-600">User not found.</div>
      ) : (
        <div className="space-y-6">
          <div className="bg-gray-50 rounded-lg p-4 border border-gray-200">
            <dl className="grid grid-cols-1 gap-4">
              <div>
                <dt className="text-sm text-gray-500">Username</dt>
                <dd className="text-gray-900">{user.username}</dd>
              </div>
              <div>
                <dt className="text-sm text-gray-500">Roles</dt>
                <dd className="mt-1">
                  <RolePills roles={user.roles} />
                </dd>
              </div>
              <div>
                <dt className="text-sm text-gray-500">Status</dt>
                <dd className="mt-1">
                  <EnabledBadge enabled={user.enabled} />
                </dd>
              </div>
              {user.updatedAt && (
                <div>
                  <dt className="text-sm text-gray-500">Updated</dt>
                  <dd className="text-gray-900">
                    {new Date(user.updatedAt).toLocaleString()}
                  </dd>
                </div>
              )}
            </dl>
          </div>

          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <div className="flex items-center justify-between">
              <h3 className="text-gray-900">Admin Actions</h3>
              {!canMutate && (
                <span className="text-xs text-gray-500">Read-only (ADMIN)</span>
              )}
            </div>

            {!canMutate ? (
              <p className="mt-2 text-sm text-gray-600">
                You can view user details, but only SUPER_ADMIN can modify users.
              </p>
            ) : (
              <>
                <div className="mt-4 grid grid-cols-1 gap-4">
                  <div>
                    <label className="block text-sm text-gray-700 mb-2">
                      Enabled
                    </label>
                    <div className="flex items-center gap-3">
                      <button
                        type="button"
                        onClick={() => setEnabledDraft((v) => !(v ?? user.enabled))}
                        className={`px-3 py-2 rounded-md border text-sm ${
                          (enabledDraft ?? user.enabled)
                            ? 'border-green-300 bg-green-50 text-green-800'
                            : 'border-gray-300 bg-gray-50 text-gray-800'
                        }`}
                      >
                        {(enabledDraft ?? user.enabled) ? 'Enabled' : 'Disabled'}
                      </button>
                      <span className="text-sm text-gray-600">
                        Click to toggle
                      </span>
                    </div>
                  </div>

                  <div>
                    <label className="block text-sm text-gray-700 mb-2">
                      Roles
                    </label>
                    <RoleMultiSelect
                      value={rolesDraft}
                      onChange={setRolesDraft}
                    />
                    <p className="mt-2 text-xs text-gray-500">
                      Changes overwrite roles.
                    </p>
                  </div>

                  <div className="flex items-center justify-end gap-3">
                    <button
                      type="button"
                      disabled={saving}
                      onClick={saveMutations}
                      className="px-4 py-2 rounded-md bg-blue-600 hover:bg-blue-700 text-white disabled:opacity-60"
                    >
                      {saving ? 'Saving…' : 'Save changes'}
                    </button>
                  </div>
                </div>

                <div className="mt-6 pt-6 border-t border-gray-200">
                  <h4 className="text-gray-900">Reset Password</h4>
                  <p className="mt-1 text-sm text-gray-600">
                    Updates the user’s password (if supported by server).
                  </p>
                  <div className="mt-3 flex items-center gap-3">
                    <input
                      type="password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      className="flex-1 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      placeholder="New password"
                    />
                    <button
                      type="button"
                      disabled={passwordSaving}
                      onClick={submitPasswordReset}
                      className="px-4 py-2 rounded-md bg-gray-900 hover:bg-black text-white disabled:opacity-60"
                    >
                      {passwordSaving ? 'Updating…' : 'Update'}
                    </button>
                  </div>
                  {passwordNote && (
                    <p className="mt-2 text-sm text-gray-600">{passwordNote}</p>
                  )}
                </div>

                <div className="mt-6 pt-6 border-t border-gray-200">
                  <h4 className="text-gray-900">TOTP / MFA</h4>
                  <p className="mt-1 text-sm text-gray-600">
                    Enroll a user into TOTP (Authenticator app).
                  </p>

                  {totpError && (
                    <div className="mt-3">
                      <ErrorBanner
                        error={totpError}
                        onDismiss={() => setTotpError(null)}
                      />
                    </div>
                  )}

                  <div className="mt-3 flex items-center gap-3">
                    <button
                      type="button"
                      disabled={totpBusy}
                      onClick={startTotpEnrollment}
                      className="px-4 py-2 rounded-md bg-gray-900 hover:bg-black text-white disabled:opacity-60"
                    >
                      {totpBusy
                        ? 'Working…'
                        : totpSetupId
                          ? 'Re-enroll'
                          : 'Start enrollment'}
                    </button>
                    {totpExpiresAt && (
                      <span className="text-xs text-gray-500">
                        Expires: {new Date(totpExpiresAt).toLocaleString()}
                      </span>
                    )}
                  </div>

                  {totpSetupId && user && (
                    <div className="mt-4 grid grid-cols-1 gap-4">
                      <div className="rounded-lg border border-gray-200 bg-gray-50 p-3">
                        <div className="flex items-center justify-center">
                          <div className="bg-white border border-gray-200 rounded-md p-2">
                            {totpQrUrl ? (
                              <img
                                src={totpQrUrl}
                                alt="TOTP QR code"
                                className="block"
                                style={{ width: 256, height: 256 }}
                              />
                            ) : (
                              <div
                                className="flex items-center justify-center text-xs text-gray-500"
                                style={{ width: 256, height: 256 }}
                              >
                                Loading QR…
                              </div>
                            )}
                          </div>
                        </div>
                        <p className="mt-2 text-xs text-gray-600 text-center">
                          Scan with Google Authenticator, Microsoft Authenticator, 1Password, etc.
                        </p>
                      </div>

                      <div>
                        <label className="block text-sm text-gray-700 mb-2">
                          Confirm code
                        </label>
                        <div className="flex items-center gap-3">
                          <input
                            value={totpCode}
                            onChange={(e) => setTotpDigits(e.target.value)}
                            className="flex-1 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                            placeholder="123456"
                            autoComplete="one-time-code"
                            inputMode="numeric"
                            maxLength={6}
                          />
                          <button
                            type="button"
                            disabled={totpBusy}
                            onClick={confirmTotpEnrollment}
                            className="px-4 py-2 rounded-md bg-blue-600 hover:bg-blue-700 text-white disabled:opacity-60"
                          >
                            {totpBusy ? 'Confirming…' : 'Confirm'}
                          </button>
                        </div>
                        {totpNote && (
                          <p className="mt-2 text-sm text-gray-600">{totpNote}</p>
                        )}
                      </div>
                    </div>
                  )}

                  {!totpSetupId && totpNote && (
                    <p className="mt-2 text-sm text-gray-600">{totpNote}</p>
                  )}
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </DrawerShell>
  );
}

export function ControlPanel() {
  const { state } = useAuth();

  const canView =
    state.status === 'authenticated' &&
    hasAnyRole(state.roles, ['ADMIN', 'SUPER_ADMIN']);

  const canMutate =
    state.status === 'authenticated' && hasAnyRole(state.roles, ['SUPER_ADMIN']);

  const [users, setUsers] = useState<AdminUserSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const [query, setQuery] = useState('');
  const [selectedUsername, setSelectedUsername] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  const loadUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await adminUsersService.listUsers();
      setUsers(list);
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (canView) {
      void loadUsers();
    }
  }, [canView, loadUsers]);

  const filteredUsers = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return users;

    return users.filter((u) => {
      const rolesStr = (u.roles || []).join(' ').toLowerCase();
      return (
        u.username.toLowerCase().includes(q) ||
        rolesStr.includes(q) ||
        (u.enabled ? 'enabled' : 'disabled').includes(q)
      );
    });
  }, [users, query]);

  if (state.status !== 'authenticated') {
    // RequireAuth should prevent reaching this.
    return null;
  }

  if (!canView) {
    return <Forbidden />;
  }

  if (loading) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-8">
        <div className="bg-white shadow-sm rounded-lg p-12 border border-gray-200">
          <LoadingSpinner size="lg" />
        </div>
      </div>
    );
  }

  if (error?.code === 'FORBIDDEN') {
    return <Forbidden />;
  }

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <div className="mb-6">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h2 className="text-gray-900">Control Panel</h2>
            <p className="mt-2 text-gray-600">Manage users and roles.</p>
          </div>

          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => void loadUsers()}
              className="px-4 py-2 rounded-md text-gray-700 hover:bg-gray-100"
            >
              Refresh
            </button>

            {canMutate && (
              <button
                type="button"
                onClick={() => setCreateOpen(true)}
                className="px-4 py-2 rounded-md bg-blue-600 hover:bg-blue-700 text-white"
              >
                Create User
              </button>
            )}
          </div>
        </div>
      </div>

      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      <div className="bg-white shadow-sm rounded-lg p-4 mb-6 border border-gray-200">
        <label className="block text-sm text-gray-700 mb-2">Search</label>
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Filter by username, role, enabled"
          className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        />
      </div>

      <div className="bg-white shadow-sm rounded-lg border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs text-gray-500 uppercase tracking-wider">
                  Username
                </th>
                <th className="px-6 py-3 text-left text-xs text-gray-500 uppercase tracking-wider">
                  Roles
                </th>
                <th className="px-6 py-3 text-left text-xs text-gray-500 uppercase tracking-wider">
                  Enabled
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {filteredUsers.length === 0 ? (
                <tr>
                  <td className="px-6 py-6 text-gray-600" colSpan={3}>
                    No users found.
                  </td>
                </tr>
              ) : (
                filteredUsers.map((u) => (
                  <tr
                    key={u.username}
                    className="hover:bg-gray-50 cursor-pointer"
                    onClick={() => setSelectedUsername(u.username)}
                  >
                    <td className="px-6 py-4 text-gray-900">{u.username}</td>
                    <td className="px-6 py-4">
                      <RolePills roles={u.roles} />
                    </td>
                    <td className="px-6 py-4">
                      <EnabledBadge enabled={u.enabled} />
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {createOpen && (
        <CreateUserModal
          onClose={() => setCreateOpen(false)}
          onCreated={loadUsers}
        />
      )}

      {selectedUsername && (
        <UserDetailsDrawer
          username={selectedUsername}
          canMutate={canMutate}
          onClose={() => setSelectedUsername(null)}
          onAfterMutation={loadUsers}
        />
      )}
    </div>
  );
}
