import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'htmlDecode',
  standalone: true
})
export class HtmlDecodePipe implements PipeTransform {

  transform(value: string | undefined): string {
    if (!value || value === '') return '-';

    // Cria um elemento textarea temporário na memória
    const doc = new DOMParser().parseFromString(value, "text/html");
    return doc.documentElement.textContent || value;
  }

}
