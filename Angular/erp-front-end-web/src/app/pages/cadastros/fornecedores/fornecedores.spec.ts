import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Fornecedores } from './fornecedores';

describe('Fornecedores', () => {
  let component: Fornecedores;
  let fixture: ComponentFixture<Fornecedores>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Fornecedores]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Fornecedores);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
