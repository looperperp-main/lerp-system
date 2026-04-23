import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Transportadoras } from './transportadoras';

describe('Transportadoras', () => {
  let component: Transportadoras;
  let fixture: ComponentFixture<Transportadoras>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Transportadoras]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Transportadoras);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
