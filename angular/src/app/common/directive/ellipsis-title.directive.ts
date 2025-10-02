import {
  Directive, ElementRef, Input, AfterViewInit, Renderer2
} from '@angular/core';

@Directive({
  selector: '[ellipsisTitle]',
  standalone: true
})
export class EllipsisTitleDirective implements AfterViewInit {
  @Input() ellipsisTitle: number = 2;

  constructor(private el: ElementRef, private renderer: Renderer2) {}

  ngAfterViewInit(): void {
    const element = this.el.nativeElement;

    // Áp dụng ellipsis
    this.renderer.setStyle(element, 'display', '-webkit-box');
    this.renderer.setStyle(element, '-webkit-line-clamp', this.ellipsisTitle);
    this.renderer.setStyle(element, '-webkit-box-orient', 'vertical');
    this.renderer.setStyle(element, 'overflow', 'hidden');

    // Kiểm tra bị cắt
    requestAnimationFrame(() => {
      if (element.scrollHeight > element.clientHeight) {
        this.renderer.setAttribute(element, 'title', element.textContent.trim());
      }
    });
  }
}
