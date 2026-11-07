import { Routes } from '@angular/router';
import { authGuard } from './core/auth';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'calendrier' },
  { path: 'login', loadComponent: () => import('./pages/login/login').then((m) => m.Login) },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: 'calendrier', loadComponent: () => import('./pages/calendar/calendar').then((m) => m.Calendar) },
      { path: 'posts/nouveau', loadComponent: () => import('./pages/post-editor/post-editor').then((m) => m.PostEditor) },
      { path: 'posts/:id', loadComponent: () => import('./pages/post-editor/post-editor').then((m) => m.PostEditor) },
      { path: 'comptes', loadComponent: () => import('./pages/accounts/accounts').then((m) => m.Accounts) },
    ],
  },
  { path: '**', redirectTo: 'calendrier' },
];
