import {Component, EventEmitter, inject, Input, OnInit, Output, signal} from '@angular/core';
import {Vendedor} from '../vendedor.model';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from '@angular/forms';
import {ToastrService} from 'ngx-toastr';
import {VendedorService} from '../vendedor.service';
import {InputNumber} from 'primeng/inputnumber';
import {Checkbox} from 'primeng/checkbox';
import {Select} from 'primeng/select';
import {ButtonDirective} from 'primeng/button';
import {InputText} from 'primeng/inputtext';

@Component({
  selector: 'app-vendedor-form',
  imports: [
    InputNumber,
    Checkbox,
    FormsModule,
    ReactiveFormsModule,
    Select,
    ButtonDirective,
    InputText
  ],
  templateUrl: './vendedor-form.html',
  styleUrl: './vendedor-form.scss',
})
export class VendedorForm implements OnInit {
  @Input() VendedorData: Vendedor | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private toastService = inject(ToastrService);
  private vendedorService = inject(VendedorService);
  pessoasOptions = signal<any[]>([]);

  form!: FormGroup;

  constructor(
    
  ) {
    this.form = this.fb.group({
      id: [this.VendedorData?.id || null],
      nome: [this.VendedorData?.nome || '', [Validators.required, Validators.maxLength(100)]],
      comissaoPercentual: [this.VendedorData?.comissaoPercentual || 0, [Validators.min(0)]],
      ativo: [this.VendedorData?.ativo || false],
      pessoaId: [this.VendedorData?.pessoaId || null]
    });
  }

  ngOnInit(): void {
    this.loadPessoas();
    // Verifica se os dados chegaram via @Input (aberto em Dialog)
    if (this.VendedorData) {

      this.form.patchValue({
        nome: this.VendedorData.nome,
        comissaoPercentual: this.VendedorData.comissaoPercentual,
        ativo: this.VendedorData.ativo !== false, // Caso seja undefined, assume true
        pessoaId: this.VendedorData.pessoaId
      });
    }

  }

  loadPessoas() {
    this.vendedorService.getPessoasDropdown().subscribe({
      next: (res) => {
        // Ajuste conforme o retorno da sua API de pessoas (usando _embedded do HATEOAS ou content)
        const content = res._embedded ? res._embedded.pessoas : (res.content || []);
        this.pessoasOptions.set(content.map((p: any) => ({
          label: p.nomeRazao,
          value: p.id
        })));
      },
      error: (err) => console.error('Erro ao carregar pessoas', err)
    });
  }

  loadVendedor(id: string) {
    this.vendedorService.getById(id).subscribe({
      next: (vendedor) => {
        this.form.patchValue(vendedor);
      },
      error: () => console.log('')//this.toastr.error('Erro ao carregar os dados do vendedor')
    });
  }

  save() {
    if (this.form.invalid) {
      //this.toastr.warning('Preencha os campos corretamente');
      return;
    }

    const vendedorData = this.form.value;

    if (this.VendedorData) {
      this.vendedorService.update(this.VendedorData.id, vendedorData).subscribe({
        next: () => {
          //this.toastr.success('Vendedor atualizado com sucesso');
          this.saved.emit();
        },
        error: () => console.log('')//this.toastr.error('Erro ao atualizar vendedor')
      });
    } else {
      this.vendedorService.create(vendedorData).subscribe({
        next: () => {
          //this.toastr.success('Vendedor criado com sucesso');
          this.saved.emit();
        },
        error: () => console.log('')//this.toastr.error('Erro ao criar vendedor')
      });
    }
  }

  onCancel(): void {
    this.canceled.emit();
  }
}
