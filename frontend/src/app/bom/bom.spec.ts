import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_BASE_URL } from '../api';
import { Bom } from './bom';

describe('Bom', () => {
  let component: Bom;
  let fixture: ComponentFixture<Bom>;
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Bom],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(Bom);
    component = fixture.componentInstance;
    httpTesting = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    httpTesting.expectOne(`${API_BASE_URL}/products`).flush([]);
    httpTesting.expectOne(`${API_BASE_URL}/boms`).flush([]);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows inline validation when the calculator has no product selected', () => {
    component.productId = 0;
    component.orderQuantity = 3;

    component.calculateMaterial();
    fixture.detectChanges();

    expect(component.calculationError).toBe('Please select a product.');
    expect(fixture.nativeElement.textContent).toContain('Please select a product.');
  });

  it('shows inline validation for a non-positive or fractional order quantity', () => {
    component.productId = 42;
    component.orderQuantity = 1.5;

    component.calculateMaterial();
    fixture.detectChanges();

    expect(component.calculationError).toBe('Enter a valid whole-number order quantity.');
    expect(fixture.nativeElement.textContent).toContain('Enter a valid whole-number order quantity.');
  });
});
