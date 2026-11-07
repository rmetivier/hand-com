import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Auth } from './core/auth';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    @if (auth.loggedIn()) {
      <header class="topbar">
        <a class="brand" routerLink="/calendrier">
          <span class="brand-ball" aria-hidden="true"></span>
          Hand Com
        </a>
        <nav>
          <a routerLink="/calendrier" routerLinkActive="active">Calendrier</a>
          <a routerLink="/posts/nouveau" routerLinkActive="active">Nouveau post</a>
          <a routerLink="/comptes" routerLinkActive="active">Comptes</a>
        </nav>
        <button class="btn ghost small" (click)="auth.logout()">Déconnexion</button>
      </header>
    }
    <main class="page">
      <router-outlet />
    </main>
  `,
})
export class App {
  protected readonly auth = inject(Auth);
}
