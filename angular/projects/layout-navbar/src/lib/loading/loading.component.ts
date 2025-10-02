import { Component, OnInit, OnDestroy } from '@angular/core';

import { Subscription } from 'rxjs';
import {NgIf} from '@angular/common';
import {HttpClientService} from '../core/http-client.service';

@Component({
  selector: 'ng-loading',
  template: `
    <div *ngIf="isLoading" class="loading-overlay">
      <div class="loading-container">
        <div class="spinner">
          <div class="double-bounce1"></div>
          <div class="double-bounce2"></div>
        </div>
        <p class="loading-message">{{ message }}</p>
      </div>
    </div>
  `,
  imports: [
    NgIf
  ],
  styles: [`
    .loading-overlay {
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background-color: rgba(0, 0, 0, 0.5);
      display: flex;
      justify-content: center;
      align-items: center;
      z-index: 9999;
    }

    .loading-container {
      background: white;
      border-radius: 8px;
      padding: 30px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
      text-align: center;
      min-width: 200px;
    }

    .spinner {
      width: 40px;
      height: 40px;
      position: relative;
      margin: 0 auto 20px;
    }

    .double-bounce1, .double-bounce2 {
      width: 100%;
      height: 100%;
      border-radius: 50%;
      background-color: #007bff;
      opacity: 0.6;
      position: absolute;
      top: 0;
      left: 0;
      animation: sk-bounce 2.0s infinite ease-in-out;
    }

    .double-bounce2 {
      animation-delay: -1.0s;
    }

    @keyframes sk-bounce {
      0%, 100% {
        transform: scale(0);
      }
      50% {
        transform: scale(1.0);
      }
    }

    .loading-message {
      margin: 0;
      font-size: 16px;
      color: #333;
      font-weight: 500;
    }
  `]
})
export class LoadingComponent implements OnInit, OnDestroy {
  isLoading = false;
  message = '';
  private subscriptions: Subscription[] = [];

  constructor(private httpClientService: HttpClientService) {}

  ngOnInit() {
    this.subscriptions.push(
      this.httpClientService.loading$.subscribe((loading: boolean) => {
        this.isLoading = loading;
      })
    );

    this.subscriptions.push(
      this.httpClientService.message$.subscribe((message: string) => {
        this.message = message;
      })
    );
  }

  ngOnDestroy() {
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }
}
