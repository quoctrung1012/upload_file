import {Component, ElementRef, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';

export const FILTER_PAG_REGEX = /[^0-9]/g;

@Component({
  selector: 'app-pagination',
  imports: [CommonModule, FormsModule],
  templateUrl: './pagination.component.html',
  styleUrl: './pagination.component.scss'
})
export class PaginationComponent implements OnChanges {
  @ViewChild('i', {static: false}) private inputRef!: ElementRef;
  @Input() public firstLoad: boolean = false;
  @Input() public pageNumber: number = 0;
  @Input() public pageSize: number = 10;
  @Input() public total: number = 0;
  @Output() public onLoad = new EventEmitter<number>(true);
  totalPage: number = 0;
  pageCurrent: number = 0;

  constructor() {
  }

  ngOnChanges(changes: SimpleChanges): void {
    this.totalPage = Math.ceil(this.total / this.pageSize);
    this.pageCurrent = this.totalPage == 0 ? 0 : this.pageNumber + 1;
  }

  protected isEllipsis(type: 'previous' | 'next'): boolean {
    if (this.totalPage == 0) return true;
    switch (type) {
      case "previous":
        return this.pageCurrent === 1;
      case "next":
        return this.pageCurrent === this.totalPage;
    }
  }

  protected selectPage(pageNumber: number | string): void {
    let page: number = typeof pageNumber === "string" ? parseInt(pageNumber) : pageNumber;
    if (isNaN(page)) {
      this.inputRef.nativeElement['value'] = this.pageCurrent.toString()
      return;
    }
    if (page > this.totalPage) {
      this._updatePages(this.totalPage);
      return;
    }
    this._updatePages(page)
  }

  protected formatInput(input: HTMLInputElement) {
    input.value = input.value.replace(FILTER_PAG_REGEX, '');
    if (!input.value) return;
  }

  private _updatePages(pageNumber: number) {
    if (pageNumber === this.pageCurrent) return;
    this.pageCurrent = pageNumber;
    this.pageNumber = pageNumber - 1;
    this.onLoad.emit(this.pageNumber);
  }

  protected getInputWidth(): string {
    const length = this.totalPage.toString().length;
    return `${2.5 + (length - 1) * 0.5}rem`;
  }

  public onLoadGrid() {
    this.onLoad.emit(this.pageNumber);
  }

  onChangePage(pageNumber: number) {
   if (this.totalPage == 0) return;
    this.pageCurrent = pageNumber + 1;
  }

}

