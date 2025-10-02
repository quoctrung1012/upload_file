import {Injectable} from '@angular/core';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {HttpClient} from '@angular/common/http';
import {catchError, tap} from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private tokenSubject = new BehaviorSubject<string | null>(null);
  private userSubject = new BehaviorSubject<any>(null);
  private refreshTokenSubject = new BehaviorSubject<string | null>(null);
  private refreshTokenTimer: any;

  public token$ = this.tokenSubject.asObservable();
  public user$ = this.userSubject.asObservable();

  private readonly API_BASE = 'http://localhost:8080/api/auth';

  constructor(private http: HttpClient) {

    // Kiểm tra token từ localStorage khi khởi tạo
    const savedToken = localStorage.getItem('authToken');
    const savedRefreshToken = localStorage.getItem('refreshToken');
    const savedUser = localStorage.getItem('userInfo');

    if (savedToken) {
      this.tokenSubject.next(savedToken);
    }

    if (savedRefreshToken) {
      this.refreshTokenSubject.next(savedRefreshToken);
    }

    if (savedUser) {
      this.userSubject.next(JSON.parse(savedUser));
    }

    // Bắt đầu auto-check timer nếu có token
    if (savedToken) {
      this.startTokenExpirationCheck();
    }
  }

  setTokenFromUrl(): boolean {
    const urlParams = new URLSearchParams(window.location.search);
    const token = urlParams.get('token');
    const refreshToken = urlParams.get('refreshToken');
    const username = urlParams.get('username');

    if (token && username) {
      this.setTokens(token, refreshToken || '');
      this.setUser({ username });

      // Xóa tokens khỏi URL để bảo mật
      const url = new URL(window.location.href);
      url.searchParams.delete('token');
      url.searchParams.delete('refreshToken');
      url.searchParams.delete('username');
      window.history.replaceState({}, document.title, url.pathname);

      return true;
    }

    return false;
  }

  setTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem('authToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
    this.tokenSubject.next(accessToken);
    this.refreshTokenSubject.next(refreshToken);

    // Bắt đầu auto-check khi có token mới
    this.startTokenExpirationCheck();
  }

  setToken(token: string): void {
    localStorage.setItem('authToken', token);
    this.tokenSubject.next(token);
  }

  setUser(user: any): void {
    localStorage.setItem('userInfo', JSON.stringify(user));
    this.userSubject.next(user);
  }

  getToken(): string | null {
    return this.tokenSubject.value || localStorage.getItem('authToken');
  }

  getRefreshToken(): string | null {
    return this.refreshTokenSubject.value || localStorage.getItem('refreshToken');
  }

  getUser(): any {
    return this.userSubject.value || JSON.parse(localStorage.getItem('userInfo') || 'null');
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  // Kiểm tra token có sắp hết hạn không (trước 10 phút)
  isTokenExpiringSoon(): boolean {
    const token = this.getToken();
    if (!token) return false;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const expirationTime = payload.exp * 1000; // Convert to milliseconds
      const currentTime = Date.now();
      const timeUntilExpiry = expirationTime - currentTime;

      // Trả về true nếu token sẽ hết hạn trong vòng 10 phút (600000ms)
      return timeUntilExpiry < 600000 && timeUntilExpiry > 0;
    } catch (error) {
      console.error('❌ Error parsing token:', error);
      return true; // Coi như token không hợp lệ
    }
  }

  // Bắt đầu timer kiểm tra token expiration mỗi 5 phút
  private startTokenExpirationCheck(): void {
    // Clear existing timer
    if (this.refreshTokenTimer) {
      clearInterval(this.refreshTokenTimer);
    }

    // Check immediately
    this.checkAndRefreshToken();

    // Then check every 5 minutes
    this.refreshTokenTimer = setInterval(() => {
      this.checkAndRefreshToken();
    }, 5 * 60 * 1000); // 5 minutes
  }

  // Kiểm tra và refresh token nếu cần
  private checkAndRefreshToken(): void {

    if (!this.isAuthenticated()) {
      return;
    }

    if (this.isTokenExpiringSoon()) {
      this.refreshAccessToken().subscribe({
        next: (response) => {
        },
        error: (error) => {
          console.error('❌ Token refresh failed:', error);
          this.handleRefreshFailure();
        }
      });
    }
  }

  // Refresh access token
  refreshAccessToken(): Observable<any> {
    const refreshToken = this.getRefreshToken();

    if (!refreshToken) {
      console.error('❌ No refresh token available');
      return of(null);
    }

    return this.http.post(`${this.API_BASE}/refresh`, { refreshToken }).pipe(
      tap((response: any) => {
        if (response.accessToken) {
          this.setToken(response.accessToken);

          // Cập nhật refresh token nếu có
          if (response.refreshToken) {
            localStorage.setItem('refreshToken', response.refreshToken);
            this.refreshTokenSubject.next(response.refreshToken);
          }
        }
      }),
      catchError((error) => {
        console.error('❌ Refresh token failed:', error);
        throw error;
      })
    );
  }

  // Xử lý khi refresh thất bại
  private handleRefreshFailure(): void {
    this.logout();

    // Thông báo cho user
    alert('Your session has expired. Please login again.');

    // Redirect về dashboard
    window.location.href = 'http://localhost:8080/dashboard';
  }

  logout(): void {

    // Clear timer
    if (this.refreshTokenTimer) {
      clearInterval(this.refreshTokenTimer);
      this.refreshTokenTimer = null;
    }

    // Clear storage
    localStorage.removeItem('authToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('userInfo');

    // Clear subjects
    this.tokenSubject.next(null);
    this.refreshTokenSubject.next(null);
    this.userSubject.next(null);
  }

  // Method để force refresh token (có thể gọi từ interceptor)
  forceRefreshToken(): Observable<any> {
    return this.refreshAccessToken();
  }
}
