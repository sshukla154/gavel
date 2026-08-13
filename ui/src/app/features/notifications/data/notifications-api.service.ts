import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ApiEnvelope,
  ProblemDetails,
  PushSubscriptionRequest,
  VapidPublicKey
} from './notification.models';

const BASE_URL = '/api/v1/notifications';

@Injectable({ providedIn: 'root' })
export class NotificationsApiService {
  private readonly http = inject(HttpClient);

  getVapidPublicKey(): Observable<ApiEnvelope<VapidPublicKey>> {
    return this.http.get<ApiEnvelope<VapidPublicKey>>(`${BASE_URL}/vapid-public-key`);
  }

  subscribe(subscription: PushSubscriptionRequest): Observable<void> {
    return this.http.post<void>(`${BASE_URL}/subscriptions`, subscription);
  }

  unsubscribe(endpoint: string): Observable<void> {
    return this.http.delete<void>(`${BASE_URL}/subscriptions`, { body: { endpoint } });
  }
}

/** Extracts a human-readable message from an RFC 7807 error response. */
export function extractErrorMessage(err: HttpErrorResponse): string {
  const problem = err.error as Partial<ProblemDetails> | null | undefined;
  if (problem && typeof problem === 'object') {
    if (typeof problem.detail === 'string' && problem.detail.length > 0) {
      return problem.detail;
    }
    if (typeof problem.title === 'string' && problem.title.length > 0) {
      return problem.title;
    }
  }
  return err.status > 0
    ? `HTTP ${err.status}: ${err.statusText}`
    : 'Network error — could not reach the server.';
}
