import {Component, inject, TemplateRef, ViewChild} from '@angular/core';
import {NgbModal, NgbModalConfig} from '@ng-bootstrap/ng-bootstrap';
import {uploadFile} from '../common/common-interface';
import {ApiService} from '../common/api.service';
import {DomSanitizer, SafeResourceUrl} from '@angular/platform-browser';
import {CommonModule} from '@angular/common';
import {NgxDocViewerModule} from 'ngx-doc-viewer';
import {FixedPdfViewerComponent} from 'layout-navbar';

enum FileType {
  IMAGE = 'image',
  PDF = 'pdf',
  OFFICE = 'office',
  TEXT = 'text',
  VIDEO = 'video',
  AUDIO = 'audio',
  UNSUPPORTED = 'unsupported'
}

@Component({
  selector: 'app-upload-file-view',
  standalone: true,
  imports: [CommonModule, FixedPdfViewerComponent, NgxDocViewerModule],
  templateUrl: './upload-file-view.component.html',
  styleUrls: ['./upload-file-view.component.scss']
})
export class UploadFileViewComponent {
  private apiService = inject(ApiService);
  private sanitizer = inject(DomSanitizer);
  @ViewChild('contentView') content!: TemplateRef<any>;
  private modalService = inject(NgbModal);

  urlView: SafeResourceUrl = '';
  previewUrl: string = '';
  fileUrl: string = '';
  currentFileType: FileType = FileType.UNSUPPORTED;
  fileName: string = '';
  fileType: string = '';

  constructor(config: NgbModalConfig) {
    config.backdrop = 'static';
    config.keyboard = true;
  }

  async open(item: uploadFile) {
    this.fileName = item.name;
    this.fileType = item.type;
    this.determineFileType(item.type);
    if (this.currentFileType === FileType.UNSUPPORTED) {
      alert('Loại file này không hỗ trợ xem trước');
      return;
    }

    const fileUrl: string = this.apiService.previewFile(item.id);
    this.fileUrl = '';
    switch (this.currentFileType) {
      case FileType.IMAGE:
        this.urlView = await this.resizeImageWithFit(fileUrl, 400, 600);
        break;
      case FileType.PDF:
        this.urlView = fileUrl;
        break;
      case FileType.OFFICE:
        this.urlView = this.sanitizer.bypassSecurityTrustResourceUrl(fileUrl);
        break;
      default:
        this.urlView = this.sanitizer.bypassSecurityTrustResourceUrl(fileUrl);

    }
    this.modalService.open(this.content, {size: 'xl', scrollable: true});
  }

  private determineFileType(mimeType: string): void {
    if (mimeType.startsWith('image/')) {
      this.currentFileType = FileType.IMAGE;
    } else if (mimeType === 'application/pdf') {
      this.currentFileType = FileType.PDF;
    } else if (
      mimeType === 'application/msword' ||
      mimeType === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' ||
      mimeType === 'application/vnd.ms-excel' ||
      mimeType === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' ||
      mimeType === 'application/vnd.ms-powerpoint' ||
      mimeType === 'application/vnd.openxmlformats-officedocument.presentationml.presentation'
    ) {
      this.currentFileType = FileType.OFFICE;
    } else if (mimeType === 'text/plain') {
      this.currentFileType = FileType.TEXT;
    } else if (mimeType.startsWith('video/')) {  // Sửa từ === 'video/mp4'
      this.currentFileType = FileType.VIDEO;
    } else if (mimeType.startsWith('audio/')) {  // Sửa từ === 'audio/mpeg'
      this.currentFileType = FileType.AUDIO;
    } else {
      this.currentFileType = FileType.UNSUPPORTED;
    }
  }

  get isImage(): boolean {
    return this.currentFileType === FileType.IMAGE;
  }

  get isPdf(): boolean {
    return this.currentFileType === FileType.PDF;
  }

  get isOffice(): boolean {
    return this.currentFileType === FileType.OFFICE;
  }

  get isText(): boolean {
    return this.currentFileType === FileType.TEXT;
  }

  get isVideo(): boolean {
    return this.currentFileType === FileType.VIDEO;
  }

  get isAudio(): boolean {
    return this.currentFileType === FileType.AUDIO;
  }

  onIframeLoad() {
    console.log("load iframe")
  }

  private resizeImageWithFit(imageUrl: string, targetWidth: number, targetHeight: number): Promise<string> {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.crossOrigin = 'anonymous'; // Bắt buộc nếu ảnh từ domain khác

      img.onload = () => {
        const canvas = document.createElement('canvas');
        canvas.width = targetWidth;
        canvas.height = targetHeight;

        const ctx = canvas.getContext('2d');
        if (!ctx) return reject(new Error('Không thể lấy context'));

        // Tính tỉ lệ scale phù hợp
        const ratio = Math.min(targetWidth / img.width, targetHeight / img.height);
        const scaledWidth = img.width * ratio;
        const scaledHeight = img.height * ratio;

        // Tính vị trí để ảnh nằm giữa canvas
        const offsetX = (targetWidth - scaledWidth) / 2;
        const offsetY = (targetHeight - scaledHeight) / 2;

        // Đổ nền trắng
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, targetWidth, targetHeight);

        // Vẽ ảnh đã resize vào giữa canvas
        ctx.drawImage(img, offsetX, offsetY, scaledWidth, scaledHeight);

        const resultDataUrl = canvas.toDataURL('image/jpeg', 1.0); // có thể chỉnh chất lượng 0.0–1.0
        resolve(resultDataUrl);
      };

      img.onerror = () =>
        reject(new Error('Không tải được ảnh. Có thể do CORS hoặc URL sai.'));

      img.src = imageUrl;
    });
  };
}
