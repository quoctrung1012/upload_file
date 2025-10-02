import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError, BehaviorSubject } from 'rxjs';
import { catchError, filter, take, switchMap } from 'rxjs/operators';
import { AuthService } from './auth.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  private isRefreshing = false;
  private refreshTokenSubject: BehaviorSubject<any> = new BehaviorSubject<any>(null);

  constructor(private authService: AuthService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const token = this.authService.getToken();

    if (token) {
      req = this.addToken(req, token);
    }

    return next.handle(req).pipe(
      catchError((error: HttpErrorResponse) => {
        // Chỉ xử lý 401 errors và không phải refresh endpoint
        if (error.status === 401 && !req.url.includes('/refresh')) {
          console.log('🚨 Received 401, attempting token refresh...');
          return this.handle401Error(req, next);
        }

        return throwError(() => error);
      })
    );
  }

  private addToken(request: HttpRequest<any>, token: string): HttpRequest<any> {
    return request.clone({
      setHeaders: {
        'Authorization': `Bearer ${token}`
      }
    });
  }

  private handle401Error(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (!this.isRefreshing) {
      this.isRefreshing = true;
      this.refreshTokenSubject.next(null);

      console.log('🔄 Starting token refresh process...');

      return this.authService.forceRefreshToken().pipe(
        switchMap((response: any) => {
          this.isRefreshing = false;

          if (response && response.accessToken) {
            console.log('✅ Token refresh successful, retrying original request');
            this.refreshTokenSubject.next(response.accessToken);

            // Retry original request với token mới
            return next.handle(this.addToken(request, response.accessToken));
          } else {
            console.log('❌ Token refresh failed, logging out');
            this.authService.logout();
            return throwError(() => new Error('Token refresh failed'));
          }
        }),
        catchError((error) => {
          this.isRefreshing = false;
          console.error('❌ Token refresh error:', error);

          // Logout user khi refresh thất bại
          this.authService.logout();
          alert('Your session has expired. Please login again.');
          window.location.href = 'http://localhost:8080/dashboard';

          return throwError(() => error);
        })
      );
    } else {
      // Nếu đang refresh, chờ refresh hoàn thành
      console.log('⏳ Token refresh in progress, waiting...');

      return this.refreshTokenSubject.pipe(
        filter(token => token != null),
        take(1),
        switchMap(jwt => {
          console.log('✅ Using refreshed token for queued request');
          return next.handle(this.addToken(request, jwt));
        })
      );
    }
  }
}
