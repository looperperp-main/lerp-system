import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TenantForm } from './tenant-form';

describe('TenantForm', () => {
  let component: TenantForm;
  let fixture: ComponentFixture<TenantForm>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TenantForm]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TenantForm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
