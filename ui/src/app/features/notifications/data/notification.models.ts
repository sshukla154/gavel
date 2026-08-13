/** Standard response envelope used by all Gavel REST endpoints. */
export interface ApiEnvelope<T> {
  data: T;
  timestamp: string;
}

export interface VapidPublicKey {
  publicKey: string;
}

/** Mirrors the browser's PushSubscriptionJSON — sent to the backend verbatim. */
export interface PushSubscriptionKeys {
  p256dh: string;
  auth: string;
}

export interface PushSubscriptionRequest {
  endpoint: string;
  keys: PushSubscriptionKeys;
}

/** RFC 7807 problem details returned by the backend on errors. */
export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}

/** The `notification.data` payload notification-service attaches to an outbid push. */
export interface OutbidNotificationData {
  auctionId: string;
}
