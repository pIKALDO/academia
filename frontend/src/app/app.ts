import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ErrorBanner } from './shared/error-banner/error-banner';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ErrorBanner],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
