import {HttpClient} from '@angular/common/http';
import {Injectable} from '@angular/core';
import {getPUrl} from 'layout-navbar';
import {catchError, throwError} from 'rxjs';
import {Chunk, ChunkFile} from './common-interface';

@Injectable({providedIn: 'root'})
export class FileUploadService {
  constructor(private http: HttpClient) {
  }

  private url = {
    chunk: getPUrl('upload/chunk'),
    merge: getPUrl('upload/merge'),
    checkChunk: getPUrl('upload/check-chunk'),
  }

  uploadChunk(chunk: Chunk) {
    const form = new FormData();
    form.append('file', chunk.file);
    form.append('filename', chunk.filename);
    form.append('chunkIndex', chunk.chunkIndex.toString());
    form.append('totalChunks', chunk.totalChunks.toString());

    return this.http.post(this.url.chunk, form, {responseType: 'text'}).pipe(
      catchError(this.errorHandler)
    );
  }

  mergeChunks(chunkFile: ChunkFile) {
    return this.http.post(this.url.merge, chunkFile).pipe(
      catchError(this.errorHandler)
    );
  }

  checkChunkExists(filename: string, chunkIndex: number): Promise<boolean> {
    return this.http.get<{ exists: boolean }>(this.url.checkChunk, {params: {filename, chunkIndex}})
      .toPromise()
      .then((res: any) => res.exists);
  }

  errorHandler(error: any) {
    let errorMessage = '';
    if (error.error instanceof ErrorEvent) {
      errorMessage = error.error.message;
    } else {
      errorMessage = `Error Code: ${error.status}\nMessage: ${error.message}`;
    }
    return throwError(errorMessage);
  }
}
