import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CondPagamento } from './cond-pagamentos';

describe('CondPagamento', () => {
  let component: CondPagamento;
  let fixture: ComponentFixture<CondPagamento>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CondPagamento]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CondPagamento);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
