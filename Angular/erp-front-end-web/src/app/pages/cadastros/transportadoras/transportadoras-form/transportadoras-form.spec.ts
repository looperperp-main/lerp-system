import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TransportadorasForm } from './transportadoras-form';

describe('TransportadorasForm', () => {
  let component: TransportadorasForm;
  let fixture: ComponentFixture<TransportadorasForm>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TransportadorasForm]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TransportadorasForm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
