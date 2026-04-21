import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProdutoCategoria } from './produto-categoria';

describe('ProdutoCategoria', () => {
  let component: ProdutoCategoria;
  let fixture: ComponentFixture<ProdutoCategoria>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProdutoCategoria]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ProdutoCategoria);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
