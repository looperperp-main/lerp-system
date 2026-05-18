import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PlansForms } from './plans-forms';

describe('PlansForms', () => {
  let component: PlansForms;
  let fixture: ComponentFixture<PlansForms>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PlansForms]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PlansForms);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
