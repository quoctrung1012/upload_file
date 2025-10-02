// i18n.service.ts
import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

@Injectable({ providedIn: 'root' })
export class I18nService {
  constructor(private translate: TranslateService) {}

  changeLanguage(lang: string): Promise<string> {
    return new Promise((resolve, reject) => {
      this.translate.use(lang).subscribe({
        next: () => resolve(lang),
        error: err => reject(err)
      });
    });
  }

  get currentLang(): string {
    return this.translate.currentLang;
  }
}
