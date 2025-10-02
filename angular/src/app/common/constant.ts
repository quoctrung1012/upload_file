import {HttpHeaders} from '@angular/common/http';

export const API: string = 'http://localhost:8080/upload';
export const httpOptions = {
  headers: new HttpHeaders({
    'Content-Type': 'application/json'
  }),
  'Access-Control-Allow-Origin': 'http://localhost:4200',
  'Access-Control-Allow-Methods': 'GET,PUT,POST,DELETE,PATCH,OPTIONS'
};
export const TYPE_VIEW = {
  EMPTY: 'empty',
  IMAGE: 'image/',
  PDF: '/pdf',
}
export const FILTER_PAG_REGEX = /[^0-9]/g;
