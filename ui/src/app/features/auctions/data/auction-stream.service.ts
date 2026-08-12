import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import Keycloak from 'keycloak-js';
import {
  AuctionSnapshotEvent,
  AuctionStreamEvent,
  BidEvent,
  WatchersEvent
} from './auction.models';

export type StreamStatus = 'closed' | 'connecting' | 'open' | 'reconnecting';

const INITIAL_BACKOFF_MS = 2_000;
const MAX_BACKOFF_MS = 30_000;

/**
 * SSE client for GET /api/v1/auctions/{id}/stream.
 *
 * The endpoint requires a Bearer JWT and native EventSource cannot set request
 * headers, so this service streams the response of a fetch() call instead: it
 * reads response.body chunk by chunk and parses text/event-stream frames
 * (event:/data:/id: lines, blank-line delimited) itself.
 *
 * Reconnects automatically with exponential backoff (2s, 4s, 8s … capped at
 * 30s). The server sends a fresh snapshot on every (re)connect, so consumers
 * can simply re-render from the snapshot event.
 *
 * Provide at component level so the stream's lifetime follows the page.
 */
@Injectable()
export class AuctionStreamService implements OnDestroy {
  private readonly keycloak = inject(Keycloak);

  private readonly eventsSubject = new Subject<AuctionStreamEvent>();
  /** Typed, named SSE events. Heartbeats and unknown events are filtered out. */
  readonly events: Observable<AuctionStreamEvent> = this.eventsSubject.asObservable();
  readonly status = signal<StreamStatus>('closed');

  private auctionId: string | null = null;
  private abortController: AbortController | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private backoffMs = INITIAL_BACKOFF_MS;
  private stopped = true;

  connect(auctionId: string): void {
    this.disconnect();
    this.auctionId = auctionId;
    this.stopped = false;
    this.backoffMs = INITIAL_BACKOFF_MS;
    this.status.set('connecting');
    void this.open();
  }

  disconnect(): void {
    this.stopped = true;
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.abortController?.abort();
    this.abortController = null;
    this.status.set('closed');
  }

  ngOnDestroy(): void {
    this.disconnect();
    this.eventsSubject.complete();
  }

  private async open(): Promise<void> {
    if (this.stopped || this.auctionId === null) {
      return;
    }
    const controller = new AbortController();
    this.abortController = controller;
    try {
      const token = await this.freshToken();
      const response = await fetch(`/api/v1/auctions/${this.auctionId}/stream`, {
        headers: {
          Accept: 'text/event-stream',
          Authorization: `Bearer ${token}`
        },
        signal: controller.signal
      });
      if (!response.ok || response.body === null) {
        throw new Error(`SSE connect failed: HTTP ${response.status}`);
      }
      this.status.set('open');
      this.backoffMs = INITIAL_BACKOFF_MS;
      await this.readStream(response.body);
      // The server closed the stream; treat it like a drop and reconnect.
      this.scheduleReconnect();
    } catch {
      if (this.stopped || controller.signal.aborted) {
        return;
      }
      this.scheduleReconnect();
    }
  }

  /**
   * Obtains the access token from the same Keycloak instance the
   * includeBearerTokenInterceptor uses, refreshing it first when it is about
   * to expire.
   */
  private async freshToken(): Promise<string> {
    try {
      await this.keycloak.updateToken(30);
    } catch {
      // Refresh failed; fall back to the current token and let the server decide.
    }
    const token = this.keycloak.token;
    if (!token) {
      throw new Error('No access token available');
    }
    return token;
  }

  private async readStream(body: ReadableStream<Uint8Array>): Promise<void> {
    const reader = body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      buffer = this.drainFrames(buffer);
    }
  }

  /** Dispatches every complete frame in the buffer; returns the unparsed remainder. */
  private drainFrames(buffer: string): string {
    const normalized = buffer.replace(/\r\n/g, '\n');
    const frames = normalized.split('\n\n');
    const remainder = frames.pop() ?? '';
    for (const frame of frames) {
      if (frame.trim().length > 0) {
        this.dispatchFrame(frame);
      }
    }
    return remainder;
  }

  private dispatchFrame(frame: string): void {
    let eventName = 'message';
    const dataLines: string[] = [];
    for (const line of frame.split('\n')) {
      if (line.length === 0 || line.startsWith(':')) {
        continue; // empty line or comment (used by some servers as keep-alive)
      }
      const separator = line.indexOf(':');
      const field = separator === -1 ? line : line.slice(0, separator);
      let value = separator === -1 ? '' : line.slice(separator + 1);
      if (value.startsWith(' ')) {
        value = value.slice(1);
      }
      if (field === 'event') {
        eventName = value;
      } else if (field === 'data') {
        dataLines.push(value);
      }
      // 'id' and 'retry' fields are intentionally ignored.
    }
    if (eventName === 'heartbeat' || dataLines.length === 0) {
      return; // heartbeat keep-alives carry no state
    }
    let payload: unknown;
    try {
      payload = JSON.parse(dataLines.join('\n'));
    } catch {
      console.warn(`Discarding malformed SSE '${eventName}' payload`);
      return;
    }
    switch (eventName) {
      case 'snapshot':
        this.eventsSubject.next({ type: 'snapshot', payload: payload as AuctionSnapshotEvent });
        break;
      case 'bid':
        this.eventsSubject.next({ type: 'bid', payload: payload as BidEvent });
        break;
      case 'watchers':
        this.eventsSubject.next({ type: 'watchers', payload: payload as WatchersEvent });
        break;
      default:
        break; // unknown event names are ignored
    }
  }

  private scheduleReconnect(): void {
    if (this.stopped) {
      return;
    }
    this.status.set('reconnecting');
    const delay = this.backoffMs;
    this.backoffMs = Math.min(this.backoffMs * 2, MAX_BACKOFF_MS);
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      void this.open();
    }, delay);
  }
}
