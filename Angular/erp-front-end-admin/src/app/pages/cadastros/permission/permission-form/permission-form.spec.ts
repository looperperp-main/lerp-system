import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PermissionForm } from './permission-form';

describe('PermissionForm', () => {
  let component: PermissionForm;
  let fixture: ComponentFixture<PermissionForm>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PermissionForm]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PermissionForm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
