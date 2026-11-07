import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Api, PLATFORM_LABELS, Post, PostStatus, STATUS_LABELS, errorMessage } from '../../core/api';
import { dayKey, formatDateTime, formatTime } from '../../core/dates';

interface Day {
  date: Date;
  key: string;
  inMonth: boolean;
  isToday: boolean;
  posts: Post[];
}

@Component({
  selector: 'app-calendar',
  imports: [RouterLink],
  templateUrl: './calendar.html',
})
export class Calendar implements OnInit, OnDestroy {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private timer?: ReturnType<typeof setInterval>;

  readonly statusLabels = STATUS_LABELS;
  readonly legend: PostStatus[] = ['DRAFT', 'SCHEDULED', 'PUBLISHED', 'PARTIAL', 'FAILED'];
  readonly platformLabels = PLATFORM_LABELS;
  readonly weekdays = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];
  readonly formatTime = formatTime;
  readonly formatDateTime = formatDateTime;

  posts = signal<Post[]>([]);
  error = signal<string | null>(null);
  /** Premier jour du mois affiché. */
  month = signal(new Date(new Date().getFullYear(), new Date().getMonth(), 1));

  monthLabel = computed(() => this.month().toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' }));

  drafts = computed(() => this.posts().filter((p) => !p.scheduledAt));

  upcoming = computed(() =>
    this.posts()
      .filter((p) => p.scheduledAt && (p.status === 'SCHEDULED' || p.status === 'PUBLISHING'))
      .sort((a, b) => a.scheduledAt!.localeCompare(b.scheduledAt!))
      .slice(0, 5),
  );

  days = computed<Day[]>(() => {
    const first = this.month();
    const byDay = new Map<string, Post[]>();
    for (const p of this.posts()) {
      if (!p.scheduledAt) continue;
      const k = dayKey(new Date(p.scheduledAt));
      byDay.set(k, [...(byDay.get(k) ?? []), p]);
    }
    byDay.forEach((list) => list.sort((a, b) => a.scheduledAt!.localeCompare(b.scheduledAt!)));

    const offset = (first.getDay() + 6) % 7; // lundi = 0
    const start = new Date(first.getFullYear(), first.getMonth(), 1 - offset);
    const todayKey = dayKey(new Date());
    const days: Day[] = [];
    for (let i = 0; i < 42; i++) {
      const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i);
      const key = dayKey(date);
      days.push({
        date,
        key,
        inMonth: date.getMonth() === first.getMonth(),
        isToday: key === todayKey,
        posts: byDay.get(key) ?? [],
      });
    }
    // Retire la dernière semaine si elle est entièrement hors du mois
    return days.slice(35).every((d) => !d.inMonth) ? days.slice(0, 35) : days;
  });

  ngOnInit() {
    this.load();
    // Rafraîchit les statuts pendant qu'une publication est en cours
    this.timer = setInterval(() => {
      if (this.posts().some((p) => p.status === 'PUBLISHING' || this.isDue(p))) this.load();
    }, 10000);
  }

  ngOnDestroy() {
    clearInterval(this.timer);
  }

  async load() {
    try {
      this.posts.set(await this.api.listPosts());
      this.error.set(null);
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }

  shift(months: number) {
    const m = this.month();
    this.month.set(new Date(m.getFullYear(), m.getMonth() + months, 1));
  }

  today() {
    const now = new Date();
    this.month.set(new Date(now.getFullYear(), now.getMonth(), 1));
  }

  newOn(day: Day) {
    this.router.navigate(['/posts/nouveau'], { queryParams: { date: day.key } });
  }

  thumb(p: Post): string | null {
    const img = p.media.find((m) => m.kind === 'IMAGE');
    return img ? img.previewUrl : null;
  }

  private isDue(p: Post): boolean {
    return p.status === 'SCHEDULED' && !!p.scheduledAt && new Date(p.scheduledAt) <= new Date();
  }
}
