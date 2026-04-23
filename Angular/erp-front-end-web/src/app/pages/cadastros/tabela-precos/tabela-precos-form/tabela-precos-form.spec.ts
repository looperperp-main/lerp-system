import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TabelaPrecosForm } from './tabela-precos-form';

describe('TabelaPrecosForm', () => {
  let component: TabelaPrecosForm;
  let fixture: ComponentFixture<TabelaPrecosForm>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TabelaPrecosForm]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TabelaPrecosForm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
