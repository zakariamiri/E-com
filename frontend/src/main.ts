import { provideHttpClient } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { RegistrationComponent } from './registration';

bootstrapApplication(RegistrationComponent, {
  providers: [provideHttpClient(), provideZonelessChangeDetection()],
}).catch(console.error);

