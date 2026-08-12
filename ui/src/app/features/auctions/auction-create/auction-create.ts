import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CreateAuctionRequest } from '../data/auction.models';
import { AuctionsApiService, extractErrorMessage } from '../data/auctions-api.service';

function toDatetimeLocalValue(date: Date): string {
  const pad = (value: number): string => String(value).padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

@Component({
  selector: 'app-auction-create',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './auction-create.html',
  styleUrl: './auction-create.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AuctionCreateComponent {
  private readonly api = inject(AuctionsApiService);
  private readonly router = inject(Router);

  readonly form = inject(NonNullableFormBuilder).group({
    title: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
    reservePriceEuros: [0, [Validators.required, Validators.min(0)]],
    endsAt: ['', [Validators.required]]
  });

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  readonly minEndsAt = toDatetimeLocalValue(new Date());

  onSubmit(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const endsAtDate = new Date(value.endsAt);
    if (Number.isNaN(endsAtDate.getTime()) || endsAtDate.getTime() <= Date.now()) {
      this.error.set('End time must be in the future.');
      return;
    }
    const description = value.description.trim();
    const request: CreateAuctionRequest = {
      title: value.title.trim(),
      description: description.length > 0 ? description : null,
      reservePriceCents: Math.round(value.reservePriceEuros * 100),
      endsAt: endsAtDate.toISOString()
    };
    this.submitting.set(true);
    this.api.create(request).subscribe({
      next: (res) => {
        void this.router.navigate(['/auctions', res.data.id]);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.error.set(extractErrorMessage(err));
      }
    });
  }
}
