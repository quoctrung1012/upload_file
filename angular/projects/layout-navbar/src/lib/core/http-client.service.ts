import {Injectable} from '@angular/core';
import {HttpClient, HttpHandler, HttpHeaders, HttpParams, HttpUrlEncodingCodec} from '@angular/common/http';
import {BehaviorSubject, catchError} from 'rxjs';
import {tap} from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class HttpClientService extends HttpClient {
  headers: any;

  // Loading state management
  private loadingSubject = new BehaviorSubject<boolean>(false);
  private messageSubject = new BehaviorSubject<string>('');

  public loading$ = this.loadingSubject.asObservable();
  public message$ = this.messageSubject.asObservable();

  constructor(handler: HttpHandler) {
    super(handler);
    this.headers = new HttpHeaders({'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'});
  }

  getCurrentToken(token?: string): string | null {
    let _token = !!token ? token : 'authToken';
    return localStorage.getItem(_token) || sessionStorage.getItem(_token);
  }

  showLoading(message: string = 'Đang tải dữ liệu...') {
    this.messageSubject.next(message);
    this.loadingSubject.next(true);
  }

  hideLoading() {
    this.loadingSubject.next(false);
    this.messageSubject.next('');
  }

  get isLoading(): boolean {
    return this.loadingSubject.value;
  }

  convertToHttpParams(obj: { [x: string]: string | number | boolean; }): HttpParams {
    if (!obj) {
      return new HttpParams();
    }
    return Object.getOwnPropertyNames(obj).reduce((p, key) => p.set(key, obj[key] == undefined ? '' : obj[key]), new HttpParams({encoder: new HttpUrlEncodingCodec()}));
  }

  getJSON(url: string, body: any) {
    return this.get(url, {params: this.convertToHttpParams(body), headers: this.headers});
  }

  postJSON(url: string, body: any) {
    return this.post(url, this.convertToHttpParams(body), {headers: this.headers});
  }

  putJSON(url: string, body: any) {
    return this.put(url, this.convertToHttpParams(body), {headers: this.headers});
  }

  deleteJSON(url: string, body: any) {
    return this.delete(url, {params: this.convertToHttpParams(body), headers: this.headers});
  }

  convertToFormData(obj: { [x: string]: string | Blob; }) {
    let formData = new FormData();
    if (!obj) {
      return formData;
    }
    Object.keys(obj).forEach(k => {
      formData.append(k, obj[k] == undefined ? '' : obj[k]);
    });
    return formData;
  }

  downloadFile(url: string, params: any, fileName: string, method: 'GET' | 'POST' = 'GET'): void {
    const headers = new HttpHeaders({ 'Accept': 'application/octet-stream' });

    const request = method === 'GET'
      ? this.get(url, { params: this.convertToHttpParams(params), responseType: 'blob', headers })
      : this.post(url, this.convertToHttpParams(params), { responseType: 'blob', headers });

    request.subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
      },
      error: (error) => {
        console.error('Error downloading file:', error);
      }
    });
  }

  getUrl(relativePath: any) {
    return relativePath;
  }
}
