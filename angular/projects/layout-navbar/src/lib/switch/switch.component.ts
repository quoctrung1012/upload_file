import {Component} from '@angular/core';
import {I18nService} from './i18n.service';

@Component({
  selector: 'app-switch',
  imports: [],
  templateUrl: './switch.component.html',
  styleUrl: './switch.component.scss'
})
export class SwitchComponent {
  currentLang: 'en' | 'vi' = 'en';

  constructor(private i18n: I18nService) {
    this.currentLang = i18n.currentLang as 'en' | 'vi';
  }

  async onToggle(event: Event) {
    const isChecked = (event.target as HTMLInputElement).checked;
    const lang = isChecked ? 'vi' : 'en';
    this.currentLang = lang;
    await this.i18n.changeLanguage(lang);
  }
}
