import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { NotificationsService } from '../data/notifications.service';

/**
 * Header control for opting in/out of "you've been outbid" Web Push alerts.
 * Renders an inline unsupported/error message instead of a toast — this app
 * has no toast library, every other feature surfaces errors as plain text.
 */
@Component({
  selector: 'app-notification-toggle',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!notifications.supported) {
      <span class="toggle-unsupported">Bid alerts aren't supported in this browser.</span>
    } @else {
      <button
        type="button"
        class="btn btn-toggle"
        [class.on]="notifications.subscribed()"
        [disabled]="notifications.busy()"
        (click)="toggle()"
      >
        {{ label() }}
      </button>
      @if (notifications.error()) {
        <p class="inline-error">{{ notifications.error() }}</p>
      }
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 0.25rem;
    }

    .toggle-unsupported {
      font-size: 0.75rem;
      color: rgba(255, 255, 255, 0.6);
    }

    .btn-toggle {
      cursor: pointer;
      border: 1px solid rgba(255, 255, 255, 0.4);
      border-radius: 4px;
      padding: 0.5rem 1.25rem;
      font-size: 0.875rem;
      background: transparent;
      color: #fff;
      transition: opacity 0.2s, background 0.2s, border-color 0.2s;

      &:hover {
        opacity: 0.85;
      }

      &:disabled {
        opacity: 0.5;
        cursor: default;
      }

      &.on {
        background: #27ae60;
        border-color: #27ae60;
      }
    }

    .inline-error {
      margin: 0.35rem 0 0;
      font-size: 0.75rem;
      color: #ffb4a8;
    }
  `
})
export class NotificationToggleComponent {
  readonly notifications = inject(NotificationsService);

  readonly label = computed(() => {
    if (this.notifications.busy()) {
      return this.notifications.subscribed() ? 'Disabling…' : 'Enabling…';
    }
    return this.notifications.subscribed() ? 'Disable bid alerts' : 'Enable bid alerts';
  });

  toggle(): void {
    if (this.notifications.subscribed()) {
      void this.notifications.optOut();
    } else {
      void this.notifications.optIn();
    }
  }
}
