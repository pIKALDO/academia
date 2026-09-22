import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SessionService } from '../../core/services/session.service';

@Component({
  selector: 'app-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  protected readonly user = this.session.currentUser;
  protected readonly isAdmin = () => this.user()?.role === 'ADMIN';

  logout(): void {
    this.session.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
