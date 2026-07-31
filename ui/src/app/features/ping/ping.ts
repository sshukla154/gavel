import { Component, inject, signal, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import Keycloak from 'keycloak-js';

interface PingData {
  status: string;
  service: string;
  totalVisits: number;
}

interface PingResponse {
  data: PingData;
  timestamp: string;
}

@Component({
  selector: 'app-ping',
  templateUrl: './ping.html',
  styleUrl: './ping.scss'
})
export class PingComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly keycloak = inject(Keycloak);

  readonly ping = signal<PingResponse | null>(null);
  readonly error = signal<string | null>(null);
  readonly loading = signal(false);

  get username(): string {
    return this.keycloak.tokenParsed?.['preferred_username'] ?? 'unknown';
  }

  ngOnInit(): void {
    this.fetchPing();
  }

  fetchPing(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<PingResponse>('/api/v1/ping').subscribe({
      next: (res) => {
        this.ping.set(res);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(`HTTP ${err.status}: ${err.statusText}`);
        this.loading.set(false);
      }
    });
  }

  logout(): void {
    this.keycloak.logout({ redirectUri: window.location.origin });
  }
}
