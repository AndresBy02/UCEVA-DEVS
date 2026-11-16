import { Injectable, computed, signal } from '@angular/core';

interface TokenResponse {
  access_token: string;
  refresh_token?: string;
  id_token?: string;
  expires_in: number;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly keycloakBase = 'http://localhost:8080/realms/presupuesto-realm/protocol/openid-connect';
  private readonly clientId = 'presupuesto-frontend';

  private readonly storageTokenKey = 'uceva_access_token';
  private readonly storageRefreshKey = 'uceva_refresh_token';
  private readonly storageIdTokenKey = 'uceva_id_token';
  private readonly storageVerifierKey = 'uceva_pkce_verifier';
  private readonly storageReturnUrlKey = 'uceva_return_url';

  private refreshTimerId: number | null = null;
  private expiryNoticeTimerId: number | null = null;

  readonly accessToken = signal<string | null>(null);
  readonly sessionNotice = signal<string | null>(null);

  private readonly claims = computed<Record<string, unknown> | null>(() => {
    const token = this.accessToken();
    return token ? this.decodePayload(token) : null;
  });

  readonly isAuthenticated = computed(() => {
    const c = this.claims();
    if (!c) return false;
    const exp = typeof c['exp'] === 'number' ? c['exp'] : 0;
    return exp * 1000 > Date.now();
  });

  readonly username = computed(() => {
    const c = this.claims();
    return (c?.['preferred_username'] as string) ?? (c?.['name'] as string) ?? '';
  });

  readonly userSub = computed(() => {
    const c = this.claims();
    return (c?.['sub'] as string) ?? '';
  });

  readonly roles = computed<string[]>(() => {
    const c = this.claims();
    const realmAccess = c?.['realm_access'] as { roles?: string[] } | undefined;
    return Array.isArray(realmAccess?.roles) ? realmAccess.roles : [];
  });

  readonly isPresupuestador = computed(() => this.roles().includes('presupuestador'));
  readonly isRevisor = computed(() => this.roles().includes('revisor'));

  constructor() {
    this.restoreSession();
  }

  private restoreSession(): void {
    const saved = sessionStorage.getItem(this.storageTokenKey);
    if (!saved) return;
    const payload = this.decodePayload(saved);
    const exp = typeof payload?.['exp'] === 'number' ? payload['exp'] : 0;
    if (exp * 1000 > Date.now()) {
      this.accessToken.set(saved);
      this.scheduleLifecycleTimers(exp);
    } else {
      this.clearLocalSession();
    }
  }

  async login(returnUrl = '/presupuestos'): Promise<void> {
    const verifier = this.generateRandomVerifier(64);
    const challenge = await this.generateCodeChallenge(verifier);
    sessionStorage.setItem(this.storageVerifierKey, verifier);
    sessionStorage.setItem(this.storageReturnUrlKey, returnUrl);

    const redirectUri = `${window.location.origin}/auth/callback`;
    const params = new URLSearchParams({
      client_id: this.clientId,
      redirect_uri: redirectUri,
      response_type: 'code',
      scope: 'openid profile email',
      code_challenge: challenge,
      code_challenge_method: 'S256',
    });
    window.location.href = `${this.keycloakBase}/auth?${params.toString()}`;
  }

  async handleAuthCallback(code: string): Promise<string> {
    const verifier = sessionStorage.getItem(this.storageVerifierKey) ?? '';
    const returnUrl = sessionStorage.getItem(this.storageReturnUrlKey) || '/presupuestos';
    const redirectUri = `${window.location.origin}/auth/callback`;

    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: this.clientId,
      code,
      redirect_uri: redirectUri,
      code_verifier: verifier,
    });

    const resp = await fetch(`${this.keycloakBase}/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString(),
    });
    if (!resp.ok) {
      throw new Error('No fue posible completar el intercambio PKCE con Keycloak.');
    }

    const tokens = (await resp.json()) as TokenResponse;
    this.storeTokens(tokens);
    sessionStorage.removeItem(this.storageVerifierKey);
    sessionStorage.removeItem(this.storageReturnUrlKey);
    return returnUrl;
  }

  async refreshToken(): Promise<boolean> {
    const refresh = sessionStorage.getItem(this.storageRefreshKey);
    if (!refresh) return false;

    try {
      const body = new URLSearchParams({
        grant_type: 'refresh_token',
        client_id: this.clientId,
        refresh_token: refresh,
      });
      const resp = await fetch(`${this.keycloakBase}/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body.toString(),
      });
      if (!resp.ok) {
        this.handleSessionExpired();
        return false;
      }
      const tokens = (await resp.json()) as TokenResponse;
      this.storeTokens(tokens);
      this.sessionNotice.set(null);
      return true;
    } catch {
      this.handleSessionExpired();
      return false;
    }
  }

  handleSessionExpired(): void {
    const currentPath = window.location.pathname || '/presupuestos';
    this.clearLocalSession();
    this.sessionNotice.set('Tu sesión ha expirado. Redirigiendo a Keycloak para iniciar sesión...');
    setTimeout(() => {
      void this.login(currentPath);
    }, 1200);
  }

  logout(): void {
    const idToken = sessionStorage.getItem(this.storageIdTokenKey);
    this.clearLocalSession();
    const params = new URLSearchParams({
      client_id: this.clientId,
      post_logout_redirect_uri: window.location.origin,
    });
    if (idToken) {
      params.set('id_token_hint', idToken);
    }
    window.location.href = `${this.keycloakBase}/logout?${params.toString()}`;
  }

  private storeTokens(tokens: TokenResponse): void {
    sessionStorage.setItem(this.storageTokenKey, tokens.access_token);
    if (tokens.refresh_token) {
      sessionStorage.setItem(this.storageRefreshKey, tokens.refresh_token);
    }
    if (tokens.id_token) {
      sessionStorage.setItem(this.storageIdTokenKey, tokens.id_token);
    }
    this.accessToken.set(tokens.access_token);
    this.sessionNotice.set(null);

    const payload = this.decodePayload(tokens.access_token);
    const exp = typeof payload?.['exp'] === 'number' ? payload['exp'] : 0;
    if (exp > 0) {
      this.scheduleLifecycleTimers(exp);
    }
  }

  private scheduleLifecycleTimers(expSeconds: number): void {
    this.clearTimers();
    const msUntilExp = expSeconds * 1000 - Date.now();
    const msUntilNotice = Math.max(1000, msUntilExp - 25000);
    const msUntilRefresh = Math.max(1500, msUntilExp - 15000);

    if (msUntilExp <= 0) {
      this.handleSessionExpired();
      return;
    }

    this.expiryNoticeTimerId = window.setTimeout(() => {
      this.sessionNotice.set('Tu sesión está por expirar; renovando credenciales automáticamente...');
    }, msUntilNotice);

    this.refreshTimerId = window.setTimeout(() => {
      void this.refreshToken();
    }, msUntilRefresh);
  }

  private clearLocalSession(): void {
    this.clearTimers();
    sessionStorage.removeItem(this.storageTokenKey);
    sessionStorage.removeItem(this.storageRefreshKey);
    sessionStorage.removeItem(this.storageIdTokenKey);
    this.accessToken.set(null);
  }

  private clearTimers(): void {
    if (this.refreshTimerId !== null) {
      clearTimeout(this.refreshTimerId);
      this.refreshTimerId = null;
    }
    if (this.expiryNoticeTimerId !== null) {
      clearTimeout(this.expiryNoticeTimerId);
      this.expiryNoticeTimerId = null;
    }
  }

  private decodePayload(token: string): Record<string, unknown> | null {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return null;
      const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
      return JSON.parse(atob(padded)) as Record<string, unknown>;
    } catch {
      return null;
    }
  }

  private generateRandomVerifier(length: number): string {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
    const bytes = new Uint8Array(length);
    crypto.getRandomValues(bytes);
    return Array.from(bytes, (b) => chars[b % chars.length]).join('');
  }

  private async generateCodeChallenge(verifier: string): Promise<string> {
    const data = new TextEncoder().encode(verifier);
    const digest = await crypto.subtle.digest('SHA-256', data);
    const bytes = new Uint8Array(digest);
    let binary = '';
    bytes.forEach((b) => (binary += String.fromCharCode(b)));
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }
}
