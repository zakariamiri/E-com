import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

@Component({
  selector: 'app-registration',
  imports: [ReactiveFormsModule],
  templateUrl: './registration.html',
})
export class RegistrationComponent {
  private readonly http = inject(HttpClient);
  private readonly builder = inject(NonNullableFormBuilder);

  readonly form = this.builder.group({
    firstName: ['', [Validators.required, Validators.maxLength(255)]],
    lastName: ['', [Validators.required, Validators.maxLength(255)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });
  readonly busy = signal(false);
  readonly submitted = signal(false);
  readonly showPassword = signal(false);
  readonly error = signal('');
  readonly createdEmail = signal('');

  register(): void {
    if (this.busy()) return;

    this.submitted.set(true);
    this.error.set('');
    const { firstName, lastName, email, password } = this.form.getRawValue();
    if (this.form.invalid || !firstName.trim() || !lastName.trim() || !password.trim()) {
      this.error.set('Vérifie les champs du formulaire.');
      return;
    }
    if (new TextEncoder().encode(password).length > 72) {
      this.error.set('Le mot de passe est trop long avec les caractères utilisés.');
      return;
    }

    this.busy.set(true);
    this.http.post('/api/users', {
      firstName: firstName.trim(),
      lastName: lastName.trim(),
      email: email.trim(),
      password,
    }).subscribe({
      next: () => {
        this.createdEmail.set(email.trim());
        this.form.reset();
        this.submitted.set(false);
        this.showPassword.set(false);
        this.busy.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.busy.set(false);
        if (error.status === 409) {
          this.error.set('Un compte utilise déjà cette adresse e-mail.');
        } else if (error.status === 400) {
          this.error.set('Vérifie les informations saisies et réessaie.');
        } else {
          this.error.set('Le service est indisponible. Réessaie dans un instant.');
        }
      },
    });
  }
}

