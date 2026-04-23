import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GrupoClienteTabelaPrecoForm } from './grupo-cliente-tabela-preco-form';

describe('GrupoClienteTabelaPrecoForm', () => {
  let component: GrupoClienteTabelaPrecoForm;
  let fixture: ComponentFixture<GrupoClienteTabelaPrecoForm>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GrupoClienteTabelaPrecoForm]
    })
    .compileComponents();

    fixture = TestBed.createComponent(GrupoClienteTabelaPrecoForm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
