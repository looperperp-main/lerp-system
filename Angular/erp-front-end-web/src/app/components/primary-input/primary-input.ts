import { Component, forwardRef, Input } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from '@angular/forms';

type InputTypes = 'text' | 'email' | 'password';

@Component({
    selector: 'app-primary-input',
    standalone: true,
    imports: [ReactiveFormsModule],
    providers: [{
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => PrimaryInput),
      multi: true
    }],
    templateUrl: './primary-input.html',
    styleUrl: './primary-input.scss',
})
export class PrimaryInput implements ControlValueAccessor {

    @Input() type: InputTypes = 'text';
    @Input() placeholder: string = '';
    @Input() label: string = '';
    @Input() inputName: string = '';
    @Input() maxLength: number | null = null;
    @Input() mask: 'cnpj' | null = null;

    value: string = '';

    onChange: any = () => {};
    onTouched: any = () => {};

    writeValue(value: any): void {
        this.value = value || '';
    }

    registerOnChange(fn: any): void {
      this.onChange = fn;
    }

    registerOnTouched(fn: any): void {
      this.onTouched = fn;
    }

    setDisabledState?(isDisabled: boolean): void {}

    onInput(event: Event) {
      const input = event.target as HTMLInputElement;
      let value = input.value;

      if (this.mask === 'cnpj') {
          value = this.formatCnpj(value);
          input.value = value;
          // Envia apenas números para o form
          this.onChange(value.replace(/\D/g, ''));
      } else {
          this.onChange(value);
      }
    }

    private formatCnpj(value: string): string {
        let numbers = value.replace(/\D/g, '');

        if (numbers.length > 14) {
            numbers = numbers.slice(0, 14);
        }

        // Formato: XX.XXX.XXX/XXXX-XX
        if (numbers.length > 12) {
            return numbers.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5');
        } else if (numbers.length > 8) {
            return numbers.replace(/^(\d{2})(\d{3})(\d{3})(\d+)$/, '$1.$2.$3/$4');
        } else if (numbers.length > 5) {
            return numbers.replace(/^(\d{2})(\d{3})(\d+)$/, '$1.$2.$3');
        } else if (numbers.length > 2) {
            return numbers.replace(/^(\d{2})(\d+)$/, '$1.$2');
        }

        return numbers;
    }
}
