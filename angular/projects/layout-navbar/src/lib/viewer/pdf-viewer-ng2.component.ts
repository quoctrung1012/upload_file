import {Component, ViewChild, ElementRef, AfterViewInit, OnDestroy, ChangeDetectorRef, Input} from '@angular/core';
import {PdfViewerComponent, PdfViewerModule} from 'ng2-pdf-viewer';
import {FormsModule} from '@angular/forms';
import {NgStyle} from '@angular/common';
import {SafeResourceUrl} from '@angular/platform-browser';

const FILTER_PAG_REGEX = /[^0-9]/g;

@Component({
  selector: 'pdf-viewer-ng2',
  template: `
    <pdf-viewer
      [src]="urlView"
      [render-text]="true"
      [original-size]="false"
      [fit-to-page]="true"
      [zoom]="0.9"
      [rotation]="0"
      [show-all]="true"
      [page]="currentPage"
      (pageChange)="pageChange($event)"
      (after-load-complete)="afterLoadComplete($event)"
      (error)="errorPdf($event)"
      (pages-initialized)="pagesInitialized($event)"
      style="display: block; width: 100%; height: 600px;">
    </pdf-viewer>
    <div class="pdf-info mt-2 p-2 bg-gradient">
      <div class="d-flex justify-content-between align-items-center">
        <span>Trang hiện tại: <strong>{{ currentPage }}</strong> / <strong>{{ totalPages }}</strong></span>
        <span>Tiến độ scroll: <strong>{{ scrollPosition }}%</strong></span>
      </div>

      <!-- Optional: Navigation controls -->
      <div class="mt-2 d-flex align-items-center gap-2">
        <button (click)="goToPreviousPage()" [disabled]="currentPage <= 1">← Trang trước</button>
        <input
          #i
          type="text"
          inputmode="numeric"
          pattern="[0-9]*"
          class="text-center form-control-sm"
          [value]="currentPage"
          [max]="totalPages"
          min="1"
          (keyup.enter)="goToSpecificPage(i)"
          (blur)="goToSpecificPage(i)"
          (input)="formatInput($any($event).target)"
          [ngStyle]="{'width': getInputWidth()}">
        <button (click)="goToNextPage()" [disabled]="currentPage >= totalPages">Trang sau →</button>
      </div>
    </div>
  `,
  imports: [
    PdfViewerModule,
    FormsModule,
    NgStyle
  ],
  styles: [`
    .pdf-container {
      width: 100%;
      height: 600px;
    }

    /* PDF Viewer Styles */
    .pdf-info {
      margin-top: 10px;
      padding: 12px;
      background-color: #f8f9fa;
      border: 1px solid #dee2e6;
      border-radius: 6px;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      font-size: 14px;
    }

    .pdf-info .info-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
    }

    .pdf-info .info-row:last-child {
      margin-bottom: 0;
    }

    .pdf-info .navigation-controls {
      display: flex;
      gap: 8px;
      align-items: center;
      margin-top: 8px;
    }

    .pdf-info button {
      padding: 6px 12px;
      border: 1px solid #ccc;
      border-radius: 4px;
      background-color: white;
      color: #333;
      cursor: pointer;
      font-size: 12px;
      transition: all 0.2s ease;
    }

    .pdf-info button:hover:not(:disabled) {
      background-color: #007bff;
      color: white;
      border-color: #007bff;
    }

    .pdf-info button:disabled {
      opacity: 0.6;
      cursor: not-allowed;
      background-color: #f8f9fa;
    }

    .pdf-info input[type="number"] {
      width: 60px;
      padding: 4px 8px;
      border: 1px solid #ccc;
      border-radius: 4px;
      text-align: center;
      font-size: 12px;
    }

    .pdf-info input[type="number"]:focus {
      outline: none;
      border-color: #007bff;
      box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.25);
    }

    /* Responsive design */
    @media (max-width: 768px) {
      .pdf-info .info-row {
        flex-direction: column;
        gap: 8px;
        align-items: flex-start;
      }

      .pdf-info .navigation-controls {
        flex-wrap: wrap;
        justify-content: center;
      }

      .pdf-info button {
        font-size: 11px;
        padding: 5px 10px;
      }
    }
  `]
})
export class FixedPdfViewerComponent {
  @Input() urlView: SafeResourceUrl = '';
  @ViewChild(PdfViewerComponent) private pdfComponent!: PdfViewerComponent;
  totalPages: number = 0;
  scrollPosition: string = '0';
  currentPage: number = 1; // Khởi tạo trang hiện tại là 1

  protected getInputWidth(): string {
    const length = this.totalPages.toString().length;
    return `${2.5 + (length - 1) * 0.5}rem`;
  }

  protected formatInput(input: HTMLInputElement) {
    input.value = input.value.replace(FILTER_PAG_REGEX, '');
    if (!input.value) {
      return;
    }
  }

  pageChange($event: number) {
    this.currentPage = $event;
    this.getScrollPosition();
  }

  getScrollPosition() {
    this.scrollPosition = ((this.currentPage / this.totalPages) * 100).toFixed(2);
  }

  afterLoadComplete($event: import("pdfjs-dist/types/src/display/api").PDFDocumentProxy) {
    this.totalPages = $event._pdfInfo.numPages;
    //console.log('afterLoadComplete', $event._pdfInfo.numPages)
    this.getScrollPosition();
  }

  pagesInitialized($event: any) {
    if (this.totalPages === 0) {
      this.totalPages = $event['source'].pagesCount;
      this.getScrollPosition();
    }
    //console.log('pagesInitialized', $event['source'].pagesCount)
  }

  scrollToPage(page: number) {
    if (page < 0) return;
    if (page > this.totalPages) return;
    this.pdfComponent.pdfViewer.scrollPageIntoView({
      pageNumber: page
    });
  }

  errorPdf($event: any) {
    console.warn($event)
  }

  goToPreviousPage() {
    this.scrollToPage(this.currentPage - 1);
  }

  goToNextPage() {
    this.scrollToPage(this.currentPage + 1);
  }

  goToSpecificPage($event: HTMLInputElement) {
    let page: number = parseInt($event.value);
    if (isNaN(page)) {
      $event.value = this.currentPage.toString();
      return;
    }
    if (page > this.totalPages) {
      $event.value = this.totalPages.toString();
      this.scrollToPage(this.totalPages);
      return;
    }
    this.scrollToPage(page)
  }
}
