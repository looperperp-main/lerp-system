import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GrupoClienteForm } from './grupo-cliente-form';

describe('GrupoClienteForm', () => {
  let component: GrupoClienteForm;
  let fixture: ComponentFixture<GrupoClienteForm>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GrupoClienteForm]
    })
    .compileComponents();

    fixture = TestBed.createComponent(GrupoClienteForm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
