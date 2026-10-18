import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Dashboard } from './dashboard';
import { API_BASE_URL } from '../api';

describe('Dashboard', () => {
  let component: Dashboard;
  let fixture: ComponentFixture<Dashboard>;
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(Dashboard);
    component = fixture.componentInstance;
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads dashboard counts and recent records from the aggregate API', () => {
    fixture.detectChanges();
    const request = httpTesting.expectOne(`${API_BASE_URL}/dashboard`);
    expect(request.request.method).toBe('GET');
    request.flush({
      totalCustomers: 8,
      totalProducts: 5,
      totalBoms: 4,
      totalOrders: 6,
      totalPurchaseOrders: 2,
      totalInvoices: 3,
      totalPayments: 7,
      paidInvoices: 1,
      partialInvoices: 1,
      unpaidInvoices: 1,
      pendingOrders: 2,
      confirmedOrders: 1,
      completedOrders: 2,
      cancelledOrders: 1,
      recentOrders: [{ orderId: 6, status: 'PENDING' }],
      recentPayments: [{ paymentId: 7, amount: 20 }],
    });

    expect(component.totalCustomers()).toBe(8);
    expect(component.totalBoms()).toBe(4);
    expect(component.totalPurchaseOrders()).toBe(2);
    expect(component.recentOrders()[0].orderId).toBe(6);
    expect(component.recentPayments()[0].paymentId).toBe(7);
    expect(component.isLoading).toBe(false);
  });

  it('shows an error instead of stale zero counts when the dashboard API fails', async () => {
    fixture.detectChanges();
    const request = httpTesting.expectOne(`${API_BASE_URL}/dashboard`);
    request.flush({ message: 'dashboard service unavailable' }, {
      status: 503,
      statusText: 'Service Unavailable',
    });
    await fixture.whenStable();

    const page = fixture.nativeElement as HTMLElement;
    expect(component.isLoading).toBe(false);
    expect(component.errorMessage).toContain('dashboard service unavailable');
    expect(page.querySelector('.stat-customers .stat-info strong')?.textContent?.trim()).toBe('—');
    expect(page.textContent).toContain('Recent sales order data unavailable');
    expect(page.textContent).toContain('Retry');
    expect(page.textContent).not.toContain('No recent sales orders');
  });
});
