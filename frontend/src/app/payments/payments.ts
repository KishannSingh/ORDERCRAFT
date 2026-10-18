import { ChangeDetectorRef, Component, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { API_BASE_URL, apiErrorMessage } from '../api';

interface InvoiceRecord {
  invoiceId: number;
  amount: number;
  status: string;
}

interface PaymentRecord {
  paymentId: number;
  tempId?: number;
  invoiceId: number;
  paymentDate: string;
  amount: number;
  mode: string;
}

@Component({
  selector: 'app-payments',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './payments.html',
  styleUrl: './payments.css',
})
export class Payments implements OnInit {
  private readonly paymentsApi = `${API_BASE_URL}/payments`;
  private readonly invoicesApi = `${API_BASE_URL}/invoices`;

  payments = signal<PaymentRecord[]>([]);
  invoices: InvoiceRecord[] = [];

  showForm = false;
  editingId: number | null = null;
  isLoading = false;
  invoicesLoading = false;
  isSaving = false;
  deletingId: number | null = null;
  errorMessage = '';
  successMessage = '';
  readonly maxPaymentDate = this.today();

  newPayment = {
    invoiceId: 0,
    paymentDate: this.today(),
    amount: 0,
    mode: 'UPI',
  };

  constructor(private http: HttpClient, private readonly cdr: ChangeDetectorRef) {}

  private today(): string {
    const now = new Date();
    return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
  }

  ngOnInit(): void {
    this.loadPayments();
    this.loadInvoices();
  }

  loadPayments(): void {
    this.isLoading = true;
    this.http.get<PaymentRecord[]>(this.paymentsApi).subscribe({
      next: (data) => {
        this.payments.set(Array.isArray(data) ? data : []);
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = apiErrorMessage(error, 'Unable to load payments.');
        this.cdr.markForCheck();
      },
    });
  }

  loadInvoices(): void {
    this.invoicesLoading = true;
    this.http.get<InvoiceRecord[]>(this.invoicesApi).subscribe({
      next: (data) => {
        this.invoices = Array.isArray(data) ? data : [];
        this.invoicesLoading = false;
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.invoicesLoading = false;
        this.errorMessage = apiErrorMessage(error, 'Unable to load invoices for payment selection.');
        this.cdr.markForCheck();
      },
    });
  }

  remainingBalance(): number {
    const invoice = this.invoices.find((item) => Number(item.invoiceId) === Number(this.newPayment.invoiceId));
    if (!invoice) return 0;
    const paid = this.payments()
      .filter((payment) => Number(payment.invoiceId) === Number(invoice.invoiceId)
        && payment.paymentId !== this.editingId)
      .reduce((sum, payment) => sum + Number(payment.amount || 0), 0);
    return Math.max(0, Number(invoice.amount) - paid);
  }

  savePayment(): void {
    if (this.isSaving) return;
    this.errorMessage = '';
    this.successMessage = '';

    const invoiceId = Number(this.newPayment.invoiceId);
    const amount = Number(this.newPayment.amount);
    const paymentDate = this.newPayment.paymentDate;
    const mode = this.newPayment.mode.trim();

    if (!Number.isInteger(invoiceId) || invoiceId <= 0
        || !this.invoices.some((invoice) => Number(invoice.invoiceId) === invoiceId)) {
      this.errorMessage = 'Select an existing invoice.';
      return;
    }
    if (!paymentDate || !Number.isFinite(amount) || amount <= 0 || !mode) {
      this.errorMessage = 'Enter a payment date, a positive amount and a payment method.';
      return;
    }
    if (amount > this.remainingBalance() + 1e-9) {
      this.errorMessage = `Payment exceeds the outstanding invoice balance of ₹${this.remainingBalance().toFixed(2)}.`;
      return;
    }

    const payload = { invoiceId, paymentDate, amount, mode };
    this.isSaving = true;
    const request = this.editingId === null
      ? this.http.post<PaymentRecord>(this.paymentsApi, payload)
      : this.http.put<PaymentRecord>(`${this.paymentsApi}/${this.editingId}`, payload);

    request.subscribe({
      next: () => {
        this.isSaving = false;
        this.successMessage = this.editingId === null ? 'Payment recorded.' : 'Payment updated.';
        this.resetForm();
        this.loadPayments();
        this.loadInvoices();
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.isSaving = false;
        this.errorMessage = apiErrorMessage(error, 'Unable to save the payment.');
        this.cdr.markForCheck();
      },
    });
  }

  editPayment(payment: PaymentRecord): void {
    this.errorMessage = '';
    this.successMessage = '';
    this.editingId = Number(payment.paymentId);
    this.newPayment = {
      invoiceId: Number(payment.invoiceId),
      paymentDate: payment.paymentDate,
      amount: Number(payment.amount),
      mode: payment.mode,
    };
    this.showForm = true;
  }

  deletePayment(id: number): void {
    if (!Number.isInteger(id) || id <= 0 || this.deletingId !== null) return;
    if (!confirm('Are you sure you want to delete this payment?')) return;

    this.errorMessage = '';
    this.successMessage = '';
    this.deletingId = id;
    this.http.delete(`${this.paymentsApi}/${id}`).subscribe({
      next: () => {
        this.deletingId = null;
        this.successMessage = 'Payment deleted.';
        this.loadPayments();
        this.loadInvoices();
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.deletingId = null;
        this.errorMessage = apiErrorMessage(error, 'Unable to delete the payment.');
        this.cdr.markForCheck();
      },
    });
  }

  resetForm(): void {
    this.newPayment = {
      invoiceId: 0,
      paymentDate: this.today(),
      amount: 0,
      mode: 'UPI',
    };
    this.editingId = null;
    this.showForm = false;
  }

  cancelEdit(): void {
    this.resetForm();
  }
}
