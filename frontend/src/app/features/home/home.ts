import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { SessionService } from '../../core/services/session.service';

@Component({
  selector: 'app-home',
  imports: [],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  protected readonly user = this.session.currentUser;

  logout(): void {
    this.session.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
