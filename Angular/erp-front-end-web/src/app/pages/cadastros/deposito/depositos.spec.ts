import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Depositos } from './depositos';

describe('Depositos', () => {
  let component: Depositos;
  let fixture: ComponentFixture<Depositos>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Depositos]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Depositos);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
