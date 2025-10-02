import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'fileSize'
})
export class FileSizePipe implements PipeTransform {

  transform(size: number = 0): string {
    return formatSize(size);
  }

}
export function formatSize(bytes: number) {
  if (bytes == 0) return '0 Bytes';
  var k = 1000, //Decimal SI (base 10)
    decimalPoint = 0,
    sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'],
    i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(decimalPoint)) + ' ' + sizes[i];
}
