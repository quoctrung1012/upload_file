import {Component, ElementRef, inject, OnDestroy, TemplateRef, ViewChild} from '@angular/core';
import {ToastService} from './toast.service';
import {ToastsContainer} from './toast-container.compoment';
import {ApiService} from '../api.service';

@Component({
  selector: 'app-toast',
  imports: [
    ToastsContainer
  ],
  templateUrl: './toast.component.html',
  styleUrl: './toast.component.scss'
})
export class ToastComponent implements OnDestroy {
  toastService = inject(ToastService);
  message: string = '';
  @ViewChild('standardTpl') standardTpl!: TemplateRef<any>;
  @ViewChild('warningTpl') warningTpl!: TemplateRef<any>;
  @ViewChild('successTpl') successTpl!: TemplateRef<any>;
  @ViewChild('dangerTpl') dangerTpl!: TemplateRef<any>;

  constructor() {

  }

  showStandard(message?: string) {
    this.standard(message)
  }
  showSuccess(message?: string) {
    this.success(message);
  }

  showWarning(message?: string) {
    this.warning(message);
  }
  showDanger(message?: string) {
    this.danger(message);
  }

  private standard(message?: string) {
    this.message = message || 'I\'m a standard toast';
    let template: TemplateRef<any> = this.standardTpl;
    this.toastService.show({template});
  }

  private success(message?: string) {
    this.message = message || 'I\'m a success toast';
    let template: TemplateRef<any> = this.successTpl;
    this.toastService.show({template, classname: 'bg-success text-light'});
  }

  private warning(message?: string) {
    this.message = message || 'I\'m a warning toast';
    let template: TemplateRef<any> = this.warningTpl;
    this.toastService.show({template, classname: 'bg-warning text-light'});
  }

  private danger(message?: string) {
    this.message = message || 'Danger Danger !';
    let template: TemplateRef<any> = this.dangerTpl;
    this.toastService.show({template, classname: 'bg-danger text-light'});
  }

  ngOnDestroy(): void {
    this.toastService.clear();
  }
}
