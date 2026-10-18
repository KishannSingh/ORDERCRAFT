import { Component, OnInit, ChangeDetectorRef, NgZone } from '@angular/core';

import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { API_BASE_URL, apiErrorMessage } from '../api';

interface PurchaseOrder {
  poId: number | null;
  orderId: number;
  bomId: number | null;
  materialName: string;
  quantity: number;
  status: string;
  tempId?: number;
}

interface SalesOrder {
  orderId: number;
  customerId?: number;
  customerName?: string;
}

interface SalesOrderItem {
  orderId: number;
  productId: number;
  quantity: number;
}

interface BomMaterial {
  bomId: number;
  productId: number;
  materialName: string;
  quantity: number;
  unit: string;
}

interface Product {
  productId: number;
  productCode?: string;
  name: string;
}

@Component({
  selector: 'app-purchase-orders',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './purchase-orders.html',
  styleUrl: './purchase-orders.css',
})
export class PurchaseOrders implements OnInit {
  private readonly apiUrl = `${API_BASE_URL}/purchase-orders`;

  private readonly salesOrdersUrl = `${API_BASE_URL}/orders`;
  private readonly orderItemsUrl = `${API_BASE_URL}/order-items`;
  private readonly bomsUrl = `${API_BASE_URL}/boms`;
  private readonly productsUrl = `${API_BASE_URL}/products`;

  showForm = false;
  editingId: number | null = null;

  loading = false;
  saving = false;
  errorMessage = '';
  referenceError = '';
  referencesLoading = false;
  successMessage = '';
  deletingId: number | null = null;

  purchaseOrders: PurchaseOrder[] = [];
  salesOrders: SalesOrder[] = [];
  salesOrderItems: SalesOrderItem[] = [];
  bomMaterials: BomMaterial[] = [];
  products: Product[] = [];

  newPurchaseOrder = {
    orderId: null as number | null,
    bomId: null as number | null,
    materialName: '',
    quantity: null as number | null,
    status: 'PENDING',
  };

  constructor(
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone,
  ) {}

  ngOnInit(): void {
    this.loadPurchaseOrders();
    this.loadReferences();
  }

  // ==============================
  // LOAD PURCHASE ORDERS
  // ==============================

  loadPurchaseOrders(): void {
    this.loading = true;
    this.errorMessage = '';

    this.http.get<PurchaseOrder[]>(this.apiUrl).subscribe({
      next: (data) => {
        this.ngZone.run(() => {
          this.purchaseOrders = Array.isArray(data) ? [...data] : [];

          this.loading = false;

          this.cdr.detectChanges();
        });
      },

      error: (error) => {
        this.ngZone.run(() => {
          this.loading = false;
          this.errorMessage = apiErrorMessage(error, 'Unable to load purchase orders.');

          this.cdr.detectChanges();
        });
      },
    });
  }

  // ==============================
  // LOAD SALES ORDERS
  // ==============================

  loadReferences(): void {
    this.referencesLoading = true;
    this.referenceError = '';
    forkJoin({
      orders: this.http.get<SalesOrder[]>(this.salesOrdersUrl),
      items: this.http.get<SalesOrderItem[]>(this.orderItemsUrl),
      boms: this.http.get<BomMaterial[]>(this.bomsUrl),
      products: this.http.get<Product[]>(this.productsUrl),
    }).subscribe({
      next: ({ orders, items, boms, products }) => this.ngZone.run(() => {
        this.salesOrders = Array.isArray(orders) ? orders : [];
        this.salesOrderItems = Array.isArray(items) ? items : [];
        this.bomMaterials = Array.isArray(boms) ? boms : [];
        this.products = Array.isArray(products) ? products : [];
        this.referencesLoading = false;
        this.cdr.detectChanges();
      }),
      error: (error) => this.ngZone.run(() => {
        this.referencesLoading = false;
        this.referenceError = apiErrorMessage(error, 'Sales order, product, or BOM data could not be loaded.');
        this.cdr.detectChanges();
      }),
    });
  }

  get eligibleBomMaterials(): BomMaterial[] {
    const orderId = Number(this.newPurchaseOrder.orderId);
    if (!Number.isInteger(orderId) || orderId <= 0) return [];
    const productIds = new Set(this.salesOrderItems
      .filter((item) => Number(item.orderId) === orderId)
      .map((item) => Number(item.productId)));
    return this.bomMaterials.filter((bom) => productIds.has(Number(bom.productId)));
  }

  getProductLabel(productId: number): string {
    const product = this.products.find((item) => Number(item.productId) === Number(productId));
    if (!product) return `Product #${productId}`;
    return product.productCode ? `${product.productCode} · ${product.name}` : product.name;
  }

  getBomForPurchaseOrder(po: PurchaseOrder): BomMaterial | undefined {
    return po.bomId == null ? undefined : this.bomMaterials.find((bom) => Number(bom.bomId) === Number(po.bomId));
  }

  requiredQuantity(bomId: number | null): number {
    const bom = this.bomMaterials.find((item) => Number(item.bomId) === Number(bomId));
    const orderId = Number(this.newPurchaseOrder.orderId);
    if (!bom || !Number.isInteger(orderId)) return 0;
    const orderedProductQuantity = this.salesOrderItems
      .filter((item) => Number(item.orderId) === orderId && Number(item.productId) === Number(bom.productId))
      .reduce((total, item) => total + Number(item.quantity), 0);
    return orderedProductQuantity * Number(bom.quantity);
  }

  remainingQuantity(bomId: number | null): number {
    const orderId = Number(this.newPurchaseOrder.orderId);
    if (bomId == null || !Number.isInteger(orderId)) return 0;
    const alreadyProcured = this.purchaseOrders
      .filter((po) => Number(po.orderId) === orderId
        && Number(po.bomId) === Number(bomId)
        && po.poId !== this.editingId
        && String(po.status).toUpperCase() !== 'CANCELLED')
      .reduce((total, po) => total + Number(po.quantity), 0);
    return Math.max(0, this.requiredQuantity(bomId) - alreadyProcured);
  }

  onOrderSelected(): void {
    this.newPurchaseOrder.bomId = null;
    this.newPurchaseOrder.materialName = '';
    this.newPurchaseOrder.quantity = null;
  }

  onBomSelected(): void {
    const bom = this.bomMaterials.find((item) => Number(item.bomId) === Number(this.newPurchaseOrder.bomId));
    this.newPurchaseOrder.materialName = bom?.materialName ?? '';
    const limit = this.purchaseQuantityLimit();
    this.newPurchaseOrder.quantity = limit > 0 ? limit : null;
  }

  purchaseQuantityLimit(): number {
    return String(this.newPurchaseOrder.status).toUpperCase() === 'CANCELLED'
      ? this.requiredQuantity(this.newPurchaseOrder.bomId)
      : this.remainingQuantity(this.newPurchaseOrder.bomId);
  }

  // ==============================
  // OPEN / CLOSE FORM
  // ==============================

  toggleForm(): void {
    if (this.showForm) {
      this.resetForm();
    } else {
      this.showForm = true;
    }
  }

  // ==============================
  // CREATE / UPDATE PURCHASE ORDER
  // ==============================

  savePurchaseOrder(): void {
    if (this.saving) return;
    const orderId = Number(this.newPurchaseOrder.orderId);
    const bomId = Number(this.newPurchaseOrder.bomId);

    const materialName = this.newPurchaseOrder.materialName.trim();

    const quantity = Number(this.newPurchaseOrder.quantity);

    const status = this.newPurchaseOrder.status;

    if (this.newPurchaseOrder.orderId === null || !Number.isInteger(orderId) || orderId <= 0) {
      this.errorMessage = 'Please select a valid sales order.';
      return;
    }

    if (this.newPurchaseOrder.bomId === null || !Number.isInteger(bomId)
        || !this.eligibleBomMaterials.some((bom) => Number(bom.bomId) === bomId)) {
      this.errorMessage = 'Select a BOM material used by a product on this sales order.';
      return;
    }

    if (!materialName) {
      this.errorMessage = 'Please enter the material name.';
      return;
    }

    if (!Number.isFinite(quantity) || quantity <= 0) {
      this.errorMessage = 'Quantity must be a finite value greater than zero.';
      return;
    }

    const quantityLimit = this.purchaseQuantityLimit();
    if (quantity > quantityLimit) {
      this.errorMessage = `Quantity cannot exceed the remaining requirement of ${quantityLimit}.`;
      return;
    }

    if (!['PENDING', 'ORDERED', 'RECEIVED', 'CANCELLED'].includes(status)) {
      this.errorMessage = 'Please select a valid purchase order status.';
      return;
    }

    const payload = {
      orderId,
      bomId,
      materialName,
      quantity,
      status,
    };

    this.saving = true;
    this.errorMessage = '';
    this.successMessage = '';

    // UPDATE
    if (this.editingId !== null) {
      const id = this.editingId;

      this.http.put<PurchaseOrder>(`${this.apiUrl}/${id}`, payload).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.saving = false;
            this.resetForm();
            this.loadPurchaseOrders();

            this.successMessage = 'Purchase order updated.';

            this.cdr.detectChanges();
          });
        },

        error: (error) => {
          this.ngZone.run(() => {
            this.saving = false;

            this.errorMessage = apiErrorMessage(error, 'Unable to update the purchase order.');

            this.cdr.detectChanges();
          });
        },
      });

      return;
    }

    // CREATE
    this.http.post<PurchaseOrder>(this.apiUrl, payload).subscribe({
      next: () => {
        this.ngZone.run(() => {
          this.saving = false;
          this.resetForm();
          this.loadPurchaseOrders();

          this.successMessage = 'Purchase order created.';

          this.cdr.detectChanges();
        });
      },

      error: (error) => {
        this.ngZone.run(() => {
          this.saving = false;

          this.errorMessage = apiErrorMessage(error, 'Unable to create the purchase order.');

          this.cdr.detectChanges();
        });
      },
    });
  }

  // ==============================
  // EDIT PURCHASE ORDER
  // ==============================

  editPurchaseOrder(po: PurchaseOrder): void {
    if (po.poId === null) {
      return;
    }

    this.editingId = po.poId;

    this.newPurchaseOrder = {
      orderId: po.orderId,
      bomId: po.bomId ?? null,
      materialName: po.materialName,
      quantity: po.quantity,
      status: po.status,
    };

    this.showForm = true;
  }

  // ==============================
  // DELETE PURCHASE ORDER
  // ==============================

  deletePurchaseOrder(id: number): void {
    if (!Number.isInteger(id) || id <= 0 || this.deletingId !== null) return;
    const confirmed = confirm('Are you sure you want to delete this purchase order?');

    if (!confirmed) {
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';
    this.deletingId = id;
    this.http.delete<void>(`${this.apiUrl}/${id}`).subscribe({
      next: () => {
        this.ngZone.run(() => {
          this.deletingId = null;
          this.successMessage = 'Purchase order deleted.';
          this.loadPurchaseOrders();

          this.cdr.detectChanges();
        });
      },

      error: (error) => {
        this.ngZone.run(() => {
          this.deletingId = null;
          this.errorMessage = apiErrorMessage(error, 'Unable to delete the purchase order.');
          this.cdr.detectChanges();
        });
      },
    });
  }

  // ==============================
  // CANCEL / RESET FORM
  // ==============================

  cancelEdit(): void {
    this.resetForm();
  }

  resetForm(): void {
    this.editingId = null;
    this.showForm = false;
    this.saving = false;

    this.newPurchaseOrder = {
      orderId: null,
      bomId: null,
      materialName: '',
      quantity: null,
      status: 'PENDING',
    };
  }
}
