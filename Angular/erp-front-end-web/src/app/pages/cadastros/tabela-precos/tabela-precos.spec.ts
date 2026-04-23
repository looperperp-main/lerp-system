import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TabelaPrecos } from './tabela-precos';

describe('TabelaPrecos', () => {
  let component: TabelaPrecos;
  let fixture: ComponentFixture<TabelaPrecos>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TabelaPrecos]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TabelaPrecos);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
