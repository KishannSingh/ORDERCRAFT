
import {
  ChangeDetectorRef,
  Component,
  OnInit
} from '@angular/core';

import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { API_BASE_URL, apiErrorMessage } from '../api';

interface Product {
  productId: number;
  name: string;
  productCode?: string;
  price?: number;
}

interface BomRecord {
  bomId: number;
  productId: number;
  materialName: string;
  quantity: number;
  unit: string;
}

interface BomPayload {
  productId: number;
  materialName: string;
  quantity: number;
  unit: string;
}

interface MaterialRequirement {
  materialName: string;
  quantity: number;
  unit: string;
}

@Component({
  selector: 'app-bom',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './bom.html',
  styleUrl: './bom.css'
})
export class Bom implements OnInit {

  private readonly BOM_API = `${API_BASE_URL}/boms`;
  private readonly PRODUCT_API = `${API_BASE_URL}/products`;

  // Backend data
  boms: BomRecord[] = [];
  products: Product[] = [];

  // Form state
  showForm = false;
  editingBomId: number | null = null;

  // Loading states
  isLoading = false;
  isSaving = false;
  isCalculating = false;

  // Error messages
  errorMessage = '';
  successMessage = '';
  productError = '';
  deletingId: number | null = null;

  // Add/Edit form model
  newBom: BomPayload = {
    productId: 0,
    materialName: '',
    quantity: 0,
    unit: ''
  };

  // Calculator model
  productId = 0;
  orderQuantity = 0;
  calculatedMaterial = 0;
  calculatedUnit = '';
  calculatedRequirements: MaterialRequirement[] = [];
  calculationError = '';

  constructor(
    private readonly http: HttpClient,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadProducts();
    this.loadBoms();
  }

  // ==========================================
  // LOAD PRODUCTS
  // ==========================================

  loadProducts(): void {
    this.productError = '';

    this.http.get<Product[]>(this.PRODUCT_API).subscribe({
      next: (data) => {
        this.products = Array.isArray(data)
          ? data.map(product => ({
            ...product,
            productId: Number(product.productId)
          }))
          : [];

        this.cdr.detectChanges();
      },

      error: (error) => {
        this.products = [];
        this.productError = apiErrorMessage(error, 'Unable to load products.');

        this.cdr.detectChanges();
      }
    });
  }

  // ==========================================
  // LOAD BOM RECORDS
  // ==========================================

  loadBoms(): void {
    this.isLoading = true;
    this.errorMessage = '';

    this.http.get<BomRecord[]>(this.BOM_API).subscribe({
      next: (data) => {
        this.boms = Array.isArray(data)
          ? data.map(bom => ({
            ...bom,
            bomId: Number(bom.bomId),
            productId: Number(bom.productId),
            quantity: Number(bom.quantity)
          }))
          : [];

        this.isLoading = false;
        this.cdr.detectChanges();
      },

      error: (error) => {
        this.boms = [];
        this.isLoading = false;
        this.errorMessage = apiErrorMessage(error, 'Unable to load BOM records.');

        this.cdr.detectChanges();
      }
    });
  }

  // ==========================================
  // ADD / UPDATE BOM
  // ==========================================

  saveBom(): void {
    if (this.isSaving) {
      return;
    }
    this.errorMessage = '';
    this.successMessage = '';

    const productId = Number(this.newBom.productId);
    const materialName = String(this.newBom.materialName ?? '').trim();
    const quantity = Number(this.newBom.quantity);
    const unit = String(this.newBom.unit ?? '').trim();

    if (!Number.isInteger(productId) || productId <= 0) {
      this.errorMessage = 'Please select a valid product.';
      return;
    }

    const selectedProduct = this.products.find(
      product => Number(product.productId) === productId
    );

    if (!selectedProduct) {
      this.errorMessage = 'Selected product was not found. Reload products and try again.';
      return;
    }

    if (!materialName) {
      this.errorMessage = 'Material name is required.';
      return;
    }

    if (!Number.isFinite(quantity) || quantity <= 0) {
      this.errorMessage = 'Quantity must be a finite value greater than zero.';
      return;
    }

    if (!unit) {
      this.errorMessage = 'Please select a unit of measurement.';
      return;
    }

    const bomData: BomPayload = {
      productId,
      materialName,
      quantity,
      unit
    };

    const isEdit = this.editingBomId !== null;
    this.isSaving = true;

    const request = isEdit
      ? this.http.put<BomRecord>(
        `${this.BOM_API}/${this.editingBomId}`,
        bomData
      )
      : this.http.post<BomRecord>(
        this.BOM_API,
        bomData
      );

    request.subscribe({
      next: (response) => {
        this.isSaving = false;
        this.resetBomForm();
        this.successMessage = isEdit ? 'BOM updated.' : 'BOM created.';

        this.loadBoms();
        this.cdr.detectChanges();
      },

      error: (error) => {
        this.isSaving = false;
        this.errorMessage = apiErrorMessage(error, 'Unable to save the BOM.');

        this.cdr.detectChanges();
      }
    });
  }

  // ==========================================
  // EDIT BOM
  // ==========================================

  editBom(bom: BomRecord): void {
    this.editingBomId = Number(bom.bomId);

    this.newBom = {
      productId: Number(bom.productId ?? 0),
      materialName: bom.materialName ?? '',
      quantity: Number(bom.quantity ?? 0),
      unit: bom.unit ?? ''
    };

    this.showForm = true;

    this.cdr.detectChanges();

    window.scrollTo({
      top: 0,
      behavior: 'smooth'
    });
  }

  // ==========================================
  // DELETE BOM
  // ==========================================

  deleteBom(id: number): void {
    if (!Number.isInteger(id) || id <= 0 || this.deletingId !== null) return;
    const confirmed = confirm(
      'Are you sure you want to delete this BOM record?'
    );

    if (!confirmed) {
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';
    this.deletingId = id;
    this.http.delete<void>(`${this.BOM_API}/${id}`).subscribe({
      next: () => {
        this.deletingId = null;
        this.successMessage = 'BOM deleted.';
        this.loadBoms();
      },

      error: (error) => {
        this.deletingId = null;
        this.errorMessage = apiErrorMessage(error, 'Unable to delete the BOM record.');

        this.cdr.detectChanges();
      }
    });
  }

  // ==========================================
  // FORM CONTROLS
  // ==========================================

  openAddForm(): void {
    this.resetBomForm();
    this.showForm = true;

    this.cdr.detectChanges();

    window.setTimeout(() => {
      document.getElementById('bom-form')?.scrollIntoView({
        behavior: 'smooth',
        block: 'start'
      });
    });
  }

  cancelForm(): void {
    this.resetBomForm();
    this.cdr.detectChanges();
  }

  resetBomForm(): void {
    this.editingBomId = null;

    this.newBom = {
      productId: 0,
      materialName: '',
      quantity: 0,
      unit: ''
    };

    this.showForm = false;
    this.isSaving = false;
  }

  // ==========================================
  // PRODUCT MAPPING
  // ==========================================

  getProductName(productId: number): string {
    const product = this.products.find(
      item => Number(item.productId) === Number(productId)
    );

    return product?.name || `Unknown Product (ID: ${productId})`;
  }

  getProductCode(productId: number): string {
    const product = this.products.find(
      item => Number(item.productId) === Number(productId)
    );

    return product?.productCode || 'Unmapped';
  }

  // ==========================================
  // MATERIAL REQUIREMENT CALCULATOR
  // Calculates from the BOM records already loaded
  // ==========================================

  calculateMaterial(): void {
    if (this.isCalculating) {
      return;
    }

    const selectedProductId = Number(this.productId);
    const orderQty = Number(this.orderQuantity);

    this.calculatedMaterial = 0;
    this.calculatedUnit = '';
    this.calculatedRequirements = [];
    this.calculationError = '';

    if (!Number.isInteger(selectedProductId) || selectedProductId <= 0) {
      this.calculationError = 'Please select a product.';
      return;
    }

    if (!Number.isInteger(orderQty) || orderQty <= 0) {
      this.calculationError = 'Enter a valid whole-number order quantity.';
      return;
    }

    this.isCalculating = true;
    this.http.get<MaterialRequirement[]>(
      `${this.BOM_API}/calculate/${selectedProductId}/${orderQty}`
    ).subscribe({
      next: (requirements) => {
        this.calculatedRequirements = Array.isArray(requirements) ? requirements : [];
        this.isCalculating = false;
        if (this.calculatedRequirements.length === 0) {
          this.calculationError = 'No material requirements were returned for this product.';
        }
        this.cdr.detectChanges();
      },
      error: (error) => {
        this.isCalculating = false;
        this.calculationError = apiErrorMessage(error, 'Unable to calculate material requirements.');
        this.cdr.detectChanges();
      }
    });
  }

  // ==========================================
  // RESET CALCULATOR
  // ==========================================

  resetCalculator(): void {
    this.productId = 0;
    this.orderQuantity = 0;
    this.calculatedMaterial = 0;
    this.calculatedUnit = '';
    this.calculatedRequirements = [];
    this.calculationError = '';
    this.isCalculating = false;

    this.cdr.detectChanges();
  }
}
