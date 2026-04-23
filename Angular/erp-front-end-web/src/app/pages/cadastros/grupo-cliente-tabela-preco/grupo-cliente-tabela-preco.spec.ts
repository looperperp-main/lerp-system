import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GrupoClienteTabelaPreco } from './grupo-cliente-tabela-preco';

describe('GrupoClienteTabelaPreco', () => {
  let component: GrupoClienteTabelaPreco;
  let fixture: ComponentFixture<GrupoClienteTabelaPreco>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GrupoClienteTabelaPreco]
    })
    .compileComponents();

    fixture = TestBed.createComponent(GrupoClienteTabelaPreco);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
