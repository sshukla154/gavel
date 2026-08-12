import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ApiEnvelope,
  AuctionResponse,
  BidAccepted,
  BidSummary,
  CreateAuctionRequest,
  PlaceBidRequest,
  ProblemDetails
} from './auction.models';

const BASE_URL = '/api/v1/auctions';

@Injectable({ providedIn: 'root' })
export class AuctionsApiService {
  private readonly http = inject(HttpClient);

  list(): Observable<ApiEnvelope<AuctionResponse[]>> {
    return this.http.get<ApiEnvelope<AuctionResponse[]>>(BASE_URL);
  }

  get(id: string): Observable<ApiEnvelope<AuctionResponse>> {
    return this.http.get<ApiEnvelope<AuctionResponse>>(`${BASE_URL}/${id}`);
  }

  bids(id: string): Observable<ApiEnvelope<BidSummary[]>> {
    return this.http.get<ApiEnvelope<BidSummary[]>>(`${BASE_URL}/${id}/bids`);
  }

  placeBid(id: string, request: PlaceBidRequest): Observable<ApiEnvelope<BidAccepted>> {
    return this.http.post<ApiEnvelope<BidAccepted>>(`${BASE_URL}/${id}/bids`, request);
  }

  close(id: string): Observable<ApiEnvelope<AuctionResponse>> {
    return this.http.post<ApiEnvelope<AuctionResponse>>(`${BASE_URL}/${id}/close`, null);
  }

  create(request: CreateAuctionRequest): Observable<ApiEnvelope<AuctionResponse>> {
    return this.http.post<ApiEnvelope<AuctionResponse>>(BASE_URL, request);
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
