import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Pessoas } from './pessoas';

describe('Pessoas', () => {
  let component: Pessoas;
  let fixture: ComponentFixture<Pessoas>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Pessoas]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Pessoas);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
