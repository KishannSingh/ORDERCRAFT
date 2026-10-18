import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { catchError, map, Observable, of, Subject, switchMap } from 'rxjs';
import { API_BASE_URL, apiErrorMessage } from '../api';

interface SalesOrder {
  orderId: number;
  customerId: number;
  orderDate: string;
  status: string;
  totalAmount: number;
}

interface Customer {
  customerId?: number;
  id?: number;
  name?: string;
  customerName?: string;
  email?: string;
  phone?: string;
}

interface Product {
  productId: number;
  productCode: string;
  name: string;
  price: number;
}

interface SalesOrderItem {
  itemId: number;
  orderId: number;
  productId: number;
  quantity: number;
  unitPrice: number;
}

interface OrdersLoadResult {
  orders?: SalesOrder[];
  error?: string;
}

interface RelatedRecordsLoadResult<T> {
  records?: T[];
  error?: string;
}

@Component({
  selector: 'app-sales-orders',
  standalone: true,
  imports: [FormsModule, DecimalPipe],
  templateUrl: './sales-orders.html',
  styleUrl: './sales-orders.css',
})
export class SalesOrders implements OnInit {
  private readonly API = API_BASE_URL;
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly orderLoadRequests = new Subject<void>();
  private readonly customerLoadRequests = new Subject<void>();
  private readonly productLoadRequests = new Subject<void>();
  private readonly orderItemLoadRequests = new Subject<number | null>();

  orders: SalesOrder[] = [];
  customers: Customer[] = [];
  products: Product[] = [];
  orderItems: SalesOrderItem[] = [];

  showForm = false;
  editingOrderId: number | null = null;
  loading = false;
  customersLoading = false;
  productsLoading = false;
  saving = false;
  deletingOrderId: number | null = null;
  errorMessage = '';
  ordersError = '';
  successMessage = '';
  customersError = '';
  productsError = '';

  selectedOrderId: number | null = null;
  orderItemsLoading = false;
  itemSaving = false;
  itemDeletingId: number | null = null;
  showItemForm = false;
  editingItemId: number | null = null;
  itemErrorMessage = '';

  newOrder = {
    customerId: 0,
    orderDate: '',
    status: 'PENDING',
    totalAmount: 0,
  };

  itemForm = {
    productId: 0,
    quantity: 1,
    unitPrice: 0,
  };

  constructor(private readonly http: HttpClient) {
    this.orderLoadRequests
      .pipe(
        switchMap(() => {
          this.loading = true;
          this.ordersError = '';
          return this.http.get<unknown>(`${this.API}/orders`).pipe(
            map((data): OrdersLoadResult => Array.isArray(data)
              ? { orders: data as SalesOrder[] }
              : { error: 'The sales orders API returned an invalid records response.' }),
            catchError((error: HttpErrorResponse): Observable<OrdersLoadResult> => of({
              error: apiErrorMessage(error, 'Unable to load sales orders.'),
            })),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.loading = false;
        if (result.error) {
          this.orders = [];
          this.ordersError = result.error;
          this.changeDetector.markForCheck();
          return;
        }
        this.orders = result.orders ?? [];
        this.ordersError = '';
        this.changeDetector.markForCheck();
      });

    this.customerLoadRequests
      .pipe(
        switchMap(() => {
          this.customersLoading = true;
          this.customersError = '';
          return this.http.get<unknown>(`${this.API}/customers`).pipe(
            map((data): RelatedRecordsLoadResult<Customer> => Array.isArray(data)
              ? { records: data as Customer[] }
              : { error: 'The customers API returned an invalid records response.' }),
            catchError((error: HttpErrorResponse): Observable<RelatedRecordsLoadResult<Customer>> => of({
              error: apiErrorMessage(error, 'Unable to load customers.'),
            })),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.customersLoading = false;
        if (result.error) {
          this.customers = [];
          this.customersError = result.error;
          this.changeDetector.markForCheck();
          return;
        }
        this.customers = result.records ?? [];
        this.customersError = '';
        this.changeDetector.markForCheck();
      });

    this.productLoadRequests
      .pipe(
        switchMap(() => {
          this.productsLoading = true;
          this.productsError = '';
          return this.http.get<unknown>(`${this.API}/products`).pipe(
            map((data): RelatedRecordsLoadResult<Product> => Array.isArray(data)
              ? { records: data as Product[] }
              : { error: 'The products API returned an invalid records response.' }),
            catchError((error: HttpErrorResponse): Observable<RelatedRecordsLoadResult<Product>> => of({
              error: apiErrorMessage(error, 'Unable to load products.'),
            })),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.productsLoading = false;
        if (result.error) {
          this.products = [];
          this.productsError = result.error;
          this.changeDetector.markForCheck();
          return;
        }
        this.products = result.records ?? [];
        this.productsError = '';
        this.changeDetector.markForCheck();
      });

    this.orderItemLoadRequests
      .pipe(
        switchMap((orderId) => {
          if (orderId === null) return of(null);
          this.orderItemsLoading = true;
          this.itemErrorMessage = '';
          return this.http.get<unknown>(`${this.API}/order-items/order/${orderId}`).pipe(
            map((data): { orderId: number; result: RelatedRecordsLoadResult<SalesOrderItem> } => ({
              orderId,
              result: Array.isArray(data)
                ? { records: data as SalesOrderItem[] }
                : { error: 'The sales order items API returned an invalid records response.' },
            })),
            catchError((error: HttpErrorResponse): Observable<{
              orderId: number;
              result: RelatedRecordsLoadResult<SalesOrderItem>;
            }> => of({
              orderId,
              result: { error: apiErrorMessage(error, 'Unable to load sales order items.') },
            })),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((loaded) => {
        this.orderItemsLoading = false;
        if (loaded === null) {
          this.orderItems = [];
          this.changeDetector.markForCheck();
          return;
        }
        this.orderItems = loaded.result.records ?? [];
        this.itemErrorMessage = loaded.result.error ?? '';
        this.changeDetector.markForCheck();
      });
  }

  ngOnInit(): void {
    this.loadOrders();
    this.loadCustomers();
    this.loadProducts();
  }

  loadOrders(): void {
    this.orderLoadRequests.next();
  }

  loadCustomers(): void {
    this.customerLoadRequests.next();
  }

  loadProducts(): void {
    this.productLoadRequests.next();
  }

  getCustomerName(customerId: number): string {
    const customer = this.customers.find(
      (item) => Number(item.customerId ?? item.id) === Number(customerId),
    );
    return customer?.name || customer?.customerName || `Customer #${customerId}`;
  }

  getCustomerInitials(customerId: number): string {
    const name = this.getCustomerName(customerId);
    if (name.startsWith('Customer #')) return 'C';
    return name.trim().split(/\s+/).slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase()).join('');
  }

  getCustomerDetail(customerId: number): string {
    const customer = this.customers.find(
      (item) => Number(item.customerId ?? item.id) === Number(customerId),
    );
    return customer?.email || customer?.phone || (this.customersLoading
      ? 'Loading customer details…'
      : 'Customer details unavailable');
  }

  getProductName(productId: number): string {
    const product = this.products.find((item) => Number(item.productId) === Number(productId));
    return product ? `${product.productCode} · ${product.name}` : `Product #${productId}`;
  }

  countByStatus(status: string): number {
    const normalizedStatus = status.toUpperCase();
    return this.orders.filter((order) => {
      const orderStatus = String(order.status ?? '').trim().toUpperCase();
      if (normalizedStatus === 'PENDING') return orderStatus === 'PENDING' || orderStatus === 'DRAFT';
      if (normalizedStatus === 'COMPLETED') return orderStatus === 'COMPLETED' || orderStatus === 'COMPLETE' || orderStatus === 'DELIVERED';
      return orderStatus === normalizedStatus;
    }).length;
  }

  openCreateForm(): void {
    this.resetForm();
    this.errorMessage = '';
    this.showForm = true;
  }

  saveOrder(): void {
    if (this.saving) return;
    const customerId = Number(this.newOrder.customerId);
    const orderDate = this.newOrder.orderDate;
    const status = this.newOrder.status;
    this.errorMessage = '';
    this.successMessage = '';

    if (!Number.isInteger(customerId) || customerId <= 0 || !orderDate || !status) {
      this.errorMessage = 'Select a customer, order date and status.';
      return;
    }

    const orderData = { customerId, orderDate, status };
    this.saving = true;
    const request = this.editingOrderId === null
      ? this.http.post<SalesOrder>(`${this.API}/orders`, orderData)
      : this.http.put<SalesOrder>(`${this.API}/orders/${this.editingOrderId}`, orderData);
    const wasEdit = this.editingOrderId !== null;

    request.subscribe({
      next: () => {
        this.saving = false;
        this.successMessage = wasEdit ? 'Sales order updated.' : 'Sales order created.';
        this.resetForm();
        this.loadOrders();
        this.changeDetector.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.saving = false;
        this.errorMessage = apiErrorMessage(error, 'Unable to save the sales order.');
        this.changeDetector.markForCheck();
      },
    });
  }

  editOrder(order: SalesOrder): void {
    this.errorMessage = '';
    this.editingOrderId = order.orderId;
    this.newOrder = {
      customerId: Number(order.customerId),
      orderDate: order.orderDate,
      status: order.status || 'PENDING',
      totalAmount: Number(order.totalAmount),
    };
    this.showForm = true;
  }

  deleteOrder(id: number): void {
    if (!Number.isInteger(id) || id <= 0 || this.deletingOrderId !== null) return;
    if (!confirm(`Are you sure you want to delete order #${id}?`)) return;

    this.errorMessage = '';
    this.successMessage = '';
    this.deletingOrderId = id;
    this.http.delete(`${this.API}/orders/${id}`).subscribe({
      next: () => {
        this.deletingOrderId = null;
        this.successMessage = 'Sales order deleted.';
        if (this.selectedOrderId === id) this.closeOrderItems();
        this.loadOrders();
        this.changeDetector.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.deletingOrderId = null;
        this.errorMessage = apiErrorMessage(error, 'Unable to delete the sales order.');
        this.changeDetector.markForCheck();
      },
    });
  }

  manageOrderItems(order: SalesOrder): void {
    this.itemErrorMessage = '';
    this.selectedOrderId = order.orderId;
    this.showItemForm = false;
    this.editingItemId = null;
    this.resetItemForm();
    this.loadOrderItems(order.orderId);
  }

  closeOrderItems(): void {
    this.orderItemLoadRequests.next(null);
    this.selectedOrderId = null;
    this.showItemForm = false;
    this.editingItemId = null;
    this.orderItems = [];
    this.itemErrorMessage = '';
    this.resetItemForm();
  }

  openNewItemForm(): void {
    this.editingItemId = null;
    this.itemErrorMessage = '';
    this.resetItemForm();
    this.showItemForm = true;
  }

  loadOrderItems(orderId: number): void {
    if (!Number.isSafeInteger(orderId) || orderId <= 0) {
      this.itemErrorMessage = 'Select a valid sales order.';
      this.changeDetector.markForCheck();
      return;
    }
    this.orderItemLoadRequests.next(orderId);
  }

  onProductSelected(productId: number): void {
    const product = this.products.find((item) => Number(item.productId) === Number(productId));
    if (product && this.editingItemId === null) this.itemForm.unitPrice = Number(product.price);
  }

  saveOrderItem(): void {
    if (this.itemSaving || this.selectedOrderId === null) return;
    const orderId = this.selectedOrderId;
    this.itemErrorMessage = '';
    const productId = Number(this.itemForm.productId);
    const quantity = Number(this.itemForm.quantity);
    const unitPrice = Number(this.itemForm.unitPrice);
    if (!Number.isInteger(productId) || productId <= 0
        || !this.products.some((product) => Number(product.productId) === productId)) {
      this.itemErrorMessage = 'Select an existing product.';
      return;
    }
    if (!Number.isInteger(quantity) || quantity <= 0) {
      this.itemErrorMessage = 'Quantity must be a positive whole number.';
      return;
    }
    if (!Number.isFinite(unitPrice) || unitPrice <= 0) {
      this.itemErrorMessage = 'Unit price must be a finite amount greater than zero.';
      return;
    }

    const payload = { orderId, productId, quantity, unitPrice };
    const request = this.editingItemId === null
      ? this.http.post<SalesOrderItem>(`${this.API}/order-items`, payload)
      : this.http.put<SalesOrderItem>(`${this.API}/order-items/${this.editingItemId}`, payload);
    this.itemSaving = true;
    request.subscribe({
      next: () => {
        this.itemSaving = false;
        this.successMessage = this.editingItemId === null ? 'Order item added.' : 'Order item updated.';
        this.showItemForm = false;
        this.editingItemId = null;
        this.resetItemForm();
        if (this.selectedOrderId === orderId) this.loadOrderItems(orderId);
        this.loadOrders();
        this.changeDetector.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.itemSaving = false;
        this.itemErrorMessage = apiErrorMessage(error, 'Unable to save the order item.');
        this.changeDetector.markForCheck();
      },
    });
  }

  editOrderItem(item: SalesOrderItem): void {
    this.editingItemId = item.itemId;
    this.itemForm = {
      productId: Number(item.productId),
      quantity: Number(item.quantity),
      unitPrice: Number(item.unitPrice),
    };
    this.itemErrorMessage = '';
    this.showItemForm = true;
  }

  deleteOrderItem(itemId: number): void {
    if (!Number.isInteger(itemId) || itemId <= 0 || this.itemDeletingId !== null) return;
    if (!confirm(`Delete order item #${itemId}?`)) return;
    this.itemErrorMessage = '';
    this.itemDeletingId = itemId;
    this.http.delete(`${this.API}/order-items/${itemId}`).subscribe({
      next: () => {
        this.itemDeletingId = null;
        this.successMessage = 'Order item deleted.';
        if (this.selectedOrderId !== null) this.loadOrderItems(this.selectedOrderId);
        this.loadOrders();
        this.changeDetector.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.itemDeletingId = null;
        this.itemErrorMessage = apiErrorMessage(error, 'Unable to delete the order item.');
        this.changeDetector.markForCheck();
      },
    });
  }

  resetForm(): void {
    this.newOrder = { customerId: 0, orderDate: '', status: 'PENDING', totalAmount: 0 };
    this.editingOrderId = null;
    this.showForm = false;
  }

  cancelEdit(): void {
    this.resetForm();
  }

  resetItemForm(): void {
    this.itemForm = { productId: 0, quantity: 1, unitPrice: 0 };
  }
}
