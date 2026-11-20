import { Component, computed, effect, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { PresupuestoApiService } from './core/services/presupuesto-api.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent {
  readonly authService = inject(AuthService);
  private readonly presupuestoApi = inject(PresupuestoApiService);
  readonly enRevisionCount = signal<number>(0);
  readonly sidebarOpen = signal<boolean>(false);
  readonly perfilAbierto = signal<boolean>(false);

  readonly initials = computed(() => {
    const name = (this.authService.username() || 'UD').trim();
    const parts = name.split(/[\s._-]+/).filter(Boolean);
    if (parts.length >= 2) {
      return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
    }
    return name.slice(0, 2).toUpperCase();
  });

  isAuthCallback(): boolean {
    return typeof window !== 'undefined' && window.location.pathname.startsWith('/auth/callback');
  }

  constructor() {
    effect(() => {
      if (this.authService.isAuthenticated()) {
        this.presupuestoApi.listarEnRevision().subscribe({
          next: (lista) => this.enRevisionCount.set(lista.length),
          error: () => this.enRevisionCount.set(0),
        });
      } else {
        this.enRevisionCount.set(0);
      }
    });
  }
}
