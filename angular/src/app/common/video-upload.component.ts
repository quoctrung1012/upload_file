import {Component, OnInit} from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {getPUrl} from 'layout-navbar';
import {DecimalPipe, NgForOf} from '@angular/common';

interface VideoMeta {
  id: string;
  name: string;
  type: string;
  size: number;
  path: string;
}

@Component({
  selector: 'app-video-upload',
  template: `
    <h2>📤 Tải lên Video</h2>
    <input type="file" multiple (change)="onFileSelected($event)"/>

    <div *ngFor="let file of selectedFiles" class="upload-item">
      <p>{{ file.name }} - {{ progressMap[file.name] || 0 }}%</p>
      <progress [value]="progressMap[file.name] || 0" max="100"></progress>
    </div>

    <hr/>

    <h2>📺 Danh sách Video đã Upload</h2>
    <div *ngFor="let video of videos" class="video-card">
      <h4>{{ video.name }}</h4>
      <p>Loại: {{ video.type }} | Dung lượng: {{ video.size | number }} bytes</p>
      <video width="400" height="240"
             controls
             (error)="onVideoError(video.name)"
      >
        <source [src]="getVideoUrl(video)" type="video/mp4"/>
        Trình duyệt không hỗ trợ phát video.
      </video>
      <br/>
      <button (click)="deleteVideo(video.id)">🗑 Xóa</button>
    </div>
  `,
  imports: [
    DecimalPipe,
    NgForOf
  ],
  styles: `
    .upload-item {
      margin: 10px 0;
    }

    .video-card {
      border: 1px solid #ddd;
      padding: 10px;
      margin-bottom: 20px;
      border-radius: 8px;
      background-color: #f9f9f9;
    }

    video {
      display: block;
      margin-top: 10px;
    }

    button {
      margin-top: 5px;
      padding: 5px 12px;
      background-color: #d9534f;
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
    }

    button:hover {
      background-color: #c9302c;
    }
  `
})
export class VideoUploadComponent implements OnInit {
  CHUNK_SIZE = 1024 * 1024; // 1MB
  uploading = false;
  progressMap: Record<string, number> = {};
  selectedFiles: File[] = [];
  videos: VideoMeta[] = [];

  private url = {
    list: getPUrl('video/list'),
    chunk: getPUrl('video/chunk'),
    merge: getPUrl('video/merge'),
    delete: getPUrl('video/delete'),
    stream: getPUrl('video/stream'),
  }

  constructor(private http: HttpClient) {
  }

  ngOnInit() {
    this.fetchVideos();
  }

  fetchVideos() {
    this.http.get<VideoMeta[]>(this.url.list).subscribe(videos => this.videos = videos);
  }

  onFileSelected(event: any) {
    const files: FileList = event.target.files;
    this.selectedFiles = Array.from(files);
    this.selectedFiles.forEach(file => this.uploadFile(file));
  }

  async uploadFile(file: File) {
    this.uploading = true;
    const filename = Date.now() + '-' + file.name;
    const totalChunks = Math.ceil(file.size / this.CHUNK_SIZE);
    for (let i = 0; i < totalChunks; i++) {
      const start = i * this.CHUNK_SIZE;
      const end = Math.min(start + this.CHUNK_SIZE, file.size);
      const chunk = file.slice(start, end);

      const form = new FormData();
      form.append('file', chunk);
      form.append('filename', filename);
      form.append('chunkIndex', i.toString());

      await this.http.post(this.url.chunk, form, {responseType: 'text' as 'json'}).toPromise();
      this.progressMap[file.name] = Math.round(((i + 1) / totalChunks) * 100);
    }

    await this.http.post(this.url.merge, {
      filename,
      totalChunks,
      type: file.type
    }).toPromise();

    this.uploading = false;
  }

  getVideoUrl(item: VideoMeta): string {
    return `${this.url.stream}?id=${encodeURIComponent(item.id)}`;
  }

  deleteVideo(id: string) {
    this.http.delete(`${this.url.delete}?id=${id}`).subscribe(() => this.fetchVideos());
  }

  onVideoError(filename: string) {
    console.error(`Không thể phát video: ${filename}. File có thể chưa tồn tại hoặc lỗi stream.`);
  }
}
