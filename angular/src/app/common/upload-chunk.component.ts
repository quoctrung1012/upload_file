import {Component} from '@angular/core';
import {FileUploadService} from './file-upload.service';
import {v4 as uuidv4} from 'uuid';
import {NgForOf, NgIf} from '@angular/common';
import {Chunk, ChunkFile} from './common-interface';

@Component({
  selector: 'app-upload',
  imports: [
    NgIf,
    NgForOf
  ],
  template: `
    <div>
      <input type="file" multiple (change)="onFileSelected($event)"/>
      <div *ngIf="uploading">Đang tải lên... {{ progress }}%</div>
    </div>
    <div *ngFor="let upload of uploadQueue" class="file-status">
      <div><strong>{{ upload.file.name }}</strong></div>

      <div *ngIf="upload.status === 'uploading'">
        Uploading: {{ upload.progress }}%
      </div>

      <div *ngIf="upload.status === 'completed'" style="color: green;">
        ✅ Hoàn tất
      </div>

      <div *ngIf="upload.status === 'error'" style="color: red;">
        ❌ Lỗi: {{ upload.errorMessage }}
      </div>
    </div>
  `
})
export class UploadComponent {
  uploadQueue: UploadFileStatus[] = [];
  CHUNK_SIZE = 2 * 1024 * 1024; // 1MB
  uploading = false;
  progress = 0;

  constructor(private uploadService: FileUploadService) {
  }

  async onFileSelected(event: any) {
    const files: FileList = event.target.files;
    if (!files || files.length === 0) return;

    // Khởi tạo danh sách upload
    this.uploadQueue = Array.from(files).map(file => ({
      file,
      filename: uuidv4() + '-' + file.name,
      progress: 0,
      status: 'pending'
    }));

    for (const upload of this.uploadQueue) {
      upload.status = 'uploading';
      try {
        const totalChunks = Math.ceil(upload.file.size / this.CHUNK_SIZE);

        for (let i = 0; i < totalChunks; i++) {
          const chunkExists = await this.uploadService.checkChunkExists(upload.filename, i);
          if (chunkExists) {
            upload.progress = Math.round(((i + 1) / totalChunks) * 100);
            continue;
          }

          const start = i * this.CHUNK_SIZE;
          const end = Math.min(upload.file.size, start + this.CHUNK_SIZE);
          const chunk = upload.file.slice(start, end);

          await this.uploadService.uploadChunk({file: chunk, filename: upload.filename,chunkIndex: i,totalChunks: totalChunks} as Chunk).toPromise();
          upload.progress = Math.round(((i + 1) / totalChunks) * 100);
        }
        await this.uploadService.mergeChunks({filename: upload.filename, totalChunks: totalChunks, type: upload.file.type} as ChunkFile).toPromise();
        upload.status = 'completed';
      } catch (error) {
        upload.status = 'error';
        upload.errorMessage = error instanceof Error ? error.message : String(error);
      }
    }
  }
}

interface UploadFileStatus {
  file: File;
  filename: string;
  progress: number;
  status: 'pending' | 'uploading' | 'completed' | 'error';
  errorMessage?: string;
}
