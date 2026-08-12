import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  signal
} from '@angular/core';

/**
 * Ticking countdown to an ISO instant. Shows mm:ss under an hour (turning red
 * under one minute), coarser units for longer spans, and "Ended" after expiry.
 */
@Component({
  selector: 'app-countdown',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="countdown" [class.urgent]="urgent()" [class.ended]="ended()">{{ label() }}</span>`,
  styles: `
    .countdown {
      font-variant-numeric: tabular-nums;
      font-weight: 600;
    }
    .urgent {
      color: #c0392b;
    }
    .ended {
      color: #888;
      font-weight: 400;
    }
  `
})
export class CountdownComponent {
  readonly endsAt = input.required<string>();

  private readonly now = signal(Date.now());

  readonly remainingMs = computed(() => new Date(this.endsAt()).getTime() - this.now());
  readonly ended = computed(() => this.remainingMs() <= 0);
  readonly urgent = computed(() => !this.ended() && this.remainingMs() < 60_000);

  readonly label = computed(() => {
    const ms = this.remainingMs();
    if (ms <= 0) {
      return 'Ended';
    }
    const totalSeconds = Math.floor(ms / 1000);
    const days = Math.floor(totalSeconds / 86_400);
    const hours = Math.floor((totalSeconds % 86_400) / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    if (days > 0) {
      return `${days}d ${hours}h`;
    }
    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    }
    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  });

  constructor() {
    const intervalId = setInterval(() => this.now.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(intervalId));
  }
}
