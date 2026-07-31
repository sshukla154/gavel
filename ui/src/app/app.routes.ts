import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'ping',
    pathMatch: 'full'
  },
  {
    path: 'ping',
    loadComponent: () => import('./features/ping/ping').then(m => m.PingComponent),
    canActivate: [authGuard]
  }
];
