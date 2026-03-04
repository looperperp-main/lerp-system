import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RolePermissionForm } from './role-permission-form';

describe('RolePermissionForm', () => {
  let component: RolePermissionForm;
  let fixture: ComponentFixture<RolePermissionForm>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RolePermissionForm]
    })
    .compileComponents();

    fixture = TestBed.createComponent(RolePermissionForm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
