import {Directive, ElementRef, Input, OnInit, Renderer2} from '@angular/core';

interface FileIconConfig {
  filetype: string;
  type: 1 | 2;
}

@Directive({
  selector: '[fileIcon]',
  standalone: true
})
export class FileIconDirective implements OnInit {
  @Input() fileIcon!: FileIconConfig;

  constructor(
    private el: ElementRef,
    private renderer: Renderer2
  ) {
  }

  ngOnInit() {
    this.applyIcon();
  }

  private applyIcon() {
    if (!this.fileIcon) return;

    const {filetype, type} = this.fileIcon;

    if (type === 1) {
      // Type 1: Sử dụng phương pháp cũ (class name)
      const iconClass = this.getFileIconClass(filetype);
      if (iconClass) {
        this.renderer.addClass(this.el.nativeElement, iconClass);
      }
    } else if (type === 2) {
      // Type 2: Sử dụng FontAwesome icons với màu sắc tự động
      const {iconClass, color} = this.getFileIconWithColor(filetype);

      // Xóa tất cả class cũ trước khi thêm mới
      this.clearExistingClasses();

      // Thêm title
      this.renderer.setAttribute(this.el.nativeElement, 'title', filetype)
      // Thêm FontAwesome classes
      this.renderer.addClass(this.el.nativeElement, 'fas');
      this.renderer.addClass(this.el.nativeElement, iconClass);

      // Áp dụng màu sắc
      this.renderer.setStyle(this.el.nativeElement, 'color', color);
    }
  }

  private clearExistingClasses() {
    const element = this.el.nativeElement;
    // Lấy danh sách class hiện tại và xóa các class FontAwesome
    const classList = Array.from(element.classList);
    classList.forEach((className: any) => {
      if (className.startsWith('fa-') || className === 'fas' || className === 'far' || className === 'fab') {
        this.renderer.removeClass(element, className);
      }
    });
    // Xóa style color cũ
    this.renderer.removeStyle(element, 'color');
  }

  // Phương pháp cũ (Type 1) - trả về class name đơn giản
  private getFileIconClass(filetype: string): string {
    switch (filetype) {
      case 'audio/ogg':
      case 'video/x-theora+ogg':
      case 'video/mpeg':
      case 'video/quicktime':
      case 'video/x-msvideo':
      case 'audio/x-wav':
        return 'video';
      case 'text/xml':
        return 'xml';
      case 'text/csv':
        return 'csv';
      case 'image/vnd.adobe.photoshop':
        return 'psd';
      case 'image/tiff':
        return 'tiff';
      case 'application/vnd.rar':
        return 'rar';
      case 'application/vnd.ms-powerpoint':
      case 'application/vnd.openxmlformats-officedocument.presentationml.presentation':
        return 'ppt';
      case 'image/png':
        return 'png';
      case 'image/gif':
        return 'gif';
      case 'application/x-msdownload':
        return 'exe';
      case 'application/postscript':
        return 'ai';
      case 'audio/aac':
        return 'aac';
      case 'application/vnd.ms-excel':
      case 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet':
        return 'xls';
      case 'application/pdf':
        return 'pdf';
      case 'video/mp4':
        return 'mp4';
      case 'application/zip':
      case 'application/x-zip-compressed':
        return 'zip';
      case 'text/rtf':
        return 'rtf';
      case 'text/plain':
        return 'txt';
      case 'image/jpeg':
        return 'jpg';
      case 'image/webp':
        return 'webp';
      case 'application/msword':
      case 'application/vnd.openxmlformats-officedocument.wordprocessingml.document':
        return 'doc';
      case 'application/json':
        return 'json';
      case 'audio/mpeg':
        return 'mp3';
      case 'text/html':
        return 'html';
      case 'text/css':
        return 'css';
      case 'text/javascript':
        return 'js';
      case 'image/svg+xml':
        return 'svg';
      default:
        return '';
    }
  }

  // Phương pháp mới (Type 2) - trả về FontAwesome icon với màu sắc
  private getFileIconWithColor(filetype: string): { iconClass: string; color: string } {
    switch (filetype) {
      // Images
      case 'image/tiff':
      case 'image/png':
      case 'image/gif':
      case 'image/jpeg':
      case 'image/webp':
      case 'image/svg+xml':
        return {
          iconClass: 'fa-file-image',
          color: '#20c997' // teal
        };
      // Audio files
      case 'audio/ogg':
      case 'audio/x-wav':
      case 'audio/aac':
      case 'audio/mpeg':
        return {
          iconClass: 'fa-file-audio',
          color: '#fd7e14' // orange
        };
      //Videos
      case 'video/x-theora+ogg':
      case 'video/mpeg':
      case 'video/quicktime':
      case 'video/x-msvideo':
      case 'video/mp4':
        return {
          iconClass: 'fa-file-video',
          color: '#e83e8c' // pink
        }
      // PDF
      case 'application/pdf':
        return {
          iconClass: 'fa-file-pdf',
          color: '#dc3545' // red
        };

      // Microsoft Word
      case 'application/msword':
      case 'application/vnd.openxmlformats-officedocument.wordprocessingml.document':
        return {
          iconClass: 'fa-file-word',
          color: '#0d6efd' // blue
        };

      // Microsoft Excel
      case 'application/vnd.ms-excel':
      case 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet':
        return {
          iconClass: 'fa-file-excel',
          color: '#198754' // green
        };

      // Microsoft PowerPoint
      case 'application/vnd.ms-powerpoint':
      case 'application/vnd.openxmlformats-officedocument.presentationml.presentation':
        return {
          iconClass: 'fa-file-powerpoint',
          color: '#fd7e14' // orange
        };

      // Archives
      case 'application/zip':
      case 'application/x-zip-compressed':
      case 'application/vnd.rar':
      case 'application/x-rar-compressed':
      case 'application/x-7z-compressed':
        return {
          iconClass: 'fa-file-archive',
          color: '#6f42c1' // purple
        };

      // Text files
      case 'text/plain':
      case 'text/csv':
      case 'text/html':
      case 'text/xml':
      case 'text/css':
      case 'text/javascript':
      case 'application/json':
        return {
          iconClass: 'fa-file-alt',
          color: '#6c757d' // gray
        };

      // Code files
      case 'application/javascript':
      case 'text/typescript':
        return {
          iconClass: 'fa-file-code',
          color: '#ffc107' // yellow
        };

      // RTF
      case 'text/rtf':
      case 'application/rtf':
        return {
          iconClass: 'fa-file-alt',
          color: '#17a2b8' // cyan
        };

      // Executable files
      case 'application/x-msdownload':
      case 'application/x-ms-installer':
        return {
          iconClass: 'fa-file',
          color: '#dc3545' // red
        };

      // Adobe files
      case 'image/vnd.adobe.photoshop':
        return {
          iconClass: 'fa-file-image',
          color: '#0066cc' // adobe blue
        };

      case 'application/postscript':
        return {
          iconClass: 'fa-file-image',
          color: '#ff6600' // adobe orange
        };

      // Default
      default:
        return {
          iconClass: 'fa-file',
          color: '#6c757d' // gray
        };
    }
  }
}
