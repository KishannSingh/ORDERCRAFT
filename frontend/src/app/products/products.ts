import { ChangeDetectorRef, Component, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { API_BASE_URL, apiErrorMessage } from '../api';

@Component({
  selector: 'app-products',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './products.html',
  styleUrl: './products.css',
})
export class Products implements OnInit {
  private readonly API_URL = `${API_BASE_URL}/products`;

  products = signal<any[]>([]);

  showForm = false;

  editingProductId: number | null = null;

  isLoading = signal(false);
  isSaving = false;
  errorMessage = '';
  successMessage = '';
  deletingId: number | null = null;

  newProduct = {
    productCode: '',
    name: '',
    price: 0,
  };

  constructor(private http: HttpClient, private readonly cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.loadProducts();
  }

  // =====================================================
  // LOAD PRODUCTS
  // =====================================================

  loadProducts(): void {
    this.isLoading.set(true);
    this.errorMessage = '';

    this.http.get<any[]>(this.API_URL).subscribe({
      next: (data) => {
        this.products.set(Array.isArray(data) ? data : []);

        this.isLoading.set(false);
        this.cdr.markForCheck();
      },

      error: (error) => {
        this.products.set([]);

        this.isLoading.set(false);
        this.errorMessage = apiErrorMessage(error, 'Unable to load products.');
        this.cdr.markForCheck();
      },
    });
  }

  // =====================================================
  // SAVE / UPDATE
  // =====================================================

  saveProduct(): void {
    if (this.isSaving) return;
    this.errorMessage = '';
    this.successMessage = '';
    const productCode = this.newProduct.productCode.trim();

    const name = this.newProduct.name.trim();

    const price = Number(this.newProduct.price);

    if (!productCode) {
      this.errorMessage = 'Product code is required.';
      return;
    }

    if (!name) {
      this.errorMessage = 'Product name is required.';
      return;
    }

    if (!Number.isFinite(price) || price < 0) {
      this.errorMessage = 'Product price must be a finite non-negative amount.';

      return;
    }

    const productData = {
      productCode,
      name,
      price,
    };

    this.isSaving = true;

    // UPDATE
    if (this.editingProductId !== null) {
      const id = this.editingProductId;

      this.http.put<any>(`${this.API_URL}/${id}`, productData).subscribe({
        next: () => {
          this.isSaving = false;

          this.successMessage = 'Product updated.';

          this.resetForm();

          this.loadProducts();
          this.cdr.markForCheck();
        },

        error: (error) => {
          this.isSaving = false;

          this.errorMessage = apiErrorMessage(error, 'Unable to update the product.');
          this.cdr.markForCheck();
        },
      });

      return;
    }

    // ADD
    this.http.post<any>(this.API_URL, productData).subscribe({
      next: () => {
        this.isSaving = false;

          this.successMessage = 'Product created.';

        this.resetForm();

        this.loadProducts();
        this.cdr.markForCheck();
      },

      error: (error) => {
        this.isSaving = false;

        this.errorMessage = apiErrorMessage(error, 'Unable to create the product.');
        this.cdr.markForCheck();
      },
    });
  }

  // =====================================================
  // EDIT
  // =====================================================

  editProduct(product: any): void {
    this.editingProductId = product.productId;

    this.newProduct = {
      productCode: product.productCode ?? '',

      name: product.name ?? '',

      price: product.price ?? 0,
    };

    this.showForm = true;
  }

  // =====================================================
  // DELETE
  // =====================================================

  deleteProduct(id: number): void {
    if (!Number.isInteger(id) || id <= 0 || this.deletingId !== null) return;
    const confirmed = confirm('Are you sure you want to delete this product?');

    if (!confirmed) {
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';
    this.deletingId = id;
    this.http.delete(`${this.API_URL}/${id}`).subscribe({
      next: () => {
        this.deletingId = null;
        this.successMessage = 'Product deleted.';
        this.loadProducts();
        this.cdr.markForCheck();
      },

      error: (error) => {
        this.deletingId = null;
        this.errorMessage = apiErrorMessage(error, 'Unable to delete the product.');
        this.cdr.markForCheck();
      },
    });
  }

  // =====================================================
  // FORM
  // =====================================================

  openAddForm(): void {
    this.resetForm();

    this.showForm = true;
  }

  cancelEdit(): void {
    this.resetForm();
  }

  resetForm(): void {
    this.editingProductId = null;

    this.newProduct = {
      productCode: '',
      name: '',
      price: 0,
    };

    this.showForm = false;

    this.isSaving = false;
  }
}
