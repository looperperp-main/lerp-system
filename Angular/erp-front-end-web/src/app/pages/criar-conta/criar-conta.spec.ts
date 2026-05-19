import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CriarConta } from './criar-conta';

describe('CriarConta', () => {
  let component: CriarConta;
  let fixture: ComponentFixture<CriarConta>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CriarConta]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CriarConta);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
