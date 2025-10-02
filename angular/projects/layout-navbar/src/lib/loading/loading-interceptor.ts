import {Injectable} from '@angular/core';
import {HttpEvent, HttpHandler, HttpInterceptor, HttpRequest} from '@angular/common/http';
import {Observable} from 'rxjs';
import {finalize} from 'rxjs/operators';
import {HttpClientService} from '../core/http-client.service';

@Injectable()
export class LoadingInterceptor implements HttpInterceptor {
  constructor(private httpClientService: HttpClientService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return next.handle(req).pipe(
      finalize(() => {
        if (this.httpClientService.isLoading) {
          this.httpClientService.hideLoading();
        }
      })
    );
  }
}

