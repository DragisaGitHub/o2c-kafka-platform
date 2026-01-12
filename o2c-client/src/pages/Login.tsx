import React, { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import type { ApiError } from '../types';
import { useAuth } from '../auth/AuthContext';

function useNextUrl(): string {
  const location = useLocation();
  return useMemo(() => {
    const params = new URLSearchParams(location.search);
    const next = params.get('next');
    return next && next.startsWith('/') ? next : '/orders';
  }, [location.search]);
}

export function Login() {
  const { startLogin, verifyPin, confirmTotpEnrollment, refresh } = useAuth();
  const navigate = useNavigate();
  const nextUrl = useNextUrl();

  const [step, setStep] = useState<'credentials' | 'enroll' | 'pin'>('credentials');
  const [challengeId, setChallengeId] = useState<string | null>(null);
  const [setupId, setSetupId] = useState<string | null>(null);
  const [qrUrl, setQrUrl] = useState<string | null>(null);

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [pin, setPin] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const setTotpCode = (value: string) => {
    const digitsOnly = value.replace(/\D/g, '').slice(0, 6);
    setPin(digitsOnly);
  };

  useEffect(() => {
    let cancelled = false;
    let objectUrl: string | null = null;

    const loadQr = async () => {
      if (!setupId) {
        setQrUrl(null);
        return;
      }

      try {
        const res = await fetch(`/auth/mfa/totp/qr?setupId=${encodeURIComponent(setupId)}`, {
          method: 'GET',
          credentials: 'include',
        });
        if (!res.ok) {
          throw new Error(res.statusText);
        }
        const blob = await res.blob();
        objectUrl = URL.createObjectURL(blob);
        if (!cancelled) {
          setQrUrl(objectUrl);
        }
      } catch {
        if (!cancelled) {
          setQrUrl(null);
        }
      }
    };

    void loadQr();

    return () => {
      cancelled = true;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [setupId]);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);

    try {
      if (step === 'credentials') {
        const res = await startLogin(username, password);
        if (res.status === 'AUTHENTICATED') {
          await refresh();
          navigate(nextUrl, { replace: true });
          return;
        }

        if (res.status === 'ENROLL_REQUIRED') {
          setSetupId(res.setupId);
          setChallengeId(null);
          setStep('enroll');
          setPin('');
          return;
        }

        setChallengeId(res.challengeId);
        setSetupId(null);
        setStep('pin');
        setPin('');
        return;
      }

      if (step === 'enroll') {
        if (!setupId) {
          throw { code: 'AUTH_ERROR', message: 'Missing setupId. Please start over.' } as ApiError;
        }

        await confirmTotpEnrollment(setupId, pin);
        await refresh();
        navigate(nextUrl, { replace: true });
        return;
      }

      if (!challengeId) {
        throw { code: 'AUTH_ERROR', message: 'Missing challengeId. Please start over.' } as ApiError;
      }

      await verifyPin(challengeId, pin);
      await refresh();
      navigate(nextUrl, { replace: true });
    } catch (err) {
      setError(err as ApiError);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="max-w-md mx-auto px-4 py-10">
      <h1 className="text-2xl font-semibold text-gray-900 mb-2">Sign in</h1>
      <p className="text-gray-600 mb-6">
        For local dev, use <span className="font-mono">user/password</span> or <span className="font-mono">admin/password</span>.
      </p>

      {step === 'pin' && (
        <p className="text-sm text-gray-600 mb-6">
          MFA required. Enter the 6-digit code from your authenticator app.
        </p>
      )}

      {step === 'enroll' && (
        <p className="text-sm text-gray-600 mb-6">
          First-time setup required. Scan the QR code and enter the 6-digit code to continue.
        </p>
      )}

      {error && (
        <div className="mb-4 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">
          {error.message || 'Login failed'}
        </div>
      )}

      <form onSubmit={onSubmit} className="space-y-4">
        {step === 'credentials' ? (
          <>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Username</label>
              <input
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full rounded-md border border-gray-300 px-3 py-2"
                autoComplete="username"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full rounded-md border border-gray-300 px-3 py-2"
                autoComplete="current-password"
              />
            </div>
          </>
        ) : step === 'enroll' ? (
          <>
            <div className="rounded-lg border border-gray-200 bg-gray-50 p-3">
              <div className="flex items-center justify-center">
                <div className="bg-white border border-gray-200 rounded-md p-2">
                  {qrUrl ? (
                    <img
                      src={qrUrl}
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
              <label className="block text-sm font-medium text-gray-700 mb-1">Confirm code</label>
              <input
                value={pin}
                onChange={(e) => setTotpCode(e.target.value)}
                className="w-full rounded-md border border-gray-300 px-3 py-2"
                autoComplete="one-time-code"
                inputMode="numeric"
                maxLength={6}
                placeholder="123456"
              />
            </div>

            <button
              type="button"
              disabled={submitting}
              onClick={() => {
                setStep('credentials');
                setChallengeId(null);
                setSetupId(null);
                setQrUrl(null);
                setPin('');
                setError(null);
              }}
              className="w-full rounded-md border border-gray-300 text-gray-700 px-3 py-2 disabled:opacity-60"
            >
              Back
            </button>
          </>
        ) : (
          <>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Authenticator code</label>
              <input
                value={pin}
                onChange={(e) => setTotpCode(e.target.value)}
                className="w-full rounded-md border border-gray-300 px-3 py-2"
                autoComplete="one-time-code"
                inputMode="numeric"
                maxLength={6}
                placeholder="123456"
              />
            </div>
            <button
              type="button"
              disabled={submitting}
              onClick={() => {
                setStep('credentials');
                setChallengeId(null);
                setSetupId(null);
                setQrUrl(null);
                setPin('');
                setError(null);
              }}
              className="w-full rounded-md border border-gray-300 text-gray-700 px-3 py-2 disabled:opacity-60"
            >
              Back
            </button>
          </>
        )}
        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-gray-900 text-white px-3 py-2 disabled:opacity-60"
        >
          {submitting
            ? 'Working…'
            : step === 'credentials'
              ? 'Continue'
              : step === 'enroll'
                ? 'Enable MFA'
                : 'Verify code'}
        </button>
      </form>
    </div>
  );
}
