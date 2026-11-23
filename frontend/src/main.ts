import { provideHttpClient } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { LOCALE_ID, provideZonelessChangeDetection } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { RegistrationComponent } from './registration';

registerLocaleData(localeFr);

bootstrapApplication(RegistrationComponent, {
  providers: [provideHttpClient(), provideZonelessChangeDetection(), { provide: LOCALE_ID, useValue: 'fr' }],
}).catch(console.error);
