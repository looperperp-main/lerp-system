import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GrupoClientes } from './grupo-clientes';

describe('GrupoClientes', () => {
  let component: GrupoClientes;
  let fixture: ComponentFixture<GrupoClientes>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GrupoClientes]
    })
    .compileComponents();

    fixture = TestBed.createComponent(GrupoClientes);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
