import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'cnpj',
  standalone: true
})
export class CnpjPipe implements PipeTransform {

  transform(value: string | number): string {
    if (!value) return '';

    // Converte para string e remove qualquer caractere que não seja número
    let cnpj = String(value).replace(/\D/g, '');

    // Verifica se possui os 14 dígitos antes de formatar
    if (cnpj.length === 14) {
      return cnpj.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})/, '$1.$2.$3/$4-$5');
    }

    // Se não tiver 14 dígitos, retorna o valor original
    return value.toString();
  }

}
