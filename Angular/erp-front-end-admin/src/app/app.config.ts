import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import {provideHttpClient, withFetch, withInterceptors} from '@angular/common/http';
import {provideToastr} from 'ngx-toastr';
import {provideAnimations} from '@angular/platform-browser/animations';
import {authInterceptor} from './util/auth.interceptor';
import Aura from '@primeuix/themes/aura';
import {providePrimeNG} from 'primeng/config';
import {MessageService} from 'primeng/api';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withFetch(),withInterceptors([authInterceptor])),
    provideToastr(),
    MessageService,
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
    provideAnimations()//TODO: Deprecated
  ]
};
