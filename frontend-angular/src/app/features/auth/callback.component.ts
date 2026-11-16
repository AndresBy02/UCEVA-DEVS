import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-callback',
  standalone: true,
  template: `
    <div class="card">
      <h2>Autenticando con Keycloak (PKCE)...</h2>
      @if (error()) {
        <p class="alert-error">{{ error() }}</p>
      }
    </div>
  `,
})
export class CallbackComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);

  readonly error = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    const code = this.route.snapshot.queryParamMap.get('code');
    const authError = this.route.snapshot.queryParamMap.get('error');
    if (authError) {
      this.error.set(`Keycloak rechazó el inicio de sesión: ${authError}`);
      return;
    }
    if (!code) {
      await this.authService.login('/presupuestos');
      return;
    }
    try {
      const returnUrl = await this.authService.handleAuthCallback(code);
      await this.router.navigateByUrl(returnUrl);
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Error inesperado de autenticación.');
    }
  }
}
