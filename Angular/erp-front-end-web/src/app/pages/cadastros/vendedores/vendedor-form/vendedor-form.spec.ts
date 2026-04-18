import { ComponentFixture, TestBed } from '@angular/core/testing';

import { VendedorForm } from './vendedor-form';

describe('VendedorForm', () => {
  let component: VendedorForm;
  let fixture: ComponentFixture<VendedorForm>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VendedorForm]
    })
    .compileComponents();

    fixture = TestBed.createComponent(VendedorForm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
