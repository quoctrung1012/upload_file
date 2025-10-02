import {Component, inject} from '@angular/core';
import {NgTemplateOutlet} from '@angular/common';
import {NgbToastModule} from '@ng-bootstrap/ng-bootstrap';
import {ToastService} from './toast.service';

@Component({
  selector: 'app-toasts',
  imports: [NgbToastModule, NgTemplateOutlet],
  standalone: true,
  template: `
    @for (toast of toastService.toasts; track toast) {
      <ngb-toast
        [class]="toast.classname"
        [autohide]="true"
        [delay]="toast.delay || 3000"
        (hidden)="toastService.remove(toast)"
      >
        <ng-template [ngTemplateOutlet]="toast.template"></ng-template>
      </ngb-toast>
    }
  `,
  host: {class: 'toast-container position-fixed top-0 end-0 p-3', style: 'z-index: 1200'},
})
export class ToastsContainer {
  toastService = inject(ToastService);
}
