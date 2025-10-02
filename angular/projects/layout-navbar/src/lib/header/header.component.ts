import {Component, OnInit} from '@angular/core';
import {NgbDropdownModule} from '@ng-bootstrap/ng-bootstrap';
import {AuthService} from '../auth/auth.service';
import {CommonModule} from '@angular/common';
import {Router} from '@angular/router';
import {HttpClientService} from '../core/http-client.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, NgbDropdownModule],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss'
})
export class HeaderComponent implements OnInit {
  user: any = null;
  isAuthenticated = false;
  constructor(private authService: AuthService,
              private http: HttpClientService,
              private router: Router) {
  }

  ngOnInit(): void {
    // Kiểm tra token từ URL trước
    if (!this.authService.setTokenFromUrl()) {
      this.isAuthenticated = this.authService.isAuthenticated();
    } else {
      this.isAuthenticated = true;
    }

    console.log('🔐 Authentication status:', this.isAuthenticated);
    if (this.isAuthenticated) {
      this.user = this.authService.getUser();
      console.log('👤 Current user:', this.user);
    } else {
      console.log('❌ User not authenticated, showing access denied message');
    }
  }

  logout() {
    this.authService.logout();
    this.isAuthenticated = false;
    this.http.showLoading(`Đang logout tài khoản: ${this.user['username']}}`);
    setTimeout(() => {
      this.http.hideLoading();
      this.user = null;
      //this.router.navigate(['/']);
      this.goToDashboard();
    }, 2000)

  }

  goToDashboard() {
    window.location.href = 'http://localhost:8080/dashboard';
  }

  onUpload() {
    this.router.navigate(['/', 'upload-list']);
  }
}
