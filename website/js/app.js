(function () {
  const API = ''; // same origin when site is served by the API server

  window.gfoAuth = {
    async login(email, password) {
      const res = await fetch(API + '/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ email, password }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || 'Login failed');
      return data;
    },
    async signup(email, password) {
      const res = await fetch(API + '/api/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ email, password }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || 'Signup failed');
      return data;
    },
    async me() {
      const res = await fetch(API + '/api/auth/me', { credentials: 'include' });
      if (res.status === 401) return null;
      return res.json();
    },
  };

  window.gfoKeys = {
    async list() {
      const res = await fetch(API + '/api/user/keys', { credentials: 'include' });
      if (res.status === 401) return null;
      if (!res.ok) throw new Error('Failed to load keys');
      return res.json();
    },
    async create(label) {
      const res = await fetch(API + '/api/user/keys', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ label: label || 'Default' }),
      });
      if (res.status === 401) return null;
      if (!res.ok) throw new Error((await res.json().catch(() => ({}))).error || 'Failed to create key');
      return res.json();
    },
    async revoke(id) {
      const res = await fetch(API + '/api/user/keys/' + id, {
        method: 'DELETE',
        credentials: 'include',
      });
      if (!res.ok) throw new Error('Failed to revoke');
      return res.json();
    },
  };
})();
