import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'auctions',
    pathMatch: 'full'
  },
  {
    path: 'ping',
    loadComponent: () => import('./features/ping/ping').then(m => m.PingComponent),
    canActivate: [authGuard]
  },
  {
    path: 'auctions',
    loadComponent: () =>
      import('./features/auctions/auction-list/auction-list').then(m => m.AuctionListComponent),
    canActivate: [authGuard]
  },
  {
    path: 'auctions/new',
    loadComponent: () =>
      import('./features/auctions/auction-create/auction-create').then(
        m => m.AuctionCreateComponent
      ),
    canActivate: [authGuard]
  },
  {
    path: 'auctions/:id',
    loadComponent: () =>
      import('./features/auctions/auction-detail/auction-detail').then(
        m => m.AuctionDetailComponent
      ),
    canActivate: [authGuard]
  }
];
