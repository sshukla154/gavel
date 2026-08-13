import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import Keycloak from 'keycloak-js';
import {
  AuctionResponse,
  AuctionStreamEvent,
  BidSummary
} from '../data/auction.models';
import { AuctionsApiService, extractErrorMessage } from '../data/auctions-api.service';
import { AuctionStreamService } from '../data/auction-stream.service';
import { CountdownComponent } from '../ui/countdown';

type BidState = 'idle' | 'submitting' | 'awaiting' | 'confirmed' | 'unconfirmed' | 'rejected';

const BID_CONFIRMATION_TIMEOUT_MS = 10_000;
const MAX_VISIBLE_BIDS = 50;

@Component({
  selector: 'app-auction-detail',
  imports: [CurrencyPipe, DatePipe, RouterLink, FormsModule, CountdownComponent],
  templateUrl: './auction-detail.html',
  styleUrl: './auction-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [AuctionStreamService]
})
export class AuctionDetailComponent implements OnInit {
  private readonly api = inject(AuctionsApiService);
  private readonly stream = inject(AuctionStreamService);
  private readonly keycloak = inject(Keycloak);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auctionId = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';

  readonly auction = signal<AuctionResponse | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly currentPriceCents = signal<number | null>(null);
  readonly watchers = signal<number | null>(null);
  readonly bids = signal<BidSummary[]>([]);
  readonly priceFlash = signal(false);
  readonly streamStatus = this.stream.status;

  readonly bidAmountEuros = signal<number | null>(null);
  readonly bidState = signal<BidState>('idle');
  readonly bidError = signal<string | null>(null);
  readonly rejectionReason = signal<string | null>(null);

  readonly justExtended = signal(false);

  readonly closing = signal(false);
  readonly closeError = signal<string | null>(null);

  readonly myUserId = this.keycloak.tokenParsed?.sub ?? null;

  private snapshotReceived = false;
  private pendingBid: { amountCents: number; bidderId: string } | null = null;
  private pendingTimer: ReturnType<typeof setTimeout> | null = null;
  private flashTimer: ReturnType<typeof setTimeout> | null = null;
  private extensionTimer: ReturnType<typeof setTimeout> | null = null;

  readonly displayPriceCents = computed(
    () => this.currentPriceCents() ?? this.auction()?.currentPriceCents ?? 0
  );

  readonly minBidEuros = computed(() => (this.displayPriceCents() + 1) / 100);

  readonly isClosed = computed(() => this.auction()?.status === 'CLOSED');

  /**
   * Show the close button to the seller. If the token carries no subject we
   * cannot tell, so show it anyway and let the backend enforce with a 403.
   */
  readonly canClose = computed(() => {
    const auction = this.auction();
    if (!auction || auction.status === 'CLOSED') {
      return false;
    }
    return this.myUserId === null || this.myUserId === auction.sellerId;
  });

  ngOnInit(): void {
    this.fetchAuction();
    this.fetchBidsFallback();
    this.stream.events
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => this.onStreamEvent(event));
    this.stream.connect(this.auctionId);
    this.destroyRef.onDestroy(() => {
      if (this.pendingTimer !== null) {
        clearTimeout(this.pendingTimer);
      }
      if (this.flashTimer !== null) {
        clearTimeout(this.flashTimer);
      }
      if (this.extensionTimer !== null) {
        clearTimeout(this.extensionTimer);
      }
    });
  }

  submitBid(): void {
    this.bidError.set(null);
    this.rejectionReason.set(null);
    const euros = this.bidAmountEuros();
    if (euros === null || Number.isNaN(euros)) {
      this.bidError.set('Enter a bid amount.');
      return;
    }
    const amountCents = Math.round(euros * 100);
    const current = this.displayPriceCents();
    if (amountCents <= current) {
      this.bidError.set(
        `Your bid must exceed the current price of €${(current / 100).toFixed(2)}.`
      );
      return;
    }
    this.bidState.set('submitting');
    this.api.placeBid(this.auctionId, { amountCents }).subscribe({
      next: (res) => {
        this.pendingBid = { amountCents: res.data.amountCents, bidderId: res.data.bidderId };
        this.bidState.set('awaiting');
        // The confirming bid event may already have arrived over the stream.
        this.checkPendingAgainstBids(this.bids());
        if (this.bidState() === 'awaiting') {
          this.startConfirmationTimeout();
        }
      },
      error: (err: HttpErrorResponse) => {
        this.bidState.set('idle');
        this.bidError.set(extractErrorMessage(err));
      }
    });
  }

  closeAuction(): void {
    this.closing.set(true);
    this.closeError.set(null);
    this.api.close(this.auctionId).subscribe({
      next: () => {
        this.closing.set(false);
        this.fetchAuction();
      },
      error: (err: HttpErrorResponse) => {
        this.closing.set(false);
        this.closeError.set(extractErrorMessage(err));
      }
    });
  }

  retry(): void {
    this.fetchAuction();
    this.fetchBidsFallback();
    this.stream.connect(this.auctionId);
  }

  private fetchAuction(): void {
    if (this.auction() === null) {
      this.loading.set(true);
    }
    this.loadError.set(null);
    this.api.get(this.auctionId).subscribe({
      next: (res) => {
        this.auction.set(res.data);
        if (this.currentPriceCents() === null) {
          this.currentPriceCents.set(res.data.currentPriceCents);
        }
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loadError.set(extractErrorMessage(err));
        this.loading.set(false);
      }
    });
  }

  /** Seeds the bid history over plain HTTP so the page works without SSE. */
  private fetchBidsFallback(): void {
    this.api.bids(this.auctionId).subscribe({
      next: (res) => {
        if (!this.snapshotReceived && this.bids().length === 0) {
          this.bids.set(this.normalize(res.data));
        }
      },
      error: () => {
        // Fallback only — the stream snapshot populates the panel when it arrives.
      }
    });
  }

  private onStreamEvent(event: AuctionStreamEvent): void {
    switch (event.type) {
      case 'snapshot': {
        this.snapshotReceived = true;
        this.updatePrice(event.payload.currentPriceCents);
        this.watchers.set(event.payload.watchers);
        this.bids.set(this.normalize(event.payload.recentBids));
        this.checkPendingAgainstBids(this.bids());
        break;
      }
      case 'bid': {
        const bid = event.payload;
        const summary: BidSummary = {
          id: bid.bidId,
          auctionId: bid.auctionId,
          bidderId: bid.bidderId,
          amountCents: bid.amountCents,
          placedAt: bid.placedAt
        };
        this.bids.update((list) =>
          list.some((existing) => existing.id === summary.id)
            ? list
            : [summary, ...list].slice(0, MAX_VISIBLE_BIDS)
        );
        if (bid.amountCents > this.displayPriceCents()) {
          this.updatePrice(bid.amountCents);
        }
        this.resolvePending(bid.bidderId, bid.amountCents);
        break;
      }
      case 'watchers': {
        this.watchers.set(event.payload.count);
        break;
      }
      case 'extended': {
        this.auction.update((a) => (a ? { ...a, endsAt: event.payload.endsAt } : a));
        this.triggerExtensionFlash();
        break;
      }
      case 'closed': {
        this.auction.update((a) => (a ? { ...a, status: 'CLOSED' } : a));
        break;
      }
      case 'rejected': {
        this.resolveRejection(event.payload.bidderId, event.payload.amountCents, event.payload.reason);
        break;
      }
    }
  }

  /** Newest first, deduplicated by bid id. */
  private normalize(bids: BidSummary[]): BidSummary[] {
    const seen = new Set<string>();
    return bids
      .filter((bid) => (seen.has(bid.id) ? false : (seen.add(bid.id), true)))
      .sort((a, b) => new Date(b.placedAt).getTime() - new Date(a.placedAt).getTime());
  }

  private updatePrice(cents: number): void {
    if (this.currentPriceCents() === cents) {
      return;
    }
    this.currentPriceCents.set(cents);
    this.triggerFlash();
  }

  private triggerFlash(): void {
    if (this.flashTimer !== null) {
      clearTimeout(this.flashTimer);
    }
    // Drop the class first so a rapid follow-up change restarts the animation.
    this.priceFlash.set(false);
    setTimeout(() => {
      this.priceFlash.set(true);
      this.flashTimer = setTimeout(() => this.priceFlash.set(false), 700);
    });
  }

  private resolvePending(bidderId: string, amountCents: number): void {
    if (
      this.pendingBid !== null &&
      this.pendingBid.bidderId === bidderId &&
      this.pendingBid.amountCents === amountCents
    ) {
      this.confirmPending();
    }
  }

  private resolveRejection(bidderId: string, amountCents: number, reason: string): void {
    if (
      this.pendingBid === null ||
      this.pendingBid.bidderId !== bidderId ||
      this.pendingBid.amountCents !== amountCents
    ) {
      return; // a rejection for someone else's bid
    }
    this.pendingBid = null;
    if (this.pendingTimer !== null) {
      clearTimeout(this.pendingTimer);
      this.pendingTimer = null;
    }
    this.rejectionReason.set(
      reason === 'AUCTION_CLOSED' ? 'The auction closed before your bid was processed.' : reason
    );
    this.bidState.set('rejected');
  }

  private triggerExtensionFlash(): void {
    if (this.extensionTimer !== null) {
      clearTimeout(this.extensionTimer);
    }
    this.justExtended.set(true);
    this.extensionTimer = setTimeout(() => this.justExtended.set(false), 4000);
  }

  private checkPendingAgainstBids(bids: BidSummary[]): void {
    const pending = this.pendingBid;
    if (
      pending !== null &&
      bids.some((bid) => bid.bidderId === pending.bidderId && bid.amountCents === pending.amountCents)
    ) {
      this.confirmPending();
    }
  }

  private confirmPending(): void {
    this.pendingBid = null;
    if (this.pendingTimer !== null) {
      clearTimeout(this.pendingTimer);
      this.pendingTimer = null;
    }
    this.bidState.set('confirmed');
    this.bidAmountEuros.set(null);
    setTimeout(() => {
      if (this.bidState() === 'confirmed') {
        this.bidState.set('idle');
      }
    }, 4000);
  }

  private startConfirmationTimeout(): void {
    if (this.pendingTimer !== null) {
      clearTimeout(this.pendingTimer);
    }
    this.pendingTimer = setTimeout(() => {
      this.pendingTimer = null;
      if (this.bidState() === 'awaiting') {
        this.pendingBid = null;
        this.bidState.set('unconfirmed');
      }
    }, BID_CONFIRMATION_TIMEOUT_MS);
  }
}
