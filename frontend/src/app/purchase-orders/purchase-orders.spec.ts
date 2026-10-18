import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PurchaseOrders } from './purchase-orders';
import { API_BASE_URL } from '../api';

describe('PurchaseOrders', () => {
  let component: PurchaseOrders;
  let fixture: ComponentFixture<PurchaseOrders>;
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PurchaseOrders],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(PurchaseOrders);
    component = fixture.componentInstance;
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify({ ignoreCancelled: true });
    fixture.destroy();
    vi.restoreAllMocks();
  });

  function initializeEmpty(): void {
    fixture.detectChanges();
    httpTesting.expectOne(`${API_BASE_URL}/purchase-orders`).flush([]);
    httpTesting.expectOne(`${API_BASE_URL}/orders`).flush([]);
    httpTesting.expectOne(`${API_BASE_URL}/order-items`).flush([]);
    httpTesting.expectOne(`${API_BASE_URL}/boms`).flush([]);
    httpTesting.expectOne(`${API_BASE_URL}/products`).flush([]);
    fixture.detectChanges();
  }

  function setValidReferences(): void {
    component.salesOrders = [{ orderId: 7, customerName: 'Test Customer' }];
    component.salesOrderItems = [{ orderId: 7, productId: 4, quantity: 2 }];
    component.bomMaterials = [{
      bomId: 9,
      productId: 4,
      materialName: 'Steel Rod',
      quantity: 3,
      unit: 'kg',
    }];
  }

  it('loads purchase orders and the references needed by the form on initial entry', () => {
    fixture.detectChanges();
    expect(component.loading).toBe(true);
    expect(component.referencesLoading).toBe(true);

    httpTesting.expectOne(`${API_BASE_URL}/purchase-orders`).flush([
      { poId: 12, orderId: 7, bomId: 9, materialName: 'Steel Rod', quantity: 2, status: 'PENDING' },
    ]);
    httpTesting.expectOne(`${API_BASE_URL}/orders`).flush([{ orderId: 7, customerName: 'Test Customer' }]);
    httpTesting.expectOne(`${API_BASE_URL}/order-items`).flush([{ orderId: 7, productId: 4, quantity: 2 }]);
    httpTesting.expectOne(`${API_BASE_URL}/boms`).flush([{
      bomId: 9, productId: 4, materialName: 'Steel Rod', quantity: 3, unit: 'kg',
    }]);
    httpTesting.expectOne(`${API_BASE_URL}/products`).flush([{ productId: 4, name: 'Widget' }]);
    fixture.detectChanges();

    expect(component.loading).toBe(false);
    expect(component.referencesLoading).toBe(false);
    expect(component.purchaseOrders.map((po) => po.poId)).toEqual([12]);
    expect(component.eligibleBomMaterials.map((bom) => bom.bomId)).toEqual([]);
    component.newPurchaseOrder.orderId = 7;
    expect(component.eligibleBomMaterials.map((bom) => bom.bomId)).toEqual([9]);
    expect(component.getProductLabel(4)).toBe('Widget');
  });

  it('creates, edits and deletes purchase orders through the matching API routes', () => {
    initializeEmpty();
    setValidReferences();
    component.newPurchaseOrder = {
      orderId: 7,
      bomId: 9,
      materialName: 'Steel Rod',
      quantity: 2,
      status: 'PENDING',
    };

    component.savePurchaseOrder();
    const createRequest = httpTesting.expectOne(`${API_BASE_URL}/purchase-orders`);
    expect(createRequest.request.method).toBe('POST');
    expect(createRequest.request.body).toEqual({
      orderId: 7,
      bomId: 9,
      materialName: 'Steel Rod',
      quantity: 2,
      status: 'PENDING',
    });
    const created = {
      poId: 12, orderId: 7, bomId: 9, materialName: 'Steel Rod', quantity: 2, status: 'PENDING',
    };
    createRequest.flush(created, { status: 201, statusText: 'Created' });
    httpTesting.expectOne(`${API_BASE_URL}/purchase-orders`).flush([created]);
    expect(component.successMessage).toBe('Purchase order created.');
    expect(component.purchaseOrders).toEqual([created]);

    component.editPurchaseOrder(created);
    component.newPurchaseOrder.quantity = 3;
    component.savePurchaseOrder();
    const updateRequest = httpTesting.expectOne(`${API_BASE_URL}/purchase-orders/12`);
    expect(updateRequest.request.method).toBe('PUT');
    expect(updateRequest.request.body.quantity).toBe(3);
    const updated = { ...created, quantity: 3 };
    updateRequest.flush(updated);
    httpTesting.expectOne(`${API_BASE_URL}/purchase-orders`).flush([updated]);
    expect(component.successMessage).toBe('Purchase order updated.');

    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    component.deletePurchaseOrder(12);
    const deleteRequest = httpTesting.expectOne(`${API_BASE_URL}/purchase-orders/12`);
    expect(deleteRequest.request.method).toBe('DELETE');
    deleteRequest.flush(null, { status: 204, statusText: 'No Content' });
    httpTesting.expectOne(`${API_BASE_URL}/purchase-orders`).flush([]);
    expect(component.successMessage).toBe('Purchase order deleted.');
    expect(component.purchaseOrders).toEqual([]);
  });
});
