import React, { useState } from 'react';
import { useAuth } from '../../context/AuthContext';

export const AuthPage: React.FC = () => {
  const { login, register } = useAuth();
  const [isRegister, setIsRegister] = useState(false);
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      if (isRegister) {
        await register(username, email, password);
      } else {
        await login(email, password);
      }
    } catch (err: any) {
      setError(err.message || 'Authentication failed');
    } finally {
      setLoading(false);
    }
  };

  const handleQuickDemo = async (demoName: string, demoEmail: string) => {
    setError('');
    setLoading(true);
    try {
      // Try login first, if fail then register
      try {
        await login(demoEmail, 'password123');
      } catch {
        await register(demoName, demoEmail, 'password123');
      }
    } catch (err: any) {
      setError(err.message || 'Demo login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>MiniCord</h1>
        <div className="subtitle">
          Distributed Computing Chat Platform & Lab Simulator
        </div>

        {error && <div className="error-msg">{error}</div>}

        <form onSubmit={handleSubmit}>
          {isRegister && (
            <div className="form-group">
              <label>Username</label>
              <input
                type="text"
                placeholder="het_patel"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
              />
            </div>
          )}

          <div className="form-group">
            <label>Email Address</label>
            <input
              type="email"
              placeholder="user@minicord.local"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label>Password</label>
            <input
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          <button type="submit" className="auth-btn" disabled={loading}>
            {loading ? 'Please wait...' : isRegister ? 'Create Account' : 'Log In'}
          </button>
        </form>

        <div className="auth-toggle">
          {isRegister ? (
            <p>
              Already have an account?{' '}
              <span onClick={() => { setIsRegister(false); setError(''); }}>Log in</span>
            </p>
          ) : (
            <p>
              Need an account?{' '}
              <span onClick={() => { setIsRegister(true); setError(''); }}>Register</span>
            </p>
          )}
        </div>

        {/* Quick Demo Logins for Lab Simulation Testing */}
        <div style={{ marginTop: '24px', paddingTop: '16px', borderTop: '1px solid var(--border-color)' }}>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em', textAlign: 'center', marginBottom: '8px' }}>
            Quick Demo Accounts (1-Click)
          </div>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              type="button"
              className="btn btn-sm"
              style={{ flex: 1, background: 'rgba(99, 102, 241, 0.15)', color: 'var(--accent-primary-hover)', border: '1px solid var(--border-active)' }}
              onClick={() => handleQuickDemo('Alice', 'alice@minicord.local')}
              disabled={loading}
            >
              Demo: Alice
            </button>
            <button
              type="button"
              className="btn btn-sm"
              style={{ flex: 1, background: 'rgba(168, 85, 247, 0.15)', color: 'var(--accent-purple)', border: '1px solid rgba(168, 85, 247, 0.3)' }}
              onClick={() => handleQuickDemo('Bob', 'bob@minicord.local')}
              disabled={loading}
            >
              Demo: Bob
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
