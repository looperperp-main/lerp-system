import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CondPagamentoForm } from './cond-pagamento-form';

describe('CondPagamentoForm', () => {
  let component: CondPagamentoForm;
  let fixture: ComponentFixture<CondPagamentoForm>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CondPagamentoForm]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CondPagamentoForm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
