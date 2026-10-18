import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { Invoices } from './invoices';
import { API_BASE_URL } from '../api';

describe('Invoices', () => {
  let component: Invoices;
  let fixture: ComponentFixture<Invoices>;
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Invoices],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(Invoices);
    component = fixture.componentInstance;
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify({ ignoreCancelled: true });
    fixture.destroy();
  });

  function startPage(): {
    invoices: TestRequest;
    payments: TestRequest;
    orders: TestRequest;
    customers: TestRequest;
  } {
    fixture.detectChanges();
    return {
      invoices: httpTesting.expectOne(`${API_BASE_URL}/invoices`),
      payments: httpTesting.expectOne(`${API_BASE_URL}/payments`),
      orders: httpTesting.expectOne(`${API_BASE_URL}/orders`),
      customers: httpTesting.expectOne(`${API_BASE_URL}/customers`),
    };
  }

  function flushRelated(requests: ReturnType<typeof startPage>): void {
    requests.orders.flush([{ orderId: 2, customerId: 3, orderDate: '2026-10-01', status: 'CONFIRMED', totalAmount: 100 }]);
    requests.customers.flush([{ customerId: 3, name: 'Kishan Singh', email: 'kishan@example.test' }]);
  }

  function flushRefresh(invoices: unknown[], payments: unknown[]): void {
    httpTesting.expectOne(`${API_BASE_URL}/invoices`).flush(invoices);
    httpTesting.expectOne(`${API_BASE_URL}/payments`).flush(payments);
    httpTesting.expectOne(`${API_BASE_URL}/orders`).flush([
      { orderId: 2, customerId: 3, orderDate: '2026-10-01', status: 'CONFIRMED', totalAmount: 100 },
    ]);
    httpTesting.expectOne(`${API_BASE_URL}/customers`).flush([
      { customerId: 3, name: 'Kishan Singh', email: 'kishan@example.test' },
    ]);
  }

  it('loads invoices and related records on entry and shows accurate headings and customer names', () => {
    const requests = startPage();
    expect(component.loading).toBe(true);
    expect(component.isRefreshing).toBe(true);
    requests.invoices.flush([
      { invoiceId: 7, orderId: 2, invoiceDate: '2026-10-01', amount: 100, status: 'UNPAID' },
    ]);
    expect(component.loading).toBe(false);
    expect(component.paymentsLoading).toBe(true);
    requests.payments.flush([{ paymentId: 1, invoiceId: 7, paymentDate: '2026-10-02', amount: 30, mode: 'UPI' }]);
    flushRelated(requests);
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(component.invoices).toHaveLength(1);
    expect(component.totalAmountReceived).toBe(30);
    expect(component.totalBalanceOutstanding).toBe(70);
    expect(component.getOrderLabel(2)).toBe('SO-2');
    expect(component.getOrderCustomerName(2)).toBe('Kishan Singh');
    expect(page.textContent).toContain('Invoice Amount');
    expect(page.textContent).toContain('Amount Paid');
    expect(page.textContent).toContain('Balance Due');
    expect(page.textContent).toContain('Payment Progress');
    expect(page.textContent).toContain('Kishan Singh');
    expect(page.textContent).not.toContain('Amount (Remaining)');
    expect(page.textContent).toContain('30%');
    expect(component.getPaymentStatus(component.invoices[0])).toBe('PARTIAL');

    component.openForm();
    expect(httpTesting.match((request) => request.method === 'GET')).toHaveLength(0);
  });

  it('refreshes without duplicate requests, cancels stale responses, and finishes loading', () => {
    const old = startPage();
    component.refreshData();
    expect(httpTesting.match((request) => request.method === 'GET')).toHaveLength(0);

    component.refreshData(true);
    expect(old.invoices.cancelled).toBe(true);
    expect(old.payments.cancelled).toBe(true);
    expect(old.orders.cancelled).toBe(true);
    expect(old.customers.cancelled).toBe(true);
    flushRefresh(
      [{ invoiceId: 9, orderId: 2, invoiceDate: '2026-10-04', amount: 200, status: 'UNPAID' }],
      [],
    );
    expect(component.invoices.map((invoice) => invoice.invoiceId)).toEqual([9]);
    expect(component.isRefreshing).toBe(false);
    expect(component.loading).toBe(false);
  });

  it('keeps invoice data when the payment endpoint fails and avoids claiming zero paid', () => {
    const requests = startPage();
    requests.invoices.flush([
      { invoiceId: 7, orderId: 2, invoiceDate: '2026-10-01', amount: 100, status: 'partially_paid' },
    ]);
    requests.payments.flush({ message: 'payment service unavailable' }, { status: 503, statusText: 'Service Unavailable' });
    flushRelated(requests);
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(component.invoices).toHaveLength(1);
    expect(component.paymentsError).toContain('payment service unavailable');
    expect(component.getPaymentStatus(component.invoices[0])).toBe('PARTIAL');
    expect(page.textContent).toContain('Payment totals are unavailable');
    expect(page.textContent).toContain('last stored invoice status');
    expect(page.textContent).toContain('—');
  });

  it('calculates balances in cents, caps progress, and handles a zero amount safely', () => {
    const requests = startPage();
    requests.invoices.flush([
      { invoiceId: 1, orderId: 2, invoiceDate: '2026-10-01', amount: 10.1, status: 'UNPAID' },
      { invoiceId: 2, orderId: 2, invoiceDate: '2026-10-01', amount: 0, status: 'UNPAID' },
    ]);
    requests.payments.flush([
      { paymentId: 1, invoiceId: 1, amount: 2.1 },
      { paymentId: 2, invoiceId: 1, amount: 8 },
      { paymentId: 3, invoiceId: 1, amount: -50 },
    ]);
    flushRelated(requests);

    expect(component.getPaidAmount(component.invoices[0])).toBe(10.1);
    expect(component.getBalanceDue(component.invoices[0])).toBe(0);
    expect(component.getPaymentProgress(component.invoices[0])).toBe(100);
    expect(component.getPaymentStatus(component.invoices[0])).toBe('PAID');
    expect(component.getPaymentProgress(component.invoices[1])).toBe(0);
    expect(component.getPaymentStatus(component.invoices[1])).toBe('UNPAID');
  });

  it('creates, edits, and deletes invoices using real API requests and refreshed records', () => {
    const initial = startPage();
    initial.invoices.flush([]);
    initial.payments.flush([]);
    flushRelated(initial);

    component.openForm();
    component.onOrderChanged(2);
    component.form.invoiceDate = '2026-10-18';
    component.saveInvoice();
    const create = httpTesting.expectOne(`${API_BASE_URL}/invoices`);
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toMatchObject({ orderId: 2, invoiceDate: '2026-10-18', amount: 100 });
    const invoice = { invoiceId: 12, orderId: 2, invoiceDate: '2026-10-18', amount: 100, status: 'UNPAID' };
    create.flush(invoice);
    flushRefresh([invoice], []);
    expect(component.invoices).toEqual([invoice]);

    component.openForm();
    component.onOrderChanged(2);
    component.form.invoiceDate = '2026-10-18';
    component.saveInvoice();
    expect(component.errorMessage).toContain('already has an invoice');
    expect(httpTesting.match((request) => request.method === 'POST')).toHaveLength(0);

    component.editInvoice(invoice);
    component.form.invoiceDate = '2026-10-19';
    component.saveInvoice();
    const update = httpTesting.expectOne(`${API_BASE_URL}/invoices/12`);
    expect(update.request.method).toBe('PUT');
    update.flush({ ...invoice, invoiceDate: '2026-10-19' });
    flushRefresh([{ ...invoice, invoiceDate: '2026-10-19' }], []);
    expect(component.invoices[0].invoiceDate).toBe('2026-10-19');

    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    component.deleteInvoice(component.invoices[0]);
    const remove = httpTesting.expectOne(`${API_BASE_URL}/invoices/12`);
    expect(remove.request.method).toBe('DELETE');
    remove.flush(null);
    flushRefresh([], []);
    expect(component.invoices).toEqual([]);
  });

  it('does not leave the list loading after invoice API failure and offers retry', () => {
    const requests = startPage();
    requests.invoices.flush({ message: 'service unavailable' }, { status: 503, statusText: 'Service Unavailable' });
    requests.payments.flush([]);
    flushRelated(requests);
    fixture.detectChanges();

    expect(component.loading).toBe(false);
    expect(component.invoicesError).toContain('service unavailable');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Retry');
  });
});
