import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UserRoleForm } from './user-role-form';

describe('UserRoleForm', () => {
  let component: UserRoleForm;
  let fixture: ComponentFixture<UserRoleForm>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserRoleForm]
    })
    .compileComponents();

    fixture = TestBed.createComponent(UserRoleForm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
