import { Component, Input } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import {NgxDocViewerModule} from 'ngx-doc-viewer';
import {NgIf} from '@angular/common';

@Component({
  selector: 'office-viewer',
  imports: [
    NgxDocViewerModule,
    NgIf
  ],
  template: `
    <ngx-doc-viewer
      *ngIf="fileUrl"
      [url]="fileUrl"
      viewer="office"
      style="width:100%;height:80vh;">
    </ngx-doc-viewer>

    <iframe *ngIf="!useNgxDocViewer"
            [src]="officeViewerUrl"
            width="100%"
            height="80vh"
            frameborder="0">
    </iframe>
  `
})
export class OfficeViewerComponent {
  @Input() fileUrl: string = '';
  @Input() useNgxDocViewer = true;

  officeViewerUrl: SafeResourceUrl = '';

  constructor(private sanitizer: DomSanitizer) {}

  ngOnChanges() {
    if (!this.useNgxDocViewer) {
      const encodedUrl = encodeURIComponent(this.fileUrl);
      this.officeViewerUrl = this.sanitizer.bypassSecurityTrustResourceUrl(
        `https://view.officeapps.live.com/op/embed.aspx?src=${encodedUrl}&embedded=true`
      );
    }
  }
}
