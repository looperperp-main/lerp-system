import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RolePermissions } from './role-permissions';

describe('RolePermissions', () => {
  let component: RolePermissions;
  let fixture: ComponentFixture<RolePermissions>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RolePermissions]
    })
    .compileComponents();

    fixture = TestBed.createComponent(RolePermissions);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
