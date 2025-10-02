import {Routes} from '@angular/router';
import {UploadFileListComponent} from './upload-file-list/upload-file-list.component';
import {UploadVideoManagerComponent} from './upload-video-manager/upload-video-manager.component';
import {FileManagerComponent} from './file-manager/file-manager.component';

const FIRST_PAGE: string = 'file-manager';

export const routes: Routes = [
  // {path: '**', redirectTo: FIRST_PAGE},
  {path: '', redirectTo: FIRST_PAGE, pathMatch: 'full'},
  {path: 'file-manager', component: FileManagerComponent},
  {path: 'upload-list', component: UploadFileListComponent, data: {titleKey: 'upload.list.title'}},
  {path: 'video-list', component: UploadVideoManagerComponent, data: {titleKey: 'upload.video.title'}},
];

