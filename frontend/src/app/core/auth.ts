import { HttpClient, HttpErrorResponse, HttpHeaders, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, firstValueFrom, throwError } from 'rxjs';

const STORAGE_KEY = 'hand-com-auth';

function readStored(): string | null {
  try {
    return sessionStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function writeStored(value: string | null) {
  try {
    if (value) sessionStorage.setItem(STORAGE_KEY, value);
    else sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    /* stockage indisponible : la session durera le temps de l'onglet */
  }
}

/** Encode « utilisateur:mot de passe » en Base64 (UTF-8) pour l'en-tête HTTP Basic. */
function basicToken(username: string, password: string): string {
  const bytes = new TextEncoder().encode(`${username}:${password}`);
  return btoa(String.fromCharCode(...bytes));
}

@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly token = signal<string | null>(readStored());
  readonly loggedIn = computed(() => this.token() !== null);

  async login(username: string, password: string): Promise<void> {
    const token = basicToken(username, password);
    await firstValueFrom(
      this.http.get('/api/me', { headers: new HttpHeaders({ Authorization: `Basic ${token}` }) }),
    );
    this.token.set(token);
    writeStored(token);
  }

  logout() {
    this.token.set(null);
    writeStored(null);
    this.router.navigate(['/login']);
  }
}

/** Ajoute l'en-tête d'authentification aux appels /api et renvoie vers /login en cas de 401. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(Auth);
  const token = auth.token();
  if (token && req.url.startsWith('/api') && !req.headers.has('Authorization')) {
    req = req.clone({ setHeaders: { Authorization: `Basic ${token}` } });
  }
  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401 && token) auth.logout();
      return throwError(() => err);
    }),
  );
};

export const authGuard: CanActivateFn = () => {
  const auth = inject(Auth);
  return auth.loggedIn() ? true : inject(Router).createUrlTree(['/login']);
};
