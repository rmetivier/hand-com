import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Account, Api, AppConfig, PLATFORM_LABELS, Platform, errorMessage } from '../../core/api';
import { formatDateTime } from '../../core/dates';

@Component({
  selector: 'app-accounts',
  templateUrl: './accounts.html',
})
export class Accounts implements OnInit {
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  /** Paramètres de retour OAuth : ?platform=…&status=ok|error&message=… */
  readonly platform = input<string>();
  readonly status = input<string>();
  readonly message = input<string>();

  readonly labels = PLATFORM_LABELS;
  readonly formatDateTime = formatDateTime;

  accounts = signal<Account[]>([]);
  config = signal<AppConfig | null>(null);
  error = signal<string | null>(null);
  notice = signal<string | null>(null);
  busy = signal<Platform | null>(null);

  async ngOnInit() {
    const name = this.platform() ? PLATFORM_LABELS[this.platform()!.toUpperCase() as Platform] : '';
    if (this.status() === 'ok') this.notice.set(`Compte ${name} connecté.`);
    if (this.status() === 'error') this.error.set(`Connexion ${name} impossible : ${this.message() ?? 'erreur inconnue'}`);
    if (this.status()) this.router.navigate([], { queryParams: {}, replaceUrl: true });
    await this.load();
  }

  async load() {
    try {
      const [accounts, config] = await Promise.all([this.api.listAccounts(), this.api.config()]);
      this.accounts.set(accounts);
      this.config.set(config);
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }

  redirectUri(p: Platform): string {
    const c = this.config();
    if (!c) return '';
    return p === 'INSTAGRAM' ? c.instagramRedirectUri : c.tiktokRedirectUri;
  }

  async connect(p: Platform) {
    this.busy.set(p);
    try {
      const { url } = await this.api.authorizeUrl(p);
      window.location.href = url;
    } catch (e) {
      this.error.set(errorMessage(e));
      this.busy.set(null);
    }
  }

  async disconnect(p: Platform) {
    if (!confirm(`Déconnecter ${PLATFORM_LABELS[p]} ? Les posts programmés ne pourront plus y être publiés.`)) return;
    this.busy.set(p);
    try {
      await this.api.disconnect(p);
      await this.load();
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(null);
    }
  }

  async copy(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      this.notice.set('Copié dans le presse-papiers.');
    } catch {
      /* sélection manuelle possible */
    }
  }
}
