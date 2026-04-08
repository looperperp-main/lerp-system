import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DepositoForm } from './deposito-form';

describe('DepositoForm', () => {
  let component: DepositoForm;
  let fixture: ComponentFixture<DepositoForm>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DepositoForm]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DepositoForm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
