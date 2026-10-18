import { ChangeDetectorRef, Component, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { API_BASE_URL, apiErrorMessage } from '../api';

@Component({
  selector: 'app-customers',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './customers.html',
  styleUrl: './customers.css',
})
export class Customers implements OnInit {
  private readonly API_URL = `${API_BASE_URL}/customers`;

  customers = signal<any[]>([]);

  showForm = false;
  editingId: number | null = null;
  isSaving = false;
  isLoading = false;
  errorMessage = '';
  successMessage = '';
  deletingId: number | null = null;

  newCustomer = {
    name: '',
    email: '',
    phone: '',
  };

  constructor(private http: HttpClient, private readonly cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.loadCustomers();
  }

  // =====================================================
  // GET ALL CUSTOMERS
  // =====================================================

  loadCustomers(): void {
    this.isLoading = true;
    this.errorMessage = '';

    this.http.get<any[]>(this.API_URL).subscribe({
      next: (data) => {
        this.customers.set(data);
        this.isLoading = false;
        this.cdr.markForCheck();
      },

      error: (error) => {
        this.isLoading = false;
        this.errorMessage = apiErrorMessage(error, 'Unable to load customers.');
        this.cdr.markForCheck();
      },
    });
  }

  // =====================================================
  // ADD / UPDATE CUSTOMER
  // =====================================================

  saveCustomer(): void {
    if (this.isSaving) return;
    this.errorMessage = '';
    this.successMessage = '';
    const name = this.newCustomer.name.trim();
    const email = this.newCustomer.email.trim();
    const phone = this.newCustomer.phone.trim();

    // Basic validation
    if (!name || !email || !phone) {
      this.errorMessage = 'Please fill all customer details.';
      return;
    }

    // Email validation
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    if (!emailPattern.test(email)) {
      this.errorMessage = 'Please enter a valid email address.';
      return;
    }

    // Phone validation
    const phonePattern = /^[0-9]{10}$/;

    if (!phonePattern.test(phone)) {
      this.errorMessage = 'Phone number must contain exactly 10 digits.';
      return;
    }

    const customerData = {
      name,
      email,
      phone,
    };

    this.isSaving = true;

    // =================================================
    // UPDATE
    // =================================================

    if (this.editingId !== null) {
      const id = this.editingId;

      this.http.put<any>(`${this.API_URL}/${id}`, customerData).subscribe({
        next: (updatedCustomer) => {
          this.customers.update((list) =>
            list.map((customer) => (customer.customerId === id ? updatedCustomer : customer)),
          );

          this.isSaving = false;

          this.successMessage = 'Customer updated.';

          this.resetForm();
          this.cdr.markForCheck();
        },

        error: (error) => {
          this.isSaving = false;

          this.errorMessage = apiErrorMessage(error, 'Unable to update the customer.');
          this.cdr.markForCheck();
        },
      });

      return;
    }

    // =================================================
    // ADD
    // =================================================

    this.http.post<any>(this.API_URL, customerData).subscribe({
      next: (createdCustomer) => {
        this.customers.update((list) => [...list, createdCustomer]);

        this.isSaving = false;

          this.successMessage = 'Customer created.';

        this.resetForm();
        this.cdr.markForCheck();
      },

      error: (error) => {
        this.isSaving = false;

        this.errorMessage = apiErrorMessage(error, 'Unable to create the customer.');
        this.cdr.markForCheck();
      },
    });
  }

  // =====================================================
  // EDIT CUSTOMER
  // =====================================================

  editCustomer(customer: any): void {
    this.editingId = customer.customerId;

    this.newCustomer = {
      name: customer.name ?? '',
      email: customer.email ?? '',
      phone: customer.phone ?? '',
    };

    this.showForm = true;
  }

  // =====================================================
  // DELETE CUSTOMER
  // =====================================================

  deleteCustomer(id: number): void {
    if (!Number.isInteger(id) || id <= 0 || this.deletingId !== null) return;
    const confirmed = confirm('Are you sure you want to delete this customer?');

    if (!confirmed) {
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';
    this.deletingId = id;
    this.http.delete(`${this.API_URL}/${id}`).subscribe({
      next: () => {
        this.deletingId = null;
        this.customers.update((list) => list.filter((customer) => customer.customerId !== id));
        this.successMessage = 'Customer deleted.';
        this.cdr.markForCheck();
      },

      error: (error) => {
        this.deletingId = null;
        this.errorMessage = apiErrorMessage(error, 'Unable to delete the customer.');
        this.cdr.markForCheck();
      },
    });
  }

  // =====================================================
  // RESET FORM
  // =====================================================

  resetForm(): void {
    this.newCustomer = {
      name: '',
      email: '',
      phone: '',
    };

    this.editingId = null;
    this.showForm = false;
    this.isSaving = false;
  }

  // =====================================================
  // CANCEL
  // =====================================================

  cancelEdit(): void {
    this.resetForm();
  }
}
