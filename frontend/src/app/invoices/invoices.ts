import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, map, Observable, of, Subject, switchMap } from 'rxjs';
import { API_BASE_URL, apiErrorMessage } from '../api';

interface InvoiceRecord {
  invoiceId: number;
  orderId: number;
  invoiceDate: string;
  amount: number;
  status: string;
}

interface PaymentRecord {
  paymentId: number;
  invoiceId: number;
  paymentDate: string;
  amount: number;
  mode: string;
}

interface SalesOrderRecord {
  orderId: number;
  customerId: number;
  orderDate: string;
  status: string;
  totalAmount: number;
}

interface CustomerRecord {
  customerId: number;
  name: string;
  email?: string;
  phone?: string;
}

interface ListResult<T> {
  records: T[] | null;
  error?: string;
}

@Component({
  selector: 'app-invoices',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './invoices.html',
  styleUrl: './invoices.css',
})
export class Invoices implements OnInit {
  private readonly invoiceApi = `${API_BASE_URL}/invoices`;
  private readonly paymentApi = `${API_BASE_URL}/payments`;
  private readonly orderApi = `${API_BASE_URL}/orders`;
  private readonly customerApi = `${API_BASE_URL}/customers`;
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly invoiceRequests = new Subject<void>();
  private readonly paymentRequests = new Subject<void>();
  private readonly orderRequests = new Subject<void>();
  private readonly customerRequests = new Subject<void>();
  private readonly paidTotalsCents = new Map<number, number>();

  invoices: InvoiceRecord[] = [];
  payments: PaymentRecord[] = [];
  orders: SalesOrderRecord[] = [];
  customers: CustomerRecord[] = [];

  loading = false;
  paymentsLoading = false;
  ordersLoading = false;
  customersLoading = false;
  isRefreshing = false;
  saving = false;
  showForm = false;

  editingId: number | null = null;
  deletingId: number | null = null;

  errorMessage = '';
  successMessage = '';
  invoicesError = '';
  paymentsError = '';
  ordersError = '';
  customersError = '';

  form = {
    orderId: 0,
    invoiceDate: '',
    amount: 0,
  };

  constructor(private readonly http: HttpClient) {
    this.bindLoader<InvoiceRecord>(this.invoiceRequests, this.invoiceApi, 'Invoice list', (result) => {
      this.invoices = result.records ?? [];
      this.invoicesError = result.error ?? '';
      this.loading = false;
      this.updateRefreshing();
    });
    this.bindLoader<PaymentRecord>(this.paymentRequests, this.paymentApi, 'Payment list', (result) => {
      this.payments = result.records ?? [];
      this.rebuildPaymentTotals();
      this.paymentsError = result.error ?? '';
      this.paymentsLoading = false;
      this.updateRefreshing();
    });
    this.bindLoader<SalesOrderRecord>(this.orderRequests, this.orderApi, 'Sales order list', (result) => {
      this.orders = result.records ?? [];
      this.ordersError = result.error ?? '';
      this.ordersLoading = false;
      this.updateRefreshing();
    });
    this.bindLoader<CustomerRecord>(this.customerRequests, this.customerApi, 'Customer list', (result) => {
      this.customers = result.records ?? [];
      this.customersError = result.error ?? '';
      this.customersLoading = false;
      this.updateRefreshing();
    });
  }

  ngOnInit(): void {
    this.refreshData();
  }

  refreshData(force = false): void {
    // The toolbar is disabled while refreshing. Mutations pass force=true so a newer
    // persisted state always cancels an older refresh before it can be applied.
    if (this.isRefreshing && !force) return;
    this.isRefreshing = true;
    this.loading = true;
    this.paymentsLoading = true;
    this.ordersLoading = true;
    this.customersLoading = true;
    this.invoicesError = '';
    this.paymentsError = '';
    this.ordersError = '';
    this.customersError = '';
    this.invoiceRequests.next();
    this.paymentRequests.next();
    this.orderRequests.next();
    this.customerRequests.next();
  }

  get totalAmountReceived(): number {
    return this.invoices.reduce((sum, invoice) => sum + this.getPaidAmount(invoice), 0);
  }

  get totalBalanceOutstanding(): number {
    return this.invoices.reduce((sum, invoice) => sum + this.getBalanceDue(invoice), 0);
  }

  getPaidAmount(invoice: InvoiceRecord): number {
    const invoiceId = Number(invoice?.invoiceId);
    if (!Number.isSafeInteger(invoiceId) || invoiceId <= 0) return 0;

    return (this.paidTotalsCents.get(invoiceId) ?? 0) / 100;
  }

  getBalanceDue(invoice: InvoiceRecord): number {
    const amountCents = Math.max(0, Math.round(this.toNumber(invoice?.amount) * 100));
    const paidCents = Math.round(this.getPaidAmount(invoice) * 100);
    return Math.max(0, amountCents - paidCents) / 100;
  }

  getPaymentProgress(invoice: InvoiceRecord): number {
    const amountCents = Math.round(this.toNumber(invoice?.amount) * 100);
    if (amountCents <= 0) return 0;
    const paidCents = Math.round(this.getPaidAmount(invoice) * 100);
    return Math.max(0, Math.min(100, Math.round((paidCents / amountCents) * 100)));
  }

  getPaymentStatus(invoice: InvoiceRecord): string {
    if (this.paymentsError) return this.normalizePaymentStatus(invoice?.status);

    const amountCents = Math.round(this.toNumber(invoice?.amount) * 100);
    const paidCents = Math.round(this.getPaidAmount(invoice) * 100);
    if (amountCents > 0 && paidCents >= amountCents) return 'PAID';
    if (paidCents > 0) return 'PARTIAL';
    return 'UNPAID';
  }

  getOrderLabel(orderId: number | string): string {
    const order = this.orders.find((item) => Number(item.orderId) === Number(orderId));
    return order ? `SO-${order.orderId}` : `SO-${orderId}`;
  }

  getOrderCustomerName(orderId: number | string): string {
    const order = this.orders.find((item) => Number(item.orderId) === Number(orderId));
    if (!order) return this.ordersLoading ? 'Loading sales order…' : 'Sales order details unavailable';
    const customer = this.customers.find((item) => Number(item.customerId) === Number(order.customerId));
    if (customer?.name?.trim()) return customer.name.trim();
    if (this.customersLoading) return 'Loading customer…';
    return this.customersError ? 'Customer details unavailable' : `Customer #${order.customerId}`;
  }

  hasInvoiceForOrder(orderId: number): boolean {
    return this.invoices.some((invoice) =>
      Number(invoice.orderId) === Number(orderId) && Number(invoice.invoiceId) !== this.editingId,
    );
  }

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 2,
    }).format(this.toNumber(value));
  }

  openForm(): void {
    this.errorMessage = '';
    this.successMessage = '';
    this.editingId = null;
    this.form = {
      orderId: 0,
      invoiceDate: new Date().toISOString().slice(0, 10),
      amount: 0,
    };
    this.showForm = true;
  }

  closeForm(): void {
    this.showForm = false;
    this.editingId = null;
    this.resetForm();
  }

  editInvoice(invoice: InvoiceRecord): void {
    this.errorMessage = '';
    this.successMessage = '';
    this.editingId = Number(invoice.invoiceId);
    this.form = {
      orderId: Number(invoice.orderId),
      invoiceDate: invoice.invoiceDate ?? '',
      amount: this.toNumber(invoice.amount),
    };
    this.showForm = true;
  }

  saveInvoice(): void {
    if (this.saving) return;
    this.errorMessage = '';
    this.successMessage = '';

    const selectedOrder = this.orders.find((order) => Number(order.orderId) === Number(this.form.orderId));
    if (!selectedOrder || !this.form.invoiceDate) {
      this.errorMessage = 'Select an existing sales order and enter an invoice date.';
      return;
    }
    if (this.editingId === null && this.hasInvoiceForOrder(selectedOrder.orderId)) {
      this.errorMessage = 'This sales order already has an invoice.';
      return;
    }
    const amount = this.toNumber(selectedOrder.totalAmount);
    if (!Number.isFinite(amount) || amount <= 0) {
      this.errorMessage = 'The sales order must have a positive total before it can be invoiced.';
      return;
    }

    const existingInvoice = this.invoices.find((item) => Number(item.invoiceId) === this.editingId);
    const wasEdit = this.editingId !== null;
    const invoiceData = {
      orderId: Number(selectedOrder.orderId),
      invoiceDate: this.form.invoiceDate,
      amount,
      status: existingInvoice?.status ?? 'UNPAID',
    };

    this.saving = true;
    const request = this.editingId === null
      ? this.http.post<InvoiceRecord>(this.invoiceApi, invoiceData)
      : this.http.put<InvoiceRecord>(`${this.invoiceApi}/${this.editingId}`, invoiceData);

    request.subscribe({
      next: () => {
        this.saving = false;
        this.closeForm();
        this.successMessage = wasEdit
          ? 'Invoice updated successfully.'
          : 'Invoice created successfully.';
        this.refreshData(true);
        this.changeDetector.markForCheck();
      },
      error: (error) => {
        this.saving = false;
        this.errorMessage = apiErrorMessage(error, 'Unable to save the invoice.');
        this.changeDetector.markForCheck();
      },
    });
  }

  deleteInvoice(invoice: InvoiceRecord): void {
    const id = Number(invoice.invoiceId);
    if (!Number.isSafeInteger(id) || id <= 0 || this.deletingId !== null) {
      this.errorMessage = 'Invalid invoice ID.';
      return;
    }
    if (!confirm(`Are you sure you want to delete invoice #${id}?`)) return;

    this.errorMessage = '';
    this.successMessage = '';
    this.deletingId = id;
    this.http.delete(`${this.invoiceApi}/${id}`).subscribe({
      next: () => {
        this.deletingId = null;
        this.successMessage = 'Invoice deleted successfully.';
        this.refreshData(true);
        this.changeDetector.markForCheck();
      },
      error: (error) => {
        this.deletingId = null;
        this.errorMessage = apiErrorMessage(error, 'Invoice deletion failed.');
        this.changeDetector.markForCheck();
      },
    });
  }

  resetForm(): void {
    this.form = { orderId: 0, invoiceDate: '', amount: 0 };
  }

  onOrderChanged(orderId: number): void {
    const order = this.orders.find((item) => Number(item.orderId) === Number(orderId));
    this.form = {
      ...this.form,
      orderId: Number(orderId) || 0,
      amount: this.toNumber(order?.totalAmount),
    };
  }

  trackByInvoiceId(index: number, invoice: InvoiceRecord): number | string {
    return invoice.invoiceId ?? index;
  }

  private loadList<T>(url: string, name: string): Observable<ListResult<T>> {
    return this.http.get<unknown>(url).pipe(
      map((response): ListResult<T> => Array.isArray(response)
        ? { records: response as T[] }
        : { records: null, error: `${name} returned an invalid records response.` }),
      catchError((error): Observable<ListResult<T>> => of({
        records: null,
        error: apiErrorMessage(error, `${name} could not be loaded.`),
      })),
    );
  }

  private bindLoader<T>(
    requests: Subject<void>,
    url: string,
    name: string,
    apply: (result: ListResult<T>) => void,
  ): void {
    requests
      .pipe(
        switchMap(() => this.loadList<T>(url, name)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        apply(result);
        this.changeDetector.markForCheck();
      });
  }

  private updateRefreshing(): void {
    this.isRefreshing = this.loading || this.paymentsLoading || this.ordersLoading || this.customersLoading;
  }

  private rebuildPaymentTotals(): void {
    this.paidTotalsCents.clear();
    for (const payment of this.payments) {
      const invoiceId = Number(payment.invoiceId);
      const amount = Number(payment.amount);
      if (!Number.isSafeInteger(invoiceId) || invoiceId <= 0 || !Number.isFinite(amount) || amount <= 0) continue;
      this.paidTotalsCents.set(
        invoiceId,
        (this.paidTotalsCents.get(invoiceId) ?? 0) + Math.round(amount * 100),
      );
    }
  }

  private normalizePaymentStatus(status: unknown): string {
    const normalized = String(status ?? '').trim().toUpperCase().replace(/[_-]+/g, ' ');
    if (normalized === 'PAID') return 'PAID';
    if (normalized.includes('PARTIAL')) return 'PARTIAL';
    return 'UNPAID';
  }

  private toNumber(value: unknown): number {
    const result = Number(value);
    return Number.isFinite(result) ? result : 0;
  }
}
