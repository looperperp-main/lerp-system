import {ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners} from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import {provideHttpClient, withFetch, withInterceptors} from '@angular/common/http';
import {provideToastr} from 'ngx-toastr';
import {authInterceptor} from './util/auth.interceptor';
import {providePrimeNG} from 'primeng/config';
import Aura from '@primeuix/themes/aura';
import {registerLocaleData} from '@angular/common';

import localePt from '@angular/common/locales/pt';
registerLocaleData(localePt, 'pt-BR');

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withFetch(),withInterceptors([authInterceptor])),
    provideToastr(),
    { provide: LOCALE_ID, useValue: 'pt-BR' },
    providePrimeNG({
      theme: {
        preset: Aura,
        options: {
          // Isso diz ao PrimeNG para aplicar o modo escuro APENAS se a tag HTML tiver a classe '.app-dark'
          // Como não vamos colocar essa classe, ele vai ficar sempre no modo Claro (Light)!
          darkModeSelector: '.app-dark'
        }
      }
    }),
  ]
};
