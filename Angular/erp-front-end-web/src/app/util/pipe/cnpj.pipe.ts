import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'cnpj',
  standalone: true
})
export class CnpjPipe implements PipeTransform {

  transform(value: string | number): string {
    if (!value) return '';

    const cnpj = String(value).replace(/[.\-\/]/g, '').toUpperCase();

    if (cnpj.length === 14) {
      return cnpj.replace(/^(.{2})(.{3})(.{3})(.{4})(.{2})$/, '$1.$2.$3/$4-$5');
    }

    return value.toString();
  }

}