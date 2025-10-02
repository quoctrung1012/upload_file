import {Component} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {HeaderComponent} from './header/header.component';
import {SwitchComponent} from './switch/switch.component';
import {LoadingComponent} from './loading/loading.component';

@Component({
  selector: 'lib-layout-navbar',
  imports: [
    RouterOutlet,
    HeaderComponent,
    SwitchComponent,
    LoadingComponent,
  ],
  template: `
    <div class="app-container">
      <div class="header-section">
        <app-header></app-header>
      </div>
      <div class="main-content">
        <router-outlet/>
      </div>
      <div class="footer-section">
        <div class="w-100 d-flex justify-content-end p-2">
          <app-switch></app-switch>
        </div>
      </div>
      <ng-loading></ng-loading>
    </div>
  `,
  styles: `
    .app-container {
      display: grid;
      grid-template-rows: auto 1fr auto;
      height: 100vh;
    }

    .header-section {
      grid-row: 1;
      position: sticky;
      top: 0;
      z-index: 999;
      background: aliceblue;
    }

    .main-content {
      grid-row: 2;
      overflow-y: auto;
    }

    .footer-section {
      grid-row: 3;
    }
  `
})
export class LayoutNavbarComponent {

}
