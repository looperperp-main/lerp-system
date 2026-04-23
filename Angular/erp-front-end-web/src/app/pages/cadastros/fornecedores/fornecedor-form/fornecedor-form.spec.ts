import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FornecedorForm } from './fornecedor-form';

describe('FornecedorForm', () => {
  let component: FornecedorForm;
  let fixture: ComponentFixture<FornecedorForm>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FornecedorForm]
    })
    .compileComponents();

    fixture = TestBed.createComponent(FornecedorForm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
