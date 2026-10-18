import { ChangeDetectorRef, Component, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL, apiErrorMessage } from '../api';

interface DashboardRecord {
  customerId?: number;
  orderId?: number;
  purchaseOrderId?: number;
  invoiceId?: number;
  paymentId?: number;
  status?: string;
  totalAmount?: number;
  amount?: number;
  paymentDate?: string;
  orderDate?: string;
  customerName?: string;
  invoiceNumber?: string;
  [key: string]: any;
}

interface DashboardSummary {
  totalCustomers: number;
  totalProducts: number;
  totalBoms: number;
  totalOrders: number;
  totalPurchaseOrders: number;
  totalInvoices: number;
  totalPayments: number;
  paidInvoices: number;
  partialInvoices: number;
  unpaidInvoices: number;
  pendingOrders: number;
  confirmedOrders: number;
  completedOrders: number;
  cancelledOrders: number;
  recentOrders: DashboardRecord[];
  recentPayments: DashboardRecord[];
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit {
  private readonly dashboardApi = `${API_BASE_URL}/dashboard`;

  // Main summary counts
  totalCustomers = signal(0);
  totalProducts = signal(0);
  totalOrders = signal(0);
  totalPurchaseOrders = signal(0);
  totalBoms = signal(0);
  totalInvoices = signal(0);
  totalPayments = signal(0);

  // Recent records
  recentOrders = signal<DashboardRecord[]>([]);
  recentPayments = signal<DashboardRecord[]>([]);

  // Invoice statistics
  paidInvoiceCount = signal(0);
  partialInvoiceCount = signal(0);
  unpaidInvoiceCount = signal(0);

  // Sales order statistics
  pendingOrderCount = signal(0);
  confirmedOrderCount = signal(0);
  completedOrderCount = signal(0);
  cancelledOrderCount = signal(0);
  isLoading = false;
  errorMessage = '';

  constructor(
    private readonly http: HttpClient,
    private readonly changeDetector: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadDashboard();
  }

  loadDashboard(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.http.get<DashboardSummary>(this.dashboardApi).subscribe({
      next: (summary) => {
        if (!summary || typeof summary !== 'object' || Array.isArray(summary)) {
          this.errorMessage = 'The dashboard API returned an invalid summary.';
          this.isLoading = false;
          this.changeDetector.markForCheck();
          return;
        }
        this.totalCustomers.set(this.toCount(summary.totalCustomers));
        this.totalProducts.set(this.toCount(summary.totalProducts));
        this.totalBoms.set(this.toCount(summary.totalBoms));
        this.totalOrders.set(this.toCount(summary.totalOrders));
        this.totalPurchaseOrders.set(this.toCount(summary.totalPurchaseOrders));
        this.totalInvoices.set(this.toCount(summary.totalInvoices));
        this.totalPayments.set(this.toCount(summary.totalPayments));
        this.paidInvoiceCount.set(this.toCount(summary.paidInvoices));
        this.partialInvoiceCount.set(this.toCount(summary.partialInvoices));
        this.unpaidInvoiceCount.set(this.toCount(summary.unpaidInvoices));
        this.pendingOrderCount.set(this.toCount(summary.pendingOrders));
        this.confirmedOrderCount.set(this.toCount(summary.confirmedOrders));
        this.completedOrderCount.set(this.toCount(summary.completedOrders));
        this.cancelledOrderCount.set(this.toCount(summary.cancelledOrders));
        this.recentOrders.set(Array.isArray(summary.recentOrders) ? summary.recentOrders : []);
        this.recentPayments.set(Array.isArray(summary.recentPayments) ? summary.recentPayments : []);
        this.isLoading = false;
        this.changeDetector.markForCheck();
      },
      error: (error) => {
        this.errorMessage = apiErrorMessage(error, 'Unable to load the dashboard summary.');
        this.isLoading = false;
        this.changeDetector.markForCheck();
      },
    });
  }

  displayCount(value: number): string | number {
    if (this.isLoading) return '…';
    if (this.errorMessage) return '—';
    return value;
  }

  private toCount(value: unknown): number {
    const count = Number(value);
    return Number.isSafeInteger(count) && count >= 0 ? count : 0;
  }

  // Methods used by dashboard.html
  paidInvoices(): number {
    return this.paidInvoiceCount();
  }

  partialInvoices(): number {
    return this.partialInvoiceCount();
  }

  unpaidInvoices(): number {
    return this.unpaidInvoiceCount();
  }

  pendingOrders(): number {
    return this.pendingOrderCount();
  }

  confirmedOrders(): number {
    return this.confirmedOrderCount();
  }

  completedOrders(): number {
    return this.completedOrderCount();
  }

  cancelledOrders(): number {
    return this.cancelledOrderCount();
  }
}
