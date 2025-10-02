export const API: string = 'http://localhost:8080';

function isObject(v: any) {
  return v && typeof v == "object";
}

function isArray(v: any) {
  return toString.apply(v) === '[object Array]';
}

function isEmptyObject(v: any) {
  if (!isObject(v)) {
    return false;
  }
  for (let key in v) {
    return false;
  }
  return true;
}

function isEmpty(v: any, allowBlank?: any) {
  return v === null || v === undefined || ((isArray(v) && !v.length)) || (!allowBlank ? v === '' : false) || isEmptyObject(v);
}

export const getPUrl = (path: any) => {
  let isSecure = /^https/i.test(window.location.protocol);
  let url = window.location.protocol + '//';
  url += window.location.hostname;
  let __port: any = window.location.port;

  if (!isEmpty(__port)) {
    if (isSecure && (__port != 443)) {
      url += ':' + __port;
    } else if (!isSecure && __port != 80) {
      url += ':' + __port;
    }
  }
  let pathname = window.location.pathname;
  if (pathname.length > 1) {
    let position = pathname.indexOf('/', 1);
    if (position == 0)
      position = pathname.length;
    url += pathname.substr(0, position);
  }
  return url.includes('localhost') ? `${API}/${path}` : `${url}/${path}`;
}
