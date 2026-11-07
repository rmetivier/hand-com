import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export type Platform = 'INSTAGRAM' | 'TIKTOK';
export type PostStatus = 'DRAFT' | 'SCHEDULED' | 'PUBLISHING' | 'PUBLISHED' | 'PARTIAL' | 'FAILED';
export type PublicationStatus = 'SUCCESS' | 'DRAFT_SENT' | 'FAILED';

export interface Media {
  id: number;
  kind: 'IMAGE' | 'VIDEO';
  contentType: string;
  originalName: string;
  sizeBytes: number;
  previewUrl: string;
}

export interface Publication {
  platform: Platform;
  status: PublicationStatus;
  externalId: string | null;
  message: string | null;
  attemptedAt: string;
}

export interface Post {
  id: number;
  caption: string;
  media: Media[];
  platforms: Platform[];
  scheduledAt: string | null;
  status: PostStatus;
  publications: Publication[];
  createdAt: string;
  updatedAt: string;
}

export interface PostRequest {
  caption: string;
  mediaIds: number[];
  platforms: Platform[];
  scheduledAt: string | null;
}

export interface Account {
  platform: Platform;
  configured: boolean;
  connected: boolean;
  username: string | null;
  connectedAt: string | null;
  accessExpiresAt: string | null;
}

export interface AppConfig {
  publicBaseUrl: string;
  publicUrlReachable: boolean;
  instagramConfigured: boolean;
  tiktokConfigured: boolean;
  instagramRedirectUri: string;
  tiktokRedirectUri: string;
}

export const PLATFORM_LABELS: Record<Platform, string> = {
  INSTAGRAM: 'Instagram',
  TIKTOK: 'TikTok',
};

export const STATUS_LABELS: Record<PostStatus, string> = {
  DRAFT: 'Brouillon',
  SCHEDULED: 'Programmé',
  PUBLISHING: 'En cours',
  PUBLISHED: 'Publié',
  PARTIAL: 'Partiel',
  FAILED: 'Échec',
};

export const PUBLICATION_LABELS: Record<PublicationStatus, string> = {
  SUCCESS: 'Publié',
  DRAFT_SENT: 'Envoyé dans l’app',
  FAILED: 'Échec',
};

/** Message lisible à partir d'une erreur HTTP. */
export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) return 'Serveur injoignable : le backend est-il lancé ?';
    return err.error?.message ?? `Erreur ${err.status}`;
  }
  return err instanceof Error ? err.message : String(err);
}

@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  listPosts() {
    return firstValueFrom(this.http.get<Post[]>('/api/posts'));
  }
  getPost(id: number) {
    return firstValueFrom(this.http.get<Post>(`/api/posts/${id}`));
  }
  createPost(body: PostRequest) {
    return firstValueFrom(this.http.post<Post>('/api/posts', body));
  }
  updatePost(id: number, body: PostRequest) {
    return firstValueFrom(this.http.put<Post>(`/api/posts/${id}`, body));
  }
  deletePost(id: number) {
    return firstValueFrom(this.http.delete<void>(`/api/posts/${id}`));
  }
  publishNow(id: number) {
    return firstValueFrom(this.http.post<Post>(`/api/posts/${id}/publish-now`, {}));
  }

  uploadMedia(file: File) {
    const form = new FormData();
    form.append('file', file);
    return firstValueFrom(this.http.post<Media>('/api/media', form));
  }

  listAccounts() {
    return firstValueFrom(this.http.get<Account[]>('/api/accounts'));
  }
  disconnect(platform: Platform) {
    return firstValueFrom(this.http.delete<void>(`/api/accounts/${platform.toLowerCase()}`));
  }
  authorizeUrl(platform: Platform) {
    return firstValueFrom(this.http.get<{ url: string }>(`/api/oauth/${platform.toLowerCase()}/authorize-url`));
  }
  config() {
    return firstValueFrom(this.http.get<AppConfig>('/api/config'));
  }
}
