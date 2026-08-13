import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import Keycloak from 'keycloak-js';
import { AuctionResponse } from '../data/auction.models';
import { AuctionsApiService, extractErrorMessage } from '../data/auctions-api.service';
import { CountdownComponent } from '../ui/countdown';
import { NotificationToggleComponent } from '../../notifications/ui/notification-toggle';

@Component({
  selector: 'app-auction-list',
  imports: [CurrencyPipe, RouterLink, CountdownComponent, NotificationToggleComponent],
  templateUrl: './auction-list.html',
  styleUrl: './auction-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AuctionListComponent implements OnInit {
  private readonly api = inject(AuctionsApiService);
  private readonly keycloak = inject(Keycloak);

  readonly auctions = signal<AuctionResponse[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  get username(): string {
    return this.keycloak.tokenParsed?.['preferred_username'] ?? 'unknown';
  }

  ngOnInit(): void {
    this.fetchAuctions();
  }

  fetchAuctions(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list().subscribe({
      next: (res) => {
        this.auctions.set(res.data);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(extractErrorMessage(err));
        this.loading.set(false);
      }
    });
  }

  logout(): void {
    this.keycloak.logout({ redirectUri: window.location.origin });
  }
}
