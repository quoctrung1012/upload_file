import {Injectable} from '@angular/core';
import {catchError, Observable, throwError} from 'rxjs';
import {ParamsSearch} from './common-interface';
import {HttpClientService, getPUrl} from 'layout-navbar';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  constructor(private http: HttpClientService) {
  }

  private url = {
    files: getPUrl('files/list'),
    delete: getPUrl('files/delete'),
    upload: getPUrl('files/upload'),
    uploads: getPUrl('files/uploads'),
    preview: getPUrl('files/preview'),
    uploadChunk: getPUrl('files/chunk'),
    mergeChunks: getPUrl('files/merge'),
    checkChunk: getPUrl('files/chunk/check'),
    info: getPUrl('files/info'),

    statusConvert: getPUrl('office/status'),
    convertPdf: getPUrl('office/convert'),
    cleanupPdf: getPUrl('office/cleanup'),

    previewFileDb: getPUrl('files/preview-filedb'),
    previewHandle: getPUrl('files/preview-handle'),
    previewCompare: getPUrl('files/preview-compare'),

  }

  statusConvert(id: string): Observable<any> {
    return this.http.getJSON(`${this.url.statusConvert}/${id}`, {}).pipe(
      catchError(this.errorHandler)
    );
  }

  convertPdf(id: string): Observable<any> {
    return this.http.postJSON(`${this.url.convertPdf}/${id}`, {}).pipe(
      catchError(this.errorHandler)
    );
  }

  cleanupPdf(): Observable<any> {
    return this.http.get(this.url.cleanupPdf).pipe(
      catchError(this.errorHandler)
    );
  }


  getAllFileUploads(params: ParamsSearch): Observable<any> {
    this.http.showLoading();
    return this.http.getJSON(this.url.files, params)
      .pipe(
        catchError(this.errorHandler)
      );
  }

  deleteFile(param: any): Observable<any> {
    return this.http.getJSON(this.url.delete, param)
      .pipe(
        catchError(this.errorHandler)
      );
  }

  uploadFile(param: any): Observable<any> {
    return this.http.post(this.url.upload, param).pipe(
      catchError(this.errorHandler)
    );
  }

  uploadMultiFile(param: any): Observable<any> {
    return this.http.post(this.url.uploads, param).pipe(
      catchError(this.errorHandler)
    );
  }

  // Chunk upload methods
  uploadChunk(param: any): Observable<any> {
    return this.http.post(this.url.uploadChunk, param, {
      responseType: 'text'
    }).pipe(
      catchError(this.errorHandler)
    );
  }

  mergeChunks(param: any): Observable<any> {
    return this.http.post(this.url.mergeChunks, param).pipe(
      catchError(this.errorHandler)
    );
  }


  downloadFile(id: string, fileName: string) {
    const param = {id: id, download: 'true'};
    this.http.downloadFile(this.url.preview, param, fileName);
  }

  previewFile(id: string): string {
    const token = this.http.getCurrentToken();
    let _token = ''
    if (token) {
      _token += `&token=${encodeURIComponent(token)}`;
    }
    return `${this.url.preview}?id=${id}${_token}`;
  }

  getFileInfo(id: string): Observable<any> {
    return this.http.get(`${this.url.info}?id=${id}`).pipe(
      catchError(this.errorHandler)
    );
  }

  errorHandler(error: any) {
    let errorMessage = '';
    if (error.error instanceof ErrorEvent) {
      errorMessage = error.error.message;
    } else {
      errorMessage = `Error Code: ${error.status}\nMessage: ${error.message}`;
    }
    return throwError(errorMessage);
  }
}
