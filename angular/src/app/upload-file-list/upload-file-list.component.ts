import {Component, inject, OnInit, ViewChild} from '@angular/core';
import {TranslateModule} from '@ngx-translate/core';
import {ApiService} from '../common/api.service';
import {ParamsSearch, uploadFile} from '../common/common-interface';
import {CommonModule, DatePipe, NgClass} from '@angular/common';
import {FileSizePipe} from '../common/pipe/file-size.pipe';
import {FileIconPipe} from '../common/pipe/file-icon.pipe';
import {ToastComponent} from '../common/toast/toast.component';
import {UploadFileCreateComponent} from '../upload-file-create/upload-file-create.component';
import {UploadFileViewComponent} from '../upload-file-view/upload-file-view.component';

import {Router} from '@angular/router';
import {PaginationComponent} from 'layout-navbar';

@Component({
  selector: 'app-upload-file-list',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    DatePipe,
    FileSizePipe,
    FileIconPipe,
    NgClass,
    ToastComponent,
    UploadFileCreateComponent,
    UploadFileViewComponent,
    PaginationComponent
  ],
  templateUrl: './upload-file-list.component.html',
  styleUrls: ['./upload-file-list.component.scss']
})
export class UploadFileListComponent implements OnInit {
  uploadFiles: uploadFile[] = [];
  apiService = inject(ApiService);
  @ViewChild('pagination') pagination!: PaginationComponent;
  @ViewChild('toastTpl') toastTpl!: ToastComponent;
  @ViewChild('createFileTpl') createFileTpl!: UploadFileCreateComponent;
  @ViewChild('viewFileTpl') viewFileTpl!: UploadFileViewComponent;
  pageNumber: number = 0;
  pageSize: number = 10;
  total: number = 0;
  paramSearch: ParamsSearch = {search: '', page: 0, size: this.pageSize};
  typePreview: number = 0;

  constructor(private router: Router) {
  }

  ngOnInit(): void {
    this.getAllList(this.paramSearch);
  }

  getAllList(params: ParamsSearch) {
    this.apiService.getAllFileUploads(params).subscribe(result => {
      if (result.type !== 'ERROR') {
        this.uploadFiles = result.items;
        this.pageNumber = result.pageNumber;
        this.total = result.totalElements;

        // Cập nhật pagination component
        this.pagination.onChangePage(this.pageNumber)

      } else {
        this.uploadFiles = [];
        this.pageNumber = 0;
        this.total = 0;
      }
    });
  }

  onUpload() {
    this.createFileTpl.open();
  }

  onVideo() {
    this.router.navigate(['/', 'video-list']);
  }

  onView(item: uploadFile) {
    this.viewFileTpl.open(item);
  }

  onDownload(item: uploadFile) {
    const fileName = item.name;
    this.apiService.downloadFile(item.id, fileName)
  }

  private adjustPage(currentTotal: number, pageSize: number, currentPage: number): number {
    const newTotal = currentTotal - 1;
    const totalPages = Math.ceil(newTotal / pageSize);
    if (currentPage >= totalPages && totalPages > 0) {
      return totalPages - 1;
    }
    return currentPage;
  }

// Cập nhật function onDelete
  onDelete(item: uploadFile) {
    if (confirm(`Bạn có chắc chắn muốn xóa file ${item.name}?`)) {
      this.apiService.deleteFile({id: item.id}).subscribe(result => {
        if (result.type !== 'ERROR') {
          this.toastTpl.showSuccess(`Xóa file ${item.name} thành công!`);
          this.paramSearch.page = this.adjustPage(this.total, this.pageSize, this.pageNumber);
          this.getAllList(this.paramSearch);
        } else {
          this.toastTpl.showDanger(`Xóa file ${item.name} thất bại!`);
        }
      });
    }
  }

  onChange(pageNumber: number) {
    this.paramSearch.page = pageNumber;
    this.getAllList(this.paramSearch);
  }
}
