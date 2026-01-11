import React, { useMemo, useState } from 'react';
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
  const { startLogin, verifyPin, refresh } = useAuth();
  const navigate = useNavigate();
  const nextUrl = useNextUrl();

  const [step, setStep] = useState<'credentials' | 'pin'>('credentials');
  const [challengeId, setChallengeId] = useState<string | null>(null);

  const [username, setUsername] = useState('user');
  const [password, setPassword] = useState('password');
  const [pin, setPin] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);

    try {
      if (step === 'credentials') {
        const res = await startLogin(username, password);
        setChallengeId(res.challengeId);
        setStep('pin');
        setPin('');
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
          MFA required. In <span className="font-mono">local/dev</span>, read the PIN from backend logs.
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
        ) : (
          <>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">PIN</label>
              <input
                value={pin}
                onChange={(e) => setPin(e.target.value)}
                className="w-full rounded-md border border-gray-300 px-3 py-2"
                autoComplete="one-time-code"
                inputMode="numeric"
              />
            </div>
            <button
              type="button"
              disabled={submitting}
              onClick={() => {
                setStep('credentials');
                setChallengeId(null);
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
          {submitting ? 'Working…' : step === 'credentials' ? 'Continue' : 'Verify PIN'}
        </button>
      </form>
    </div>
  );
}
