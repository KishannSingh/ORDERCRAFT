import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SalesOrders } from './sales-orders';
import { API_BASE_URL } from '../api';

describe('SalesOrders', () => {
  let component: SalesOrders;
  let fixture: ComponentFixture<SalesOrders>;
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SalesOrders],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(SalesOrders);
    component = fixture.componentInstance;
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify({ ignoreCancelled: true });
    fixture.destroy();
  });

  function initializeEmpty(): void {
    fixture.detectChanges();
    httpTesting.expectOne(`${API_BASE_URL}/orders`).flush([]);
    httpTesting.expectOne(`${API_BASE_URL}/customers`).flush([]);
    httpTesting.expectOne(`${API_BASE_URL}/products`).flush([]);
    fixture.detectChanges();
  }

  function openItemsFor(orderId: number, items: unknown[] = []): void {
    component.manageOrderItems({ orderId } as Parameters<typeof component.manageOrderItems>[0]);
    httpTesting.expectOne(`${API_BASE_URL}/order-items/order/${orderId}`).flush(items);
    fixture.detectChanges();
  }

  it('loads orders and customer names automatically on initial page entry', () => {
    fixture.detectChanges();
    const orderRequest = httpTesting.expectOne(`${API_BASE_URL}/orders`);
    const customerRequest = httpTesting.expectOne(`${API_BASE_URL}/customers`);
    const productRequest = httpTesting.expectOne(`${API_BASE_URL}/products`);
    expect(component.loading).toBe(true);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Loading sales orders');

    orderRequest.flush([
      { orderId: 2, customerId: 3, orderDate: '2026-10-01', status: 'PENDING', totalAmount: 50 },
      { orderId: 5, customerId: 3, orderDate: '2026-10-02', status: 'DELIVERED', totalAmount: 75 },
    ]);
    customerRequest.flush([{ customerId: 3, name: 'Kishan Singh', email: 'kishan@example.test' }]);
    productRequest.flush([]);
    fixture.detectChanges();

    expect(component.loading).toBe(false);
    expect(component.orders).toHaveLength(2);
    expect(component.countByStatus('PENDING')).toBe(1);
    expect(component.countByStatus('COMPLETED')).toBe(1);
    expect(component.getCustomerName(3)).toBe('Kishan Singh');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('SO-2');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Kishan Singh');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Customer ID: 3');
  });

  it('distinguishes an empty order list from an API failure and provides retry', () => {
    fixture.detectChanges();
    const orderRequest = httpTesting.expectOne(`${API_BASE_URL}/orders`);
    httpTesting.expectOne(`${API_BASE_URL}/customers`).flush([]);
    httpTesting.expectOne(`${API_BASE_URL}/products`).flush([]);
    orderRequest.flush([]);
    expect(component.orders).toEqual([]);
    expect(component.ordersError).toBe('');
    expect(component.loading).toBe(false);

    component.loadOrders();
    const retryRequest = httpTesting.expectOne(`${API_BASE_URL}/orders`);
    expect(component.loading).toBe(true);
    retryRequest.flush({ message: 'Backend unavailable' }, { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();
    expect(component.ordersError).toContain('Backend unavailable');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Unable to load sales orders');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Retry');
  });

  it('creates, edits and deletes sales orders through the backend and reloads the list', () => {
    initializeEmpty();
    const newOrder = { orderId: 11, customerId: 4, orderDate: '2026-10-18', status: 'PENDING', totalAmount: 0 };
    component.newOrder = { customerId: 4, orderDate: '2026-10-18', status: 'PENDING', totalAmount: 0 };
    component.saveOrder();
    const createRequest = httpTesting.expectOne(`${API_BASE_URL}/orders`);
    expect(createRequest.request.method).toBe('POST');
    expect(createRequest.request.body).toEqual({ customerId: 4, orderDate: '2026-10-18', status: 'PENDING' });
    createRequest.flush(newOrder);
    httpTesting.expectOne(`${API_BASE_URL}/orders`).flush([newOrder]);
    expect(component.orders).toEqual([newOrder]);

    component.editOrder(newOrder);
    component.newOrder.orderDate = '2026-10-17';
    component.saveOrder();
    const updateRequest = httpTesting.expectOne(`${API_BASE_URL}/orders/11`);
    expect(updateRequest.request.method).toBe('PUT');
    updateRequest.flush({ ...newOrder, orderDate: '2026-10-17' });
    httpTesting.expectOne(`${API_BASE_URL}/orders`).flush([{ ...newOrder, orderDate: '2026-10-17' }]);

    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    component.deleteOrder(11);
    const deleteRequest = httpTesting.expectOne(`${API_BASE_URL}/orders/11`);
    expect(deleteRequest.request.method).toBe('DELETE');
    deleteRequest.flush(null);
    httpTesting.expectOne(`${API_BASE_URL}/orders`).flush([]);
    expect(component.orders).toEqual([]);
  });

  it('cancels an older list request when a newer refresh starts', () => {
    fixture.detectChanges();
    const firstLoad = httpTesting.expectOne(`${API_BASE_URL}/orders`);
    httpTesting.expectOne(`${API_BASE_URL}/customers`).flush([]);
    httpTesting.expectOne(`${API_BASE_URL}/products`).flush([]);
    component.loadOrders();
    expect(firstLoad.cancelled).toBe(true);
    httpTesting.expectOne(`${API_BASE_URL}/orders`).flush([
      { orderId: 5, customerId: 9, orderDate: '2026-10-18', status: 'CONFIRMED', totalAmount: 20 },
    ]);
    expect(component.orders.map((order) => order.orderId)).toEqual([5]);
  });

  it('cancels stale order-item loads when changing orders and when closing the panel', () => {
    initializeEmpty();

    component.selectedOrderId = 2;
    component.loadOrderItems(2);
    const oldRequest = httpTesting.expectOne(`${API_BASE_URL}/order-items/order/2`);

    component.selectedOrderId = 5;
    component.loadOrderItems(5);
    expect(oldRequest.cancelled).toBe(true);
    const currentRequest = httpTesting.expectOne(`${API_BASE_URL}/order-items/order/5`);
    currentRequest.flush([
      { itemId: 50, orderId: 5, productId: 9, quantity: 2, unitPrice: 10 },
    ]);
    expect(component.orderItems.map((item) => item.itemId)).toEqual([50]);

    component.loadOrderItems(5);
    const closingRequest = httpTesting.expectOne(`${API_BASE_URL}/order-items/order/5`);
    component.closeOrderItems();
    expect(closingRequest.cancelled).toBe(true);
    expect(component.orderItems).toEqual([]);
    expect(component.orderItemsLoading).toBe(false);
  });

  it('renders the order-items panel close control as a non-submitting icon button', () => {
    initializeEmpty();
    openItemsFor(11);

    const closeButton = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.items-panel-close');
    expect(closeButton?.type).toBe('button');
    expect(closeButton?.getAttribute('aria-label')).toBe('Close sales order items panel');
    expect(closeButton?.textContent?.trim()).toBe('×');
    closeButton?.click();
    fixture.detectChanges();

    expect(component.selectedOrderId).toBeNull();
    expect(component.showItemForm).toBe(false);
    httpTesting.expectNone((request) => request.url.includes('/order-items')
      && ['POST', 'PUT', 'DELETE'].includes(request.method));
  });

  it('closes and resets the Add Item form from both controls without submitting', () => {
    initializeEmpty();
    component.products = [
      { productId: 9, productCode: 'P-9', name: 'Test product', price: 25 } as (typeof component.products)[number],
    ];
    openItemsFor(11);
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.items-heading-actions .primary-btn')?.click();
    component.itemForm = { productId: 9, quantity: 3, unitPrice: 75 };
    fixture.detectChanges();

    const closeButton = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.item-form-close');
    expect(closeButton?.type).toBe('button');
    expect(closeButton?.getAttribute('aria-label')).toBe('Close Add Item form');
    expect((fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.item-form-actions .save-btn')?.type).toBe('submit');
    closeButton?.click();
    fixture.detectChanges();

    expect(component.showItemForm).toBe(false);
    expect(component.itemForm).toEqual({ productId: 0, quantity: 1, unitPrice: 0 });

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.items-heading-actions .primary-btn')?.click();
    fixture.detectChanges();
    expect(component.itemForm).toEqual({ productId: 0, quantity: 1, unitPrice: 0 });
    component.itemForm = { productId: 9, quantity: 4, unitPrice: 100 };
    fixture.detectChanges();

    const cancelButton = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.item-form-actions .cancel-btn');
    expect(cancelButton?.type).toBe('button');
    cancelButton?.click();
    fixture.detectChanges();

    expect(component.showItemForm).toBe(false);
    expect(component.editingItemId).toBeNull();
    expect(component.itemForm).toEqual({ productId: 0, quantity: 1, unitPrice: 0 });
    httpTesting.expectNone((request) => request.url.includes('/order-items'));
  });

  it('keeps edit-item data when reopening the form in edit mode', () => {
    initializeEmpty();
    component.products = [
      { productId: 9, productCode: 'P-9', name: 'Test product', price: 25 } as (typeof component.products)[number],
    ];
    const item = { itemId: 42, orderId: 11, productId: 9, quantity: 2, unitPrice: 30 };
    openItemsFor(11, [item]);
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.item-action-buttons .edit-btn')?.click();
    fixture.detectChanges();
    expect(component.editingItemId).toBe(42);
    expect(component.itemForm).toEqual({ productId: 9, quantity: 2, unitPrice: 30 });
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Update Item');

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.item-form-close')?.click();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.item-action-buttons .edit-btn')?.click();
    fixture.detectChanges();

    expect(component.showItemForm).toBe(true);
    expect(component.editingItemId).toBe(42);
    expect(component.itemForm).toEqual({ productId: 9, quantity: 2, unitPrice: 30 });
    httpTesting.expectNone((request) => request.url.includes('/order-items'));
  });
});
