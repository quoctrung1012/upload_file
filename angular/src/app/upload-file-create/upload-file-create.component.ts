import {Component, EventEmitter, inject, Output, TemplateRef, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {NgbModal, NgbModalConfig} from '@ng-bootstrap/ng-bootstrap';
import {ApiService} from '../common/api.service';
import {ToastComponent} from '../common/toast/toast.component';
import {FileSizePipe} from '../common/pipe/file-size.pipe';
import {FileIconDirective} from '../common/directive/file-icon.directive';

interface InfoFile {
  name: string;
  url?: string;
  type: string;
  size: number;
  file?: File;
  uploadProgress?: number;
  status?: 'pending' | 'uploading' | 'success' | 'error';
  errorMessage?: string;
}

interface ChunkInfo {
  chunk: Blob;
  index: number;
  totalChunks: number;
  filename: string;
}

@Component({
  selector: 'app-upload-file-create',
  standalone: true,
  imports: [CommonModule, ToastComponent, FileSizePipe, FileIconDirective],
  templateUrl: './upload-file-create.component.html',
  styleUrls: ['./upload-file-create.component.scss']
})
export class UploadFileCreateComponent {
  @ViewChild('contentCreate') content!: TemplateRef<any>;
  @ViewChild('toastTpl') toastTpl!: ToastComponent;
  @Output() onLoad = new EventEmitter<any>();

  private modalService = inject(NgbModal);
  private apiService = inject(ApiService);

  imageUrls: InfoFile[] = [];
  fileDocumentUrl: InfoFile[] = [];
  isDragging = false;
  files: InfoFile[] = [];
  isUploading = false;
  overallProgress = 0;

  // File size limits
  maxFileSize = 500 * 1024 * 1024; // 500MB max
  chunkThreshold = 10 * 1024 * 1024; // 10MB - files larger than this will be chunked
  chunkSize = 2 * 1024 * 1024; // 2MB per chunk
  // Supported file types with more comprehensive list
  supportedTypes = [
    // Images
    'image/jpeg',
    'image/jpg',
    'image/png',
    'image/gif',
    'image/webp',
    'image/bmp',
    'image/tiff',

    // PDFs
    'application/pdf',

    // Microsoft Office
    'application/msword', // .doc
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document', // .docx
    'application/vnd.ms-excel', // .xls
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', // .xlsx
    'application/vnd.ms-powerpoint', // .ppt
    'application/vnd.openxmlformats-officedocument.presentationml.presentation', // .pptx

    // Text files
    'text/plain',
    'text/csv',
    'text/html',
    'text/xml',
    'application/json',

    // Archives
    'application/zip',
    'application/x-rar-compressed',
    'application/x-7z-compressed',

    // Other common formats
    'application/rtf',

    "application/x-zip-compressed",
    "application/zip",
    "application/x-7z-compressed",
    "application/x-msdownload", // .exe
    "application/x-ms-installer", // .msi

    // Video
    "video/mp4",
    "video/x-msvideo",
    "video/quicktime",
    "video/x-ms-wmv",
    "video/x-flv",
    "video/webm",
    "video/x-matroska",
    // Audio
    "audio/mpeg",
    "audio/wav",
    "audio/flac",
    "audio/aac",
    "audio/ogg",
    "audio/mp4"



  ];

  constructor(config: NgbModalConfig) {
    config.backdrop = 'static';
    config.keyboard = true;
  }

  open() {
    this.modalService.open(this.content, {size: 'lg', scrollable: true});
  }

  close(reason?: any) {
    this.modalService.dismissAll(reason);
    if (reason === 'Save click' || reason === 'Cross click') {
      this.reset();
    }
  }

  async save() {
    if (this.files.length === 0) {
      this.toastTpl.showDanger('Vui lòng chọn ít nhất một file');
      return;
    }

    this.isUploading = true;
    this.overallProgress = 0;

    // KHỞI TẠO STATUS cho tất cả files
    this.files.forEach(file => {
      file.status = 'pending';
      file.uploadProgress = 0;
    });

    try {
      // Separate files by size
      const smallFiles = this.files.filter(f => f.size <= this.chunkThreshold);
      const largeFiles = this.files.filter(f => f.size > this.chunkThreshold);

      let successCount = 0;
      let errorCount = 0;

      // Upload small files in batch
      if (smallFiles.length > 0) {
        try {
          await this.uploadSmallFiles(smallFiles);
          successCount += smallFiles.length;
        } catch (error) {
          console.error('Error uploading small files:', error);
          errorCount += smallFiles.length;
        }
      }

      // Upload large files individually with chunking
      for (const file of largeFiles) {
        try {
          await this.uploadLargeFile(file);
          successCount++;
        } catch (error) {
          console.error(`Error uploading large file ${file.name}:`, error);
          file.status = 'error';
          file.errorMessage = 'Upload failed';
          file.uploadProgress = 0;
          this.updateOverallProgress();
          errorCount++;
        }
      }

      // Show results
      if (successCount > 0) {
        this.toastTpl.showSuccess(`Tải lên thành công ${successCount} file!`);
      }

      if (errorCount > 0) {
        this.toastTpl.showDanger(`Có ${errorCount} file tải lên thất bại!`);
      }

      if (successCount > 0) {
        this.onLoad.emit();
        this.close('Save click');
      }

    } catch (error) {
      console.error('Upload error:', error);
      this.toastTpl.showDanger('Có lỗi xảy ra trong quá trình tải lên');
    } finally {
      this.isUploading = false;
      this.overallProgress = 100; // Hoặc reset về 0 tùy theo UX mong muốn
    }
  }

  private async uploadSmallFiles(files: InfoFile[]): Promise<void> {
    const formData = new FormData();
    files.forEach(fileInfo => {
      if (fileInfo.file) {
        formData.append('files', fileInfo.file);
        fileInfo.status = 'uploading';
        fileInfo.uploadProgress = 50; // Set initial progress
      }
    });

    this.updateOverallProgress();

    return new Promise((resolve, reject) => {
      this.apiService.uploadMultiFile(formData).subscribe({
        next: (result) => {
          if (result.type !== 'ERROR') {
            files.forEach(file => {
              file.status = 'success';
              file.uploadProgress = 100;
            });
            this.updateOverallProgress();
            resolve();
          } else {
            files.forEach(file => {
              file.status = 'error';
              file.errorMessage = result.message;
              file.uploadProgress = 0;
            });
            this.updateOverallProgress();
            reject(new Error(result.message));
          }
        },
        error: (error) => {
          files.forEach(file => {
            file.status = 'error';
            file.errorMessage = 'Upload failed';
            file.uploadProgress = 0;
          });
          this.updateOverallProgress();
          reject(error);
        }
      });
    });
  }

  private async uploadLargeFile(fileInfo: InfoFile): Promise<void> {
    if (!fileInfo.file) {
      throw new Error('File is missing');
    }

    const file = fileInfo.file;
    const totalChunks = Math.ceil(file.size / this.chunkSize);

    fileInfo.status = 'uploading';
    fileInfo.uploadProgress = 0;

    try {
      // Upload chunks sequentially để track progress đúng
      let uploadedChunks = 0;

      for (let i = 0; i < totalChunks; i++) {
        const start = i * this.chunkSize;
        const end = Math.min(start + this.chunkSize, file.size);
        const chunk = file.slice(start, end);

        await this.uploadChunk(chunk, file.name, i, totalChunks, file.size);

        uploadedChunks++;

        // CẬP NHẬT PROGRESS cho từng chunk
        fileInfo.uploadProgress = Math.floor((uploadedChunks / totalChunks) * 90); // 90% cho upload chunks

        // CẬP NHẬT OVERALL PROGRESS
        this.updateOverallProgress();
      }

      // Update progress cho merge step
      fileInfo.uploadProgress = 90;
      this.updateOverallProgress();

      // Merge chunks
      await this.mergeChunks(file.name, totalChunks, file.type, file.size);

      fileInfo.status = 'success';
      fileInfo.uploadProgress = 100;
      this.updateOverallProgress();

    } catch (error) {
      fileInfo.status = 'error';
      fileInfo.errorMessage = 'Chunk upload failed';
      throw error;
    }
  }

  private updateOverallProgress(): void {
    const totalFiles = this.files.length;
    if (totalFiles === 0) {
      this.overallProgress = 0;
      return;
    }

    let totalProgress = 0;

    this.files.forEach(file => {
      switch (file.status) {
        case 'success':
          totalProgress += 100;
          break;
        case 'uploading':
          totalProgress += (file.uploadProgress || 0);
          break;
        case 'pending':
          totalProgress += 0;
          break;
        case 'error':
          totalProgress += 0;
          break;
      }
    });

    this.overallProgress = Math.floor(totalProgress / totalFiles);
  }

  private uploadChunk(chunk: Blob, filename: string, chunkIndex: number, totalChunks: number, fileSize: number): Promise<any> {
    const formData = new FormData();
    formData.append('file', chunk);
    formData.append('filename', filename);
    formData.append('chunkIndex', chunkIndex.toString());
    formData.append('totalChunks', totalChunks.toString());
    formData.append('totalSize', fileSize.toString());

    return new Promise((resolve, reject) => {
      this.apiService.uploadChunk(formData).subscribe({
        next: (result) => {
          if (result.error) {
            reject(new Error(result.error));
          } else {
            resolve(result);
          }
        },
        error: (error) => reject(error)
      });
    });
  }

  private mergeChunks(filename: string, totalChunks: number, fileType: string, totalSize: number): Promise<any> {
    const mergeRequest = {
      filename: filename,
      totalChunks: totalChunks,
      type: fileType,
      totalSize: totalSize
    };

    return new Promise((resolve, reject) => {
      this.apiService.mergeChunks(mergeRequest).subscribe({
        next: (result) => {
          if (result.error) {
            reject(new Error(result.error));
          } else {
            resolve(result);
          }
        },
        error: (error) => reject(error)
      });
    });
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    this.isDragging = true;
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    this.isDragging = false;
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    this.isDragging = false;

    if (event.dataTransfer?.files) {
      this.handleFiles(event.dataTransfer.files);
    }
  }

  onFilesSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) {
      this.handleFiles(input.files);
    }
  }

  removeFile(index: number, isImage: boolean) {
    if (isImage) {
      const fileInfo = this.imageUrls[index];
      this.imageUrls.splice(index, 1);
      this.files = this.files.filter(f => f.name !== fileInfo.name);
    } else {
      const fileInfo = this.fileDocumentUrl[index];
      this.fileDocumentUrl.splice(index, 1);
      this.files = this.files.filter(f => f.name !== fileInfo.name);
    }
  }

  reset() {
    this.imageUrls = [];
    this.fileDocumentUrl = [];
    this.files = [];
    this.isUploading = false;
    this.overallProgress = 0;
  }

  private handleFiles(fileList: FileList) {
    const newFiles = Array.from(fileList);

    // Filter and validate files
    const validFiles = newFiles.filter(file => {
      // Check if file already exists
      const exists = this.files.some(f => f.name === file.name && f.size === file.size);
      if (exists) {
        this.toastTpl.showWarning(`File ${file.name} đã được chọn`);
        return false;
      }

      // Check file size
      if (file.size > this.maxFileSize) {
        this.toastTpl.showDanger(`File ${file.name} vượt quá kích thước cho phép (${this.maxFileSize / (1024 * 1024)}MB)`);
        return false;
      }

      // Check empty file
      if (file.size === 0) {
        this.toastTpl.showDanger(`File ${file.name} rỗng`);
        return false;
      }
      // Check file type
      if (!this.supportedTypes.includes(file.type)) {
        this.toastTpl.showDanger(`Loại file ${file.name} không được hỗ trợ`);
        return false;
      }

      return true;
    });

    // Add valid files
    validFiles.forEach(file => {
      const fileInfo: InfoFile = {
        name: file.name,
        type: file.type,
        size: file.size,
        file: file,
        status: 'pending',
        uploadProgress: 0
      };

      this.files.push(fileInfo);

      // Handle preview based on file type
      if (file.type.startsWith('image/')) {
        this.createImagePreview(fileInfo);
      } else {
        this.fileDocumentUrl.push(fileInfo);
      }
    });

    if (validFiles.length > 0) {
      this.toastTpl.showSuccess(`Đã thêm ${validFiles.length} file`);
    }
  }

  private createImagePreview(fileInfo: InfoFile) {
    if (!fileInfo.file) return;

    const reader = new FileReader();
    reader.onload = (e: any) => {
      fileInfo.url = e.target.result;
      this.imageUrls.push(fileInfo);
    };
    reader.onerror = () => {
      this.toastTpl.showDanger(`Không thể tạo preview cho ${fileInfo.name}`);
      this.fileDocumentUrl.push(fileInfo); // Add to document list instead
    };
    reader.readAsDataURL(fileInfo.file);
  }

  getFileTypeIcon(type: string): string {
    if (type.startsWith('image/')) return 'fa-file-image';
    if (type === 'application/pdf') return 'fa-file-pdf';
    if (type.includes('word') || type.includes('document')) return 'fa-file-word';
    if (type.includes('excel') || type.includes('sheet')) return 'fa-file-excel';
    if (type.includes('powerpoint') || type.includes('presentation')) return 'fa-file-powerpoint';
    if (type.startsWith('text/')) return 'fa-file-alt';
    if (type.includes('zip') || type.includes('rar') || type.includes('7z')) return 'fa-file-archive';
    return 'fa-file';
  }

  getStatusIcon(status?: string): string {
    switch (status) {
      case 'uploading':
        return 'fa-spinner fa-spin';
      case 'success':
        return 'fa-check-circle text-success';
      case 'error':
        return 'fa-exclamation-circle text-danger';
      default:
        return 'fa-clock text-muted';
    }
  }

  isLargeFile(size: number): boolean {
    return size > this.chunkThreshold;
  }

  getTotalSize(): number {
    return this.files.reduce((total, file) => total + file.size, 0);
  }

  /**
   * Rút gọn tên file nếu quá dài
   */
  getDisplayFileName(fileName: string, maxLength: number = 25): string {
    if (fileName.length <= maxLength) {
      return fileName;
    }

    const extension = this.getFileExtension(fileName);
    const nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
    const availableLength = maxLength - extension.length - 4; // -4 cho "..." và "."

    if (availableLength <= 0) {
      return fileName.substring(0, maxLength - 3) + '...';
    }

    return nameWithoutExt.substring(0, availableLength) + '...' + extension;
  }

  /**
   * Lấy phần mở rộng file
   */
  getFileExtension(fileName: string): string {
    const lastDot = fileName.lastIndexOf('.');
    return lastDot !== -1 ? fileName.substring(lastDot) : '';
  }

  /**
   * Kiểm tra tên file có dài không
   */
  isLongFileName(fileName: string): boolean {
    return fileName.length > 20;
  }

  /**
   * Lấy class CSS cho hiển thị tên file
   */
  getFileNameClass(fileName: string): string {
    return this.isLongFileName(fileName) ? 'long-name' : 'short-name';
  }

  /**
   * Tạo tooltip text cho tên file
   */
  getFileTooltip(file: any): string {
    const sizeText = this.formatFileSize(file.size);
    return `${file.name} (${sizeText})`;
  }

  /**
   * Format file size
   */
  private formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 Bytes';

    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));

    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  /**
   * Lấy màu sắc theo loại file
   */
  getFileTypeColor(fileName: string): string {
    const extension = this.getFileExtension(fileName).toLowerCase();

    const colorMap: { [key: string]: string } = {
      '.pdf': '#dc3545',
      '.doc': '#0d6efd',
      '.docx': '#0d6efd',
      '.xls': '#198754',
      '.xlsx': '#198754',
      '.ppt': '#fd7e14',
      '.pptx': '#fd7e14',
      '.zip': '#6f42c1',
      '.rar': '#6f42c1',
      '.7z': '#6f42c1',
      '.jpg': '#20c997',
      '.jpeg': '#20c997',
      '.png': '#20c997',
      '.gif': '#20c997',
      '.webp': '#20c997',
      '.txt': '#6c757d',
      '.csv': '#6c757d',
      '.json': '#6c757d'
    };

    return colorMap[extension] || '#6c757d';
  }

  /**
   * Tạo style inline cho file extension badge
   */
  getExtensionBadgeStyle(fileName: string): any {
    const color = this.getFileTypeColor(fileName);
    return {
      'background-color': color + '20', // 20% opacity
      'color': color,
      'border-color': color + '40' // 40% opacity
    };
  }
}
