import {Component, inject, OnDestroy} from '@angular/core';
import {ActivatedRoute, NavigationEnd, Router} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {CommonModule} from '@angular/common';
import {Title} from '@angular/platform-browser';

import {map, Subject, takeUntil} from 'rxjs';
import {LayoutNavbarComponent} from 'layout-navbar';

@Component({
  selector: 'app-root',
  imports: [CommonModule, TranslateModule, LayoutNavbarComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnDestroy {
  public translate = inject(TranslateService);
  private title = inject(Title);
  private activatedRoute = inject(ActivatedRoute);
  private destroy$ = new Subject<void>();
  private router = inject(Router);
  protected currentLang = 'en';

  constructor() {
    this.setupLanguage();
    this.setupTitle();
  }

  setupLanguage() {
    this.translate.use(this.currentLang);
  }

  setupTitle() {
    this.router.events
      .pipe(
        map((event) => {
          if (event instanceof NavigationEnd) {
            let route = this.activatedRoute.firstChild;
            while (route?.firstChild) {
              route = route.firstChild;
            }
            return route?.snapshot.data['titleKey'] || null;
          }
          return null;
        }),
        takeUntil(this.destroy$)
      )
      .subscribe((titleKey: string | string[]) => {
        if (titleKey) {
          this.translate.get(titleKey).subscribe((translated: string) => {
            this.title.setTitle(translated);
          });
        }
      });
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
