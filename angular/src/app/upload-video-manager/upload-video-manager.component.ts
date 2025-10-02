import {Component, OnInit} from '@angular/core';
import {NgForOf, NgIf} from '@angular/common';
import {HttpClient, HttpParams} from '@angular/common/http';
import {getPUrl} from 'layout-navbar';

interface Video {
  id: string;
  name: string;
  type: string;
  size: number;
  createdAt: string;
  path: string;
}

interface ResponseResult {
  message: string;
  status: string;
}

@Component({
  selector: 'app-video-manager',
  imports: [
    NgIf,
    NgForOf
  ],
  standalone: true,
  templateUrl: './upload-video-manager.component.html',
  styleUrl: './upload-video-manager.component.scss'
})
export class UploadVideoManagerComponent implements OnInit {
  CHUNK_SIZE = 1024 * 1024; // 1MB
  uploading = false;
  progressMap: { [key: string]: number } = {};
  selectedFiles: File[] = [];
  videos: Video[] = [];
  loading = true;
  error: string | null = null;

  // API endpoints - cập nhật theo backend controller
  readonly urls = {
    list: getPUrl(`video/list`),
    chunk: getPUrl(`video/chunk`),
    chunkAsync: getPUrl(`video/chunk-async`),
    merge: getPUrl(`video/merge`),
    delete: getPUrl(`video/delete`),
    stream: getPUrl(`video/stream`),
    info: getPUrl(`video/info`),
    delChunk: getPUrl(`video/delete-chunk`)
  };

  constructor(private http: HttpClient) {
  }

  ngOnInit(): void {
    this.fetchVideos();
  }

  async fetchVideos() {
    this.loading = true;
    this.error = null;
    try {
      const response = await this.http.get<Video[]>(this.urls.list).toPromise();
      this.videos = response || [];
    } catch (error) {
      console.error('Error fetching videos:', error);
      this.error = 'Không thể tải danh sách video';
    } finally {
      this.loading = false;
    }
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const files = Array.from(input.files);
      this.processFiles(files);
    }
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    if (event.dataTransfer && event.dataTransfer.files.length > 0) {
      const files = Array.from(event.dataTransfer.files);
      this.processFiles(files);
    }
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
  }

  processFiles(files: File[]) {
    const videoFiles = files.filter(file => file.type.startsWith('video/'));
    if (videoFiles.length === 0) {
      alert('Vui lòng chọn file video hợp lệ!');
      return;
    }

    this.selectedFiles = videoFiles;
    videoFiles.forEach(file => this.uploadFile(file));
  }

  async uploadFile(file: File) {
    if (this.uploading) return;

    this.uploading = true;
    const filename = Date.now() + '-' + file.name;
    const totalChunks = Math.ceil(file.size / this.CHUNK_SIZE);

    this.progressMap[file.name] = 0;

    try {
      // Upload chunks
      for (let i = 0; i < totalChunks; i++) {

        const formData = this.chunkFormData(i, file, filename)

        try {
          await this.http.post(this.urls.chunk, formData, {
            responseType: 'text'
          }).toPromise();

          this.progressMap[file.name] = Math.round(((i + 1) / totalChunks) * 100);
        } catch (chunkError) {
          console.error(`Error uploading chunk ${i}:`, chunkError);
          this.progressMap[file.name] = -1; // Mark as error
          throw chunkError;
        }
      }

      // Merge chunks
      const mergeRequest = {
        filename: filename,
        totalChunks: totalChunks,
        type: file.type
      };

      try {
        const mergeResponse = await this.http.post<ResponseResult>(
          this.urls.merge,
          mergeRequest
        ).toPromise();

        if (mergeResponse?.status === 'SUCCESS') {
          delete this.progressMap[file.name];
          this.fetchVideos(); // Refresh video list
          console.log('Upload completed successfully:', mergeResponse.message);
        } else {
          throw new Error(mergeResponse?.message || 'Merge failed');
        }
      } catch (mergeError) {
        this.progressMap[file.name] = -1;

        // Clean up chunks on merge error
        await this.cleanupChunks(filename, totalChunks);
      }

    } catch (error) {
      console.error('Upload error:', error);
      this.progressMap[file.name] = -1;

      // Clean up chunks on any error
      await this.cleanupChunks(filename, totalChunks);
    } finally {
      this.uploading = false;
    }
  }

  // Method to use async chunk upload (optional)
  async uploadFileAsync(file: File) {
    if (this.uploading) return;

    this.uploading = true;
    const filename = Date.now() + '-' + file.name;
    const totalChunks = Math.ceil(file.size / this.CHUNK_SIZE);

    this.progressMap[file.name] = 0;

    try {
      // Upload chunks asynchronously
      const chunkPromises = [];

      for (let i = 0; i < totalChunks; i++) {
        const formData = this.chunkFormData(i, file, filename)

        const chunkPromise = this.http.post(this.urls.chunkAsync, formData, {
          responseType: 'text'
        }).toPromise();

        chunkPromises.push(chunkPromise);
      }

      // Wait for all chunks to complete
      await Promise.all(chunkPromises);
      this.progressMap[file.name] = 100;

      // Merge chunks
      const mergeRequest = {
        filename: filename,
        totalChunks: totalChunks,
        type: file.type
      };

      const mergeResponse = await this.http.post<ResponseResult>(
        this.urls.merge,
        mergeRequest
      ).toPromise();

      if (mergeResponse?.status === 'SUCCESS') {
        delete this.progressMap[file.name];
        this.fetchVideos();
      } else {
        throw new Error(mergeResponse?.message || 'Merge failed');
      }

    } catch (error) {
      console.error('Async upload error:', error);
      this.progressMap[file.name] = -1;
      await this.cleanupChunks(filename, totalChunks);
    } finally {
      this.uploading = false;
    }
  }

  private async cleanupChunks(filename: string, totalChunks: number) {
    try {
      const params = new HttpParams()
        .set('filename', filename)
        .set('totalChunk', totalChunks.toString());

      const cleanupResponse = await this.http.post<ResponseResult>(
        this.urls.delChunk,
        null,
        {params}
      ).toPromise();

      console.log('Cleanup result:', cleanupResponse?.message);
    } catch (cleanupError) {
      console.error('Cleanup error:', cleanupError);
    }
  }

  getVideoUrl(video: Video) {
    return `${this.urls.stream}?id=${encodeURIComponent(video.id)}`;
  }

  formatFileSize(bytes: number) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  async deleteVideo(id: string) {
    if (!confirm('Bạn có chắc chắn muốn xóa video này?')) return;

    try {
      const params = new HttpParams().set('id', id);

      const response = await this.http.delete<ResponseResult>(
        this.urls.delete,
        {params}
      ).toPromise();

      if (response?.status === 'SUCCESS') {
        console.log('Delete successful:', response.message);
        this.fetchVideos(); // Refresh list
      } else {
        throw new Error(response?.message || 'Delete failed');
      }
    } catch (error) {
      console.error('Delete error:', error);
      alert('Không thể xóa video. Vui lòng thử lại!');
    }
  }

  async getVideoInfo(id: string) {
    try {
      const videoInfo = await this.http.get<Video>(`${this.urls.info}/${id}`).toPromise();
      console.log('Video info:', videoInfo);
      return videoInfo;
    } catch (error) {
      console.error('Error getting video info:', error);
      return null;
    }
  }

  getProgressKeys() {
    return Object.keys(this.progressMap);
  }

  isUploadError(filename: string) {
    return this.progressMap[filename] === -1;
  }

  // Toggle between sync and async upload methods
  useAsyncUpload = false;
  selectedVideoInfo: Video | null = null;

  toggleUploadMethod() {
    this.useAsyncUpload = !this.useAsyncUpload;
    console.log('Upload method:', this.useAsyncUpload ? 'Async' : 'Sync');
  }

  // Override processFiles to use selected method
  processFilesWithMethod(files: File[]) {
    const videoFiles = files.filter(file => file.type.startsWith('video/'));
    if (videoFiles.length === 0) {
      alert('Vui lòng chọn file video hợp lệ!');
      return;
    }

    this.selectedFiles = videoFiles;

    if (this.useAsyncUpload) {
      videoFiles.forEach(file => this.uploadFileAsync(file));
    } else {
      videoFiles.forEach(file => this.uploadFile(file));
    }
  }

  // TrackBy function for better performance
  trackByVideoId(index: number, video: Video): string {
    return video.id;
  }

  // Format date for display
  formatDate(timestamp: string): string {
    const date = new Date(parseInt(timestamp));
    return date.toLocaleDateString('vi-VN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  // Show video info modal
  async showVideoInfo(video: Video) {
    try {
      const detailedInfo = await this.getVideoInfo(video.id);
      this.selectedVideoInfo = detailedInfo || video;
    } catch (error) {
      console.error('Error getting detailed video info:', error);
      this.selectedVideoInfo = video; // Fallback to basic info
    }
  }

  // Close video info modal
  closeVideoInfo() {
    this.selectedVideoInfo = null;
  }

  chunkFormData(index: number, file: File, filename: string): FormData {
    const start: number = index * this.CHUNK_SIZE;
    const end: number = Math.min(start + this.CHUNK_SIZE, file.size);
    const chunk = file.slice(start, end);

    const formData = new FormData();
    formData.append('file', chunk);
    formData.append('filename', filename);
    formData.append('chunkIndex', index.toString());
    return formData;
  }
}
