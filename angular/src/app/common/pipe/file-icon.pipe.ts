import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'fileIcon'
})
export class FileIconPipe implements PipeTransform {

  transform(name: string): string {
    let cls = ''
    switch (name) {
      case 'audio/ogg':
      case 'video/x-theora+ogg':
      case 'video/mpeg':
      case 'video/quicktime':
      case 'video/x-msvideo':
      case 'audio/x-wav':
        cls = 'video'
        break;
      case 'text/xml':
        cls = 'xml'
        break;
      case 'text/csv':
        cls = 'csv'
        break;
      case 'image/vnd.adobe.photoshop':
        cls = 'psd'
        break;
      case 'image/tiff':
        cls = 'tiff'
        break;
      case 'application/vnd.rar':
        cls = 'rar'
        break;
      case 'application/vnd.ms-powerpoint':
      case 'application/vnd.openxmlformats-officedocument.presentationml.presentation':
        cls = 'ppt'
        break;
      case 'image/png':
        cls = 'png'
        break;
      case 'image/gif':
        cls = 'gif'
        break;
      case 'application/x-msdownload':
        cls = 'exe'
        break;
      case 'application/postscript':
        cls = 'ai'
        break;
      case 'audio/aac':
        cls = 'aac'
        break;
      case 'application/vnd.ms-excel':
      case 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet':
        cls = 'xls'
        break;
      case 'application/pdf':
        cls = 'pdf'
        break;
      case 'video/mp4':
        cls = 'mp4'
        break;
      case 'application/zip':
      case 'application/x-zip-compressed':
        cls = 'zip'
        break;
      case 'text/rtf':
        cls = 'rtf'
        break;
      case 'text/plain':
        cls = 'txt'
        break;
      case 'image/jpeg':
        cls = 'jpg'
        break;
      case 'image/webp':
        cls = 'webp'
        break;
      case 'application/msword':
      case 'application/vnd.openxmlformats-officedocument.wordprocessingml.document':
        cls = 'doc'
        break;
      case 'application/json':
        cls = 'json'
        break;
      case 'audio/mpeg':
        cls = 'mp3'
        break;
      case 'text/html':
        cls = 'html'
        break;
      case 'text/css':
        cls = 'css'
        break;
      case 'text/javascript':
        cls = 'js'
        break;
      case 'image/svg+xml':
        cls = 'svg'
        break;
      default:
        cls = ''
    }
    return cls;
  }

}
