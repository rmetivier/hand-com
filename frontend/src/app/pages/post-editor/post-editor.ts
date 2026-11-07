import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  Api,
  Media,
  PLATFORM_LABELS,
  PUBLICATION_LABELS,
  Platform,
  Post,
  STATUS_LABELS,
  errorMessage,
} from '../../core/api';
import { formatDateTime, toLocalInput } from '../../core/dates';
import { TEMPLATES, TemplateFields, TemplateId, buildCaption, emptyFields } from '../../core/templates';

const CLUB_KEY = 'hand-com-club';
const HASHTAGS_KEY = 'hand-com-hashtags';
const MAX_CAPTION = 2200;

function load(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function save(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* préférence non mémorisée, sans conséquence */
  }
}

@Component({
  selector: 'app-post-editor',
  imports: [FormsModule, RouterLink],
  templateUrl: './post-editor.html',
})
export class PostEditor implements OnInit {
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  /** Paramètre de route :id (absent pour un nouveau post). */
  readonly id = input<string>();
  /** Paramètre ?date=AAAA-MM-JJ venant du calendrier. */
  readonly date = input<string>();

  readonly templates = TEMPLATES;
  readonly platformLabels = PLATFORM_LABELS;
  readonly statusLabels = STATUS_LABELS;
  readonly publicationLabels = PUBLICATION_LABELS;
  readonly formatDateTime = formatDateTime;
  readonly maxCaption = MAX_CAPTION;

  post = signal<Post | null>(null);
  caption = signal('');
  media = signal<Media[]>([]);
  instagram = signal(true);
  tiktok = signal(false);
  scheduled = signal(true);
  scheduledLocal = signal('');

  template = signal<TemplateId>('match');
  fields: TemplateFields = emptyFields();

  uploading = signal(0);
  busy = signal(false);
  error = signal<string | null>(null);
  notice = signal<string | null>(null);

  readonly editable = computed(() => {
    const s = this.post()?.status;
    return !s || s === 'DRAFT' || s === 'SCHEDULED' || s === 'FAILED' || s === 'PARTIAL';
  });

  readonly platforms = computed<Platform[]>(() => [
    ...(this.instagram() ? (['INSTAGRAM'] as const) : []),
    ...(this.tiktok() ? (['TIKTOK'] as const) : []),
  ]);

  /** Règles des réseaux, vérifiées avant l'envoi. */
  readonly contentProblems = computed(() => {
    const list: string[] = [];
    const media = this.media();
    const videos = media.filter((m) => m.kind === 'VIDEO').length;
    if (this.platforms().length === 0) list.push('Choisissez au moins un réseau.');
    if (media.length === 0) list.push('Ajoutez au moins une photo ou une vidéo.');
    if (this.instagram() && media.length > 10) list.push('Instagram : 10 médias maximum par carrousel.');
    if (this.tiktok() && (media.length !== 1 || videos !== 1)) list.push('TikTok : il faut exactement une vidéo (pas de photo).');
    if (this.caption().length > MAX_CAPTION) list.push(`Texte trop long (${MAX_CAPTION} caractères maximum).`);
    return list;
  });

  /** Problèmes bloquant la programmation (contenu + date). */
  readonly problems = computed(() => {
    const list = [...this.contentProblems()];
    if (this.scheduled()) {
      if (!this.scheduledLocal()) list.push('Choisissez la date et l’heure de publication.');
      else if (new Date(this.scheduledLocal()) < new Date()) list.push('La date de publication est déjà passée.');
    }
    return list;
  });

  readonly format = computed(() => {
    const m = this.media();
    if (m.length === 0) return '';
    if (m.length > 1) return `Carrousel de ${m.length} médias`;
    return m[0].kind === 'VIDEO' ? 'Vidéo (Reel sur Instagram)' : 'Photo';
  });

  async ngOnInit() {
    this.fields.club = load(CLUB_KEY) ?? '';
    this.fields.hashtags = load(HASHTAGS_KEY) ?? this.fields.hashtags;

    const id = this.id();
    if (id) {
      this.template.set('libre');
      try {
        this.fill(await this.api.getPost(Number(id)));
      } catch (e) {
        this.error.set(errorMessage(e));
      }
    } else {
      const base = this.date() ? new Date(`${this.date()}T18:00:00`) : this.defaultDate();
      this.scheduledLocal.set(toLocalInput(base));
      if (this.date()) this.fields.date = this.date()!;
    }
  }

  private defaultDate(): Date {
    const d = new Date();
    d.setHours(d.getHours() + 1, 0, 0, 0);
    return d;
  }

  private fill(p: Post) {
    this.post.set(p);
    this.caption.set(p.caption ?? '');
    this.media.set(p.media);
    this.instagram.set(p.platforms.includes('INSTAGRAM'));
    this.tiktok.set(p.platforms.includes('TIKTOK'));
    this.scheduled.set(!!p.scheduledAt);
    this.scheduledLocal.set(p.scheduledAt ? toLocalInput(new Date(p.scheduledAt)) : toLocalInput(this.defaultDate()));
  }

  // ---------------------------------------------------------------- Modèles

  generate() {
    save(CLUB_KEY, this.fields.club);
    save(HASHTAGS_KEY, this.fields.hashtags);
    const text = buildCaption(this.template(), this.fields);
    if (text) this.caption.set(text);
  }

  // ----------------------------------------------------------------- Médias

  async onFiles(event: Event) {
    const inputEl = event.target as HTMLInputElement;
    const files = Array.from(inputEl.files ?? []);
    inputEl.value = '';
    this.error.set(null);
    for (const file of files) {
      this.uploading.update((n) => n + 1);
      try {
        const m = await this.api.uploadMedia(file);
        this.media.update((list) => [...list, m]);
      } catch (e) {
        this.error.set(`${file.name} : ${errorMessage(e)}`);
      } finally {
        this.uploading.update((n) => n - 1);
      }
    }
  }

  move(index: number, delta: number) {
    this.media.update((list) => {
      const copy = [...list];
      const target = index + delta;
      if (target < 0 || target >= copy.length) return list;
      [copy[index], copy[target]] = [copy[target], copy[index]];
      return copy;
    });
  }

  remove(index: number) {
    this.media.update((list) => list.filter((_, i) => i !== index));
  }

  // ---------------------------------------------------------------- Actions

  private request(asDraft: boolean) {
    return {
      caption: this.caption(),
      mediaIds: this.media().map((m) => m.id),
      platforms: this.platforms(),
      scheduledAt: asDraft || !this.scheduled() ? null : new Date(this.scheduledLocal()).toISOString(),
    };
  }

  private async persist(asDraft: boolean): Promise<Post> {
    const current = this.post();
    const body = this.request(asDraft);
    return current ? this.api.updatePost(current.id, body) : this.api.createPost(body);
  }

  async submit() {
    await this.run(async () => {
      await this.persist(false);
      this.router.navigate(['/calendrier']);
    });
  }

  async saveDraft() {
    await this.run(async () => {
      const p = await this.persist(true);
      this.fill(p);
      this.notice.set('Brouillon enregistré.');
      if (!this.id()) this.router.navigate(['/posts', p.id], { replaceUrl: true });
    });
  }

  async publishNow() {
    if (!confirm('Publier ce post maintenant ?')) return;
    await this.run(async () => {
      const p = await this.persist(true);
      await this.api.publishNow(p.id);
      this.router.navigate(['/calendrier']);
    });
  }

  async remove_() {
    const p = this.post();
    if (!p || !confirm('Supprimer ce post ?')) return;
    await this.run(async () => {
      await this.api.deletePost(p.id);
      this.router.navigate(['/calendrier']);
    });
  }

  async copyCaption() {
    try {
      await navigator.clipboard.writeText(this.caption());
      this.notice.set('Texte copié : collez-le dans TikTok au moment de publier.');
    } catch {
      this.error.set('Copie impossible, sélectionnez le texte à la main.');
    }
  }

  private async run(action: () => Promise<void>) {
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    try {
      await action();
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
