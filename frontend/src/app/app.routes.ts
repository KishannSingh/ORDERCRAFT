import { Routes } from '@angular/router';

import { Dashboard } from './dashboard/dashboard';
import { Customers } from './customers/customers';
import { Products } from './products/products';
import { Bom } from './bom/bom';
import { SalesOrders } from './sales-orders/sales-orders';
import { PurchaseOrders } from './purchase-orders/purchase-orders';
import { Invoices } from './invoices/invoices';
import { Payments } from './payments/payments';

import { Login } from './auth/login/login';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  /* =====================================================
     LOGIN
     ===================================================== */

  {
    path: 'login',
    component: Login,
  },

  /* =====================================================
     PROTECTED ROUTES
     ===================================================== */

  {
    path: 'dashboard',
    component: Dashboard,
    canActivate: [authGuard],
  },

  {
    path: 'customers',
    component: Customers,
    canActivate: [authGuard],
  },

  {
    path: 'products',
    component: Products,
    canActivate: [authGuard],
  },

  {
    path: 'bom',
    component: Bom,
    canActivate: [authGuard],
  },

  {
    path: 'orders',
    component: SalesOrders,
    canActivate: [authGuard],
  },

  {
    path: 'purchase-orders',
    component: PurchaseOrders,
    canActivate: [authGuard],
  },

  {
    path: 'invoices',
    component: Invoices,
    canActivate: [authGuard],
  },

  {
    path: 'payments',
    component: Payments,
    canActivate: [authGuard],
  },

  /* =====================================================
     DEFAULT ROUTE
     ===================================================== */

  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full',
  },

  /* =====================================================
     UNKNOWN ROUTE
     ===================================================== */

  {
    path: '**',
    redirectTo: 'dashboard',
  },
];
