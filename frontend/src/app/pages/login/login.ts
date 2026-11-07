import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Auth } from '../../core/auth';
import { errorMessage } from '../../core/api';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  template: `
    <div class="login-wrap">
      <form class="card login" (ngSubmit)="submit()">
        <div class="brand big"><span class="brand-ball" aria-hidden="true"></span> Hand Com</div>
        <p class="muted">La com du club, programmée à l’avance.</p>
        <label>Identifiant
          <input name="username" [(ngModel)]="username" autocomplete="username" required />
        </label>
        <label>Mot de passe
          <input name="password" type="password" [(ngModel)]="password" autocomplete="current-password" required />
        </label>
        @if (error()) { <p class="alert error">{{ error() }}</p> }
        <button class="btn primary" type="submit" [disabled]="loading()">
          {{ loading() ? 'Connexion…' : 'Se connecter' }}
        </button>
      </form>
    </div>
  `,
})
export class Login {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);

  username = signal('admin');
  password = signal('');
  loading = signal(false);
  error = signal<string | null>(null);

  async submit() {
    this.loading.set(true);
    this.error.set(null);
    try {
      await this.auth.login(this.username(), this.password());
      this.router.navigate(['/calendrier']);
    } catch (e) {
      this.error.set(
        e instanceof HttpErrorResponse && e.status === 401 ? 'Identifiant ou mot de passe incorrect.' : errorMessage(e),
      );
    } finally {
      this.loading.set(false);
    }
  }
}
