import { Injectable, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { SwPush } from '@angular/service-worker';
import { firstValueFrom, map } from 'rxjs';
import { OutbidNotificationData } from './notification.models';
import { NotificationsApiService, extractErrorMessage } from './notifications-api.service';

/**
 * Signal-based facade over Angular's SwPush for the "bid alerts" opt-in.
 *
 * Wraps subscribe/unsubscribe with the backend calls that keep
 * notification-service's subscription store in sync with the browser's
 * PushSubscription, and surfaces support/permission failures as plain text
 * (this app has no toast library — every other feature reports errors inline).
 */
@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly swPush = inject(SwPush);
  private readonly api = inject(NotificationsApiService);
  private readonly router = inject(Router);

  /** False whenever the browser lacks Service Worker/Push support, or the worker is disabled (e.g. `ng serve`). */
  readonly supported = this.swPush.isEnabled;

  private readonly currentSubscription = toSignal(this.swPush.subscription, { initialValue: null });
  readonly subscribed = computed(() => this.currentSubscription() !== null);

  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    this.swPush.notificationClicks.subscribe(({ notification }) => {
      const data = notification.data as Partial<OutbidNotificationData> | undefined;
      if (data?.auctionId) {
        void this.router.navigate(['/auctions', data.auctionId]);
      }
    });
  }

  /** Requests notification permission, subscribes to Web Push, and registers the subscription with the backend. */
  async optIn(): Promise<void> {
    if (!this.supported) {
      this.error.set('Push notifications are not supported in this browser.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const keyRes = await firstValueFrom(this.api.getVapidPublicKey());
      const subscription = await this.swPush.requestSubscription({
        serverPublicKey: keyRes.data.publicKey
      });
      const json = subscription.toJSON();
      if (!json.endpoint || !json.keys?.['p256dh'] || !json.keys?.['auth']) {
        throw new Error('Push subscription is missing required fields.');
      }
      await firstValueFrom(
        this.api.subscribe({
          endpoint: json.endpoint,
          keys: { p256dh: json.keys['p256dh'], auth: json.keys['auth'] }
        })
      );
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.busy.set(false);
    }
  }

  /** Unsubscribes locally, then tells the backend to drop the subscription by endpoint. */
  async optOut(): Promise<void> {
    const subscription = this.currentSubscription();
    if (subscription === null) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    const endpoint = subscription.endpoint;
    try {
      await this.swPush.unsubscribe();
      await firstValueFrom(this.api.unsubscribe(endpoint));
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.busy.set(false);
    }
  }

  private describeError(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      return extractErrorMessage(err);
    }
    if (err instanceof DOMException && err.name === 'NotAllowedError') {
      return 'Notification permission was denied. Allow notifications for this site in your browser settings to receive bid alerts.';
    }
    if (err instanceof Error) {
      return err.message;
    }
    return 'Something went wrong while updating bid alerts.';
  }
}
