import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProdutoCategoriaForm } from './produto-categoria-form';

describe('ProdutoCategoriaForm', () => {
  let component: ProdutoCategoriaForm;
  let fixture: ComponentFixture<ProdutoCategoriaForm>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProdutoCategoriaForm]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ProdutoCategoriaForm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
