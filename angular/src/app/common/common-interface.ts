export interface ParamsSearch {
  page: number | 0,
  size: number | 5,
  search?: string,
}

export interface uploadFile {
  id: string,
  name: string,
  size: number,
  type: string,
  creationDate: string,
}

export interface ChunkFile {
  filename: string,
  totalChunks: number,
  type: string
}

export interface Chunk {
  file: Blob,
  filename: string,
  chunkIndex: number,
  totalChunks: number
}
