import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Permission } from './permission';

describe('Permission', () => {
  let component: Permission;
  let fixture: ComponentFixture<Permission>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Permission]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Permission);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
