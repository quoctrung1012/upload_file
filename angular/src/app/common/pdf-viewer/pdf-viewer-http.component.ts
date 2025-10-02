// pdf-viewer-http.component.ts
import { Component, Input, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { catchError, of } from 'rxjs';

@Component({
  selector: 'pdf-viewer-http',
  standalone: true,
  imports: [CommonModule],
  template: `

  `,
  styles: [`
    .pdf-container {
      width: 100%;
      max-width: 1200px;
      margin: 0 auto;
      border: 1px solid #ddd;
      border-radius: 8px;
      overflow: hidden;
      box-shadow: 0 2px 10px rgba(0,0,0,0.1);
    }

    .pdf-header {
      background: #f8f9fa;
      padding: 15px 20px;
      border-bottom: 1px solid #ddd;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .pdf-info {
      display: flex;
      gap: 20px;
      font-size: 14px;
      color: #666;
    }

    .pdf-content {
      background: white;
    }

    .loading-container {
      padding: 40px;
      text-align: center;
      background: white;
    }

    .progress-container {
      width: 100%;
      height: 20px;
      background-color: #f0f0f0;
      border-radius: 10px;
      overflow: hidden;
      margin-bottom: 20px;
    }

    .progress-bar {
      height: 100%;
      background: linear-gradient(90deg, #007bff, #0056b3);
      transition: width 0.3s ease;
      border-radius: 10px;
    }

    .error-container {
      padding: 20px;
      background: white;
    }

    .alert {
      padding: 15px;
      border-radius: 4px;
      margin-bottom: 15px;
    }

    .alert-danger {
      background-color: #f8d7da;
      border: 1px solid #f5c6cb;
      color: #721c24;
    }

    .controls {
      padding: 15px 20px;
      background: #f8f9fa;
      border-top: 1px solid #ddd;
      display: flex;
      gap: 10px;
    }

    .btn {
      padding: 8px 16px;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      font-size: 14px;
      transition: all 0.2s;
    }

    .btn-primary {
      background-color: #007bff;
      color: white;
    }

    .btn-secondary {
      background-color: #6c757d;
      color: white;
    }

    .btn-info {
      background-color: #17a2b8;
      color: white;
    }

    .btn:hover {
      opacity: 0.8;
      transform: translateY(-1px);
    }
  `]
})
export class PdfViewerHttpComponent implements OnInit {
  @Input() pdfUrl: string = '';

  pdfBlobUrl: SafeResourceUrl | null = null;
  loading = false;
  error: string | null = null;
  progress = 0;
  pdfBlob: Blob | null = null;
  pdfInfo: { size: number; type: string } | null = null;

  constructor(
    private http: HttpClient,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit() {
    if (this.pdfUrl) {
      this.loadPdf();
    }
  }

  loadPdf() {
    if (!this.pdfUrl) {
      this.error = 'URL PDF không hợp lệ';
      return;
    }

    this.loading = true;
    this.error = null;
    this.progress = 0;

    // Simulate progress for demo
    const progressInterval = setInterval(() => {
      if (this.progress < 90) {
        this.progress += 10;
      }
    }, 200);

    this.http.get(this.pdfUrl, {
      responseType: 'blob',
      reportProgress: true,
      observe: 'events'
    }).pipe(
      catchError(error => {
        console.error('Lỗi khi tải PDF:', error);
        return of(null);
      })
    ).subscribe({
      next: (event: any) => {
        if (event && event.body) {
          clearInterval(progressInterval);
          this.progress = 100;

          this.pdfBlob = event.body;
          this.pdfInfo = {
            size: event.body.size,
            type: event.body.type || 'application/pdf'
          };

          const url = window.URL.createObjectURL(event.body);
          this.pdfBlobUrl = this.sanitizer.bypassSecurityTrustResourceUrl(url);
          this.loading = false;
        }
      },
      error: (error) => {
        clearInterval(progressInterval);
        this.loading = false;
        this.error = this.getErrorMessage(error);
      }
    });
  }

  retryLoad() {
    this.loadPdf();
  }

  downloadPdf() {
    if (this.pdfBlob) {
      const url = window.URL.createObjectURL(this.pdfBlob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'document.pdf';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    }
  }

  printPdf() {
    if (this.pdfBlobUrl) {
      const printWindow = window.open('', '_blank');
      if (printWindow) {
        printWindow.document.write(`
          <html>
            <head><title>Print PDF</title></head>
            <body style="margin:0;">
              <iframe src="${this.pdfBlobUrl}"
                      width="100%" height="100%"
                      frameborder="0" onload="window.print();">
              </iframe>
            </body>
          </html>
        `);
      }
    }
  }

  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  private getErrorMessage(error: any): string {
    if (error.status === 0) {
      return 'Không thể kết nối đến server. Kiểm tra kết nối mạng.';
    } else if (error.status === 404) {
      return 'File PDF không tồn tại.';
    } else if (error.status === 403) {
      return 'Không có quyền truy cập file PDF.';
    } else if (error.status >= 500) {
      return 'Lỗi server. Vui lòng thử lại sau.';
    } else {
      return `Lỗi khi tải PDF: ${error.message || 'Unknown error'}`;
    }
  }

  // Method để thay đổi PDF từ component cha
  changePdfUrl(newUrl: string) {
    this.pdfUrl = newUrl;
    this.loadPdf();
  }
}
