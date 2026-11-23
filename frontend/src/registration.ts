import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom, timeout } from 'rxjs';

interface Account { id: number; firstName: string; lastName: string; email: string; roles: string[]; }
interface LoginResponse { accessToken: string; tokenType: string; expiresIn: number; user: Account; }
interface SavedSession { accessToken: string; expiresAt: number; }
interface Product {
  id: number;
  name: string;
  description: string;
  brand: string;
  sku?: string;
  price: number;
  stockQuantity: number;
  available: boolean;
  category: { name: string } | null;
}
interface CartLine { product: Product; quantity: number; }
type PaymentMethod = 'CARD' | 'CASH_ON_DELIVERY';
interface Order {
  id: number;
  totalAmount: number;
  status: 'CREATED' | 'CONFIRMED' | 'CANCELLED';
  shippingAddress: string | null;
  paymentMethod?: PaymentMethod;
  items: { id: number; productName: string; quantity: number; unitPrice: number; subtotal: number }[];
}
interface Payment { id: number; status: 'PENDING' | 'SUCCEEDED' | 'FAILED'; }
type Screen = 'products' | 'product' | 'cart' | 'auth' | 'checkout' | 'payment' | 'orders';
const basketKey = 'novastore.basket';
const sessionKey = 'novastore.session';
const paymentTimeoutMs = 10_000;

@Component({
  selector: 'app-registration',
  imports: [ReactiveFormsModule, CurrencyPipe],
  templateUrl: './registration.html',
})
export class RegistrationComponent {
  private readonly http = inject(HttpClient);
  private readonly builder = inject(NonNullableFormBuilder);
  readonly screen = signal<Screen>('products');
  readonly products = signal<Product[]>([]);
  readonly search = signal('');
  readonly category = signal('');
  readonly inStock = signal(false);
  readonly sort = signal('default');
  readonly categories = computed(() => [...new Set(this.products().flatMap(product =>
    product.category ? [product.category.name] : []))].sort((a, b) => a.localeCompare(b, 'fr')));
  readonly filteredProducts = computed(() => {
    const term = this.search().trim().toLocaleLowerCase('fr');
    const category = this.category();
    const inStock = this.inStock();
    const products = this.products().filter(product =>
      (!category || product.category?.name === category)
      && (!inStock || product.available)
      && (!term || [product.name, product.description, product.brand, product.category?.name]
        .some(value => value?.toLocaleLowerCase('fr').includes(term))));

    switch (this.sort()) {
      case 'price-asc': return products.sort((a, b) => a.price - b.price);
      case 'price-desc': return products.sort((a, b) => b.price - a.price);
      case 'name': return products.sort((a, b) => a.name.localeCompare(b.name, 'fr'));
      default: return products;
    }
  });
  readonly selected = signal<Product | null>(null);
  readonly loadingProducts = signal(true);
  readonly catalogError = signal(false);
  readonly basket = signal<CartLine[]>(this.readBasket());
  readonly count = computed(() => this.basket().reduce((sum, line) => sum + line.quantity, 0));
  readonly total = computed(() => this.basket().reduce((sum, line) => sum + line.quantity * line.product.price, 0));
  readonly checkout = this.builder.group({
    shippingAddress: ['', [Validators.required, Validators.maxLength(500)]],
    paymentMethod: this.builder.control<PaymentMethod>('CARD'),
  });
  readonly orders = signal<Order[]>([]);
  readonly currentOrder = signal<Order | null>(null);
  readonly payment = signal<Payment | null>(null);
  readonly demoCardReady = signal(false);
  readonly shopError = signal('');
  readonly orderLabels = { CREATED: 'En attente de paiement', CONFIRMED: 'Confirmée', CANCELLED: 'Annulée' };
  private afterLogin: Screen = 'products';
  private beforeOrders: Screen = 'products';
  private beforeCart: Screen = 'products';
  private orderKey = '';
  private orderDraft = '';

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
  readonly mode = signal<'register' | 'login'>('register');
  readonly account = signal<Account | null>(null);
  readonly profileSaved = signal(false);
  readonly profileForm = this.builder.group({
    firstName: ['', [Validators.required, Validators.maxLength(255)]],
    lastName: ['', [Validators.required, Validators.maxLength(255)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
  });
  readonly loginForm = this.builder.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.maxLength(72)]],
  });
  private accessToken = '';
  private sessionExpiresAt = 0;
  private expiryTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    void this.restoreSession();
    this.loadProducts();
  }

  loadProducts(): void {
    this.loadingProducts.set(true);
    this.catalogError.set(false);
    this.http.get<Product[]>('/api/products').subscribe({
      next: products => {
        this.products.set(products);
        this.loadingProducts.set(false);
      },
      error: () => {
        this.catalogError.set(true);
        this.loadingProducts.set(false);
      },
    });
  }

  show(screen: Screen): void {
    this.shopError.set('');
    const account = this.account();
    if (screen === 'auth' && account) {
      this.profileForm.reset({ firstName: account.firstName, lastName: account.lastName, email: account.email });
      this.profileSaved.set(false);
      this.error.set('');
    }
    this.screen.set(screen);
  }

  selectCategory(category: string): void {
    this.category.set(category);
    this.show('products');
  }

  openCart(): void {
    const previous = this.screen();
    if (previous !== 'cart') this.beforeCart = previous;
    this.show('cart');
  }

  backFromCart(): void {
    if (this.beforeCart === 'orders') {
      void this.loadOrders('orders');
      return;
    }
    this.show(this.beforeCart);
  }

  searchProducts(term: string): void {
    this.search.set(term);
    this.show('products');
  }

  clearFilters(): void {
    this.search.set('');
    this.category.set('');
    this.inStock.set(false);
    this.sort.set('default');
  }

  viewProduct(product: Product): void {
    this.selected.set(product);
    this.show('product');
  }

  quantity(product: Product): number {
    return this.basket().find(line => line.product.id === product.id)?.quantity ?? 0;
  }

  changeQuantity(product: Product, delta: number): void {
    const quantity = this.quantity(product) + delta;
    if (delta > 0 && (!product.available || quantity > product.stockQuantity)) return;
    const lines = this.basket();
    if (quantity <= 0) this.saveBasket(lines.filter(line => line.product.id !== product.id));
    else if (!this.quantity(product)) this.saveBasket([...lines, { product, quantity }]);
    else this.saveBasket(lines.map(line => line.product.id === product.id ? { product, quantity } : line));
  }

  requireAccount(screen: Screen): void {
    if (this.account()) {
      this.show(screen);
      return;
    }
    this.afterLogin = screen;
    this.changeMode('login');
    this.show('auth');
  }

  async beginCheckout(): Promise<void> {
    if (this.busy()) return;
    this.shopError.set('');
    const result = await this.restoreSession();
    if (result === 'unavailable') {
      this.shopError.set('Impossible de vérifier ta connexion. Réessaie dans un instant.');
      return;
    }
    this.requireAccount('checkout');
  }

  async placeOrder(): Promise<void> {
    const account = this.account();
    if (!account) { this.requireAccount('checkout'); return; }
    const shippingAddress = this.checkout.controls.shippingAddress.value.trim();
    if (this.busy() || !this.count() || this.checkout.invalid) return;
    if (!shippingAddress) {
      this.shopError.set('Saisis une adresse de livraison.');
      return;
    }
    const items = this.basket().map(({ product, quantity }) => ({ productId: product.id, quantity }));
    const payload = { customerId: account.id, shippingAddress, items, paymentMethod: this.checkout.controls.paymentMethod.value };
    const draft = JSON.stringify(payload);
    if (draft !== this.orderDraft) {
      this.orderDraft = draft;
      this.orderKey = crypto.randomUUID();
    }
    this.busy.set(true);
    this.shopError.set('');
    try {
      const order = await firstValueFrom(this.http.post<Order>('/api/orders', payload, {
        headers: { ...this.authorization(), 'Idempotency-Key': this.orderKey },
      }));
      if (this.account()?.id === account.id) {
        this.orderDraft = '';
        this.orderKey = '';
        this.saveBasket([]);
        this.openOrder(order);
      }
    } catch (failure) {
      this.shopError.set(failure instanceof HttpErrorResponse && failure.status === 409
        ? 'Le stock a changé. Vérifie les quantités dans ton panier.'
        : 'La commande n’a pas pu être confirmée. Tu peux réessayer.');
    } finally {
      this.busy.set(false);
    }
  }

  openOrder(order: Order): void {
    if (!this.account()) return;
    this.demoCardReady.set(false);
    if (this.currentOrder()?.id !== order.id) this.payment.set(null);
    this.currentOrder.set(order);
    this.show('payment');
  }

  canPay(order: Order): boolean {
    return order.paymentMethod !== 'CASH_ON_DELIVERY' && (
      order.status === 'CREATED' || (order.status === 'CONFIRMED' && this.hasPendingPayment(order.id))
    );
  }

  async pay(): Promise<void> {
    const order = this.currentOrder();
    const account = this.account();
    if (!account || !this.demoCardReady() || !order || !this.canPay(order) || this.busy()
      || this.payment()?.status === 'FAILED') return;
    const pendingKey = this.pendingPaymentKey(account.id, order.id);
    this.busy.set(true);
    this.shopError.set('');
    try {
      const options = { headers: this.authorization() };
      const pending = this.payment() ?? await firstValueFrom(this.http.post<Payment>(
        '/api/payments', { orderId: order.id, amount: order.totalAmount }, {
          headers: { ...options.headers, 'Idempotency-Key': 'novastore-payment-' + order.id },
        }).pipe(timeout(paymentTimeoutMs)));
      if (this.account()?.id !== account.id) return;
      this.payment.set(pending);
      let result = pending;
      if (pending.status === 'PENDING') {
        this.rememberPendingPayment(pendingKey, true);
        result = await firstValueFrom(this.http.patch<Payment>(
          '/api/payments/' + pending.id + '/status', { status: 'SUCCEEDED' }, options).pipe(timeout(paymentTimeoutMs)));
      }
      if (result.status !== 'PENDING') this.rememberPendingPayment(pendingKey, false);
      if (this.account()?.id !== account.id) return;
      this.payment.set(result);
      if (result.status === 'SUCCEEDED') this.currentOrder.set({ ...order, status: 'CONFIRMED' });
      else if (result.status === 'FAILED') this.shopError.set('Cette tentative a échoué. Vérifie la commande dans ton historique.');
      else this.shopError.set('Le résultat du paiement est encore en attente. Réessaie pour le vérifier.');
    } catch (failure) {
      if (this.account()?.id !== account.id) return;
      if (failure instanceof HttpErrorResponse && failure.status === 401) {
        this.logout();
        this.error.set('Session expirée. Reconnecte-toi pour retrouver ta commande.');
      } else if (failure instanceof HttpErrorResponse && failure.status === 409) {
        this.shopError.set('Le paiement est en conflit avec l’état de la commande. Consulte Mes commandes.');
      } else if (failure instanceof HttpErrorResponse && failure.status === 404) {
        this.shopError.set('La commande ou le paiement est introuvable. Consulte Mes commandes.');
      } else {
        this.shopError.set('La confirmation est temporairement indisponible. Réessaie pour reprendre le même paiement.');
      }
    } finally {
      this.busy.set(false);
    }
  }

  private pendingPaymentKey(accountId: number, orderId: number): string {
    return `novastore.pending-payment.${accountId}.${orderId}`;
  }

  private hasPendingPayment(orderId: number): boolean {
    const account = this.account();
    if (!account) return false;
    try {
      return sessionStorage.getItem(this.pendingPaymentKey(account.id, orderId)) === '1';
    } catch {
      return false;
    }
  }

  private rememberPendingPayment(key: string, pending: boolean): void {
    try {
      if (pending) sessionStorage.setItem(key, '1');
      else sessionStorage.removeItem(key);
    } catch { /* La reprise reste disponible depuis la page courante. */ }
  }

  async loadOrders(previous = this.screen()): Promise<void> {
    if (previous !== 'orders') this.beforeOrders = previous;
    this.requireAccount('orders');
    const account = this.account();
    if (!account || this.busy()) return;
    this.busy.set(true);
    try {
      const orders = await firstValueFrom(this.http.get<Order[]>('/api/orders/customer/' + account.id, {
        headers: this.authorization(),
      }));
      if (this.account()?.id === account.id) this.orders.set(orders);
    } catch {
      this.shopError.set('Impossible de charger tes commandes.');
    } finally {
      this.busy.set(false);
    }
  }

  backFromOrders(): void {
    this.show(this.beforeOrders);
  }

  private authorization(): Record<string, string> {
    return { Authorization: 'Bearer ' + this.accessToken };
  }

  private async restoreSession(): Promise<'valid' | 'missing' | 'unavailable'> {
    let session: SavedSession | null;
    try {
      session = this.accessToken
        ? { accessToken: this.accessToken, expiresAt: this.sessionExpiresAt }
        : JSON.parse(sessionStorage.getItem(sessionKey) ?? 'null');
    } catch {
      this.clearSavedSession();
      return 'missing';
    }
    if (!session) return 'missing';
    if (typeof session.accessToken !== 'string' || !session.accessToken
      || !Number.isFinite(session.expiresAt) || session.expiresAt <= Date.now()) {
      this.logout();
      return 'missing';
    }

    this.busy.set(true);
    try {
      const account = await firstValueFrom(this.http.get<Account>('/api/auth/me', {
        headers: { Authorization: 'Bearer ' + session.accessToken },
      }).pipe(timeout(8000)));
      if (session.expiresAt > Date.now()) {
        this.startSession(session, account);
        return 'valid';
      }
      this.logout();
      return 'missing';
    } catch (response) {
      if (response instanceof HttpErrorResponse && (response.status === 401 || response.status === 403)) {
        this.logout();
        return 'missing';
      }
      return 'unavailable';
    } finally {
      this.busy.set(false);
    }
  }

  private startSession(session: SavedSession, account: Account): void {
    clearTimeout(this.expiryTimer);
    this.accessToken = session.accessToken;
    this.sessionExpiresAt = session.expiresAt;
    this.account.set(account);
    this.expiryTimer = setTimeout(() => this.logout(), session.expiresAt - Date.now());
  }

  private clearSavedSession(): void {
    try { sessionStorage.removeItem(sessionKey); } catch { /* Le stockage peut être bloqué. */ }
  }

  private readBasket(): CartLine[] {
    try {
      const lines: unknown = JSON.parse(localStorage.getItem(basketKey) ?? '[]');
      return Array.isArray(lines) ? lines.filter(line => Number.isSafeInteger(line?.product?.id)
        && Number.isFinite(line?.product?.price) && Number.isSafeInteger(line?.quantity) && line.quantity > 0) : [];
    } catch {
      return [];
    }
  }

  private saveBasket(lines: CartLine[]): void {
    this.basket.set(lines);
    try { localStorage.setItem(basketKey, JSON.stringify(lines)); } catch { /* Le panier reste disponible en mémoire. */ }
  }

  changeMode(mode: 'register' | 'login'): void {
    if (this.busy()) return;
    this.mode.set(mode);
    this.error.set('');
    this.submitted.set(false);
    this.showPassword.set(false);
    this.form.controls.password.reset();
    this.loginForm.controls.password.reset();
    this.loginForm.controls.email.setValue(this.createdEmail() || this.loginForm.controls.email.value);
    this.createdEmail.set('');
  }

  login(event: Event): void {
    if (this.busy()) return;
    this.error.set('');
    // Password managers can fill the DOM without updating Angular's form controls.
    if (event.target instanceof HTMLFormElement) {
      const fields = new FormData(event.target);
      this.loginForm.setValue({
        email: String(fields.get('email') ?? '').trim(),
        password: String(fields.get('password') ?? ''),
      });
    }
    if (this.loginForm.invalid) {
      this.error.set('Saisis ton adresse e-mail et ton mot de passe.');
      return;
    }
    this.busy.set(true);
    this.http.post<LoginResponse>('/api/auth/login', this.loginForm.getRawValue()).subscribe({
      next: response => {
        const session = { accessToken: response.accessToken, expiresAt: Date.now() + response.expiresIn * 1000 };
        this.startSession(session, response.user);
        try { sessionStorage.setItem(sessionKey, JSON.stringify(session)); } catch { /* La session reste en mémoire. */ }
        this.loginForm.controls.password.reset();
        this.busy.set(false);
        const destination = this.afterLogin;
        this.afterLogin = 'products';
        if (destination === 'orders') void this.loadOrders();
        else this.show(destination);
      },
      error: (error: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(error.status === 401 ? 'Email ou mot de passe incorrect.' : 'Connexion impossible. Réessaie dans un instant.');
      },
    });
  }

  async saveProfile(): Promise<void> {
    const account = this.account();
    if (!account || this.busy()) return;
    this.error.set('');
    this.profileSaved.set(false);
    const fields = this.profileForm.getRawValue();
    const update = { firstName: fields.firstName.trim(), lastName: fields.lastName.trim(), email: fields.email.trim() };
    if (this.profileForm.invalid || !update.firstName || !update.lastName) {
      this.error.set('Vérifie le prénom, le nom et l’adresse e-mail.');
      return;
    }
    this.busy.set(true);
    try {
      const saved = await firstValueFrom(this.http.put<Account>('/api/users/' + account.id, update, {
        headers: this.authorization(),
      }));
      if (this.account()?.id !== account.id) return;
      this.account.set(saved);
      this.profileForm.reset({ firstName: saved.firstName, lastName: saved.lastName, email: saved.email });
      this.profileSaved.set(true);
    } catch (failure) {
      if (this.account()?.id !== account.id) return;
      if (failure instanceof HttpErrorResponse && failure.status === 401) {
        this.logout();
        this.error.set('Session expirée. Reconnecte-toi.');
      } else {
        this.error.set(failure instanceof HttpErrorResponse && failure.status === 409
          ? 'Cette adresse e-mail est déjà utilisée.' : 'Impossible d’enregistrer le profil.');
      }
    } finally {
      this.busy.set(false);
    }
  }

  logout(): void {
    clearTimeout(this.expiryTimer);
    this.clearSavedSession();
    this.accessToken = '';
    this.sessionExpiresAt = 0;
    this.account.set(null);
    this.orders.set([]);
    this.currentOrder.set(null);
    this.payment.set(null);
    const screen = this.screen();
    if (screen === 'checkout' || screen === 'orders') this.requireAccount(screen);
    if (screen === 'payment') this.requireAccount('orders');
    this.mode.set('login');
    this.error.set('');
  }

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

