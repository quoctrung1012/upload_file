import {NgbModal, NgbModalRef} from '@ng-bootstrap/ng-bootstrap';

interface HiddenElementState {
  element: Element;
  originalValue: string | null;
}

export function NgbModalCustom(modalService: NgbModal): NgbModal {
  const originalOpen = modalService.open.bind(modalService);

  modalService.open = (content: any, options?: any): NgbModalRef => {
    // 🔹 Ghi lại tất cả phần tử có aria-hidden trước khi mở modal
    const savedBefore: HiddenElementState[] = [];
    const beforeElements = new Set<Element>();

    document.querySelectorAll('[aria-hidden]').forEach(el => {
      savedBefore.push({
        element: el,
        originalValue: el.getAttribute('aria-hidden'),
      });
      beforeElements.add(el);
    });

    // 🔹 Mở modal
    const modalRef = originalOpen(content, options);

    // 🔹 Sau khi modal mở → tìm phần tử mới bị thêm aria-hidden
    const addedAfter: Element[] = [];

    document.querySelectorAll('[aria-hidden]').forEach(el => {
      if (!beforeElements.has(el)) {
        addedAfter.push(el);
      }
      el.removeAttribute('aria-hidden');
    });

    // 🔹 Khi modal đóng → khôi phục savedBefore + xóa addedAfter
    const restoreAriaHiddenAndFocus = () => {
      savedBefore.forEach(({element, originalValue}) => {
        if (originalValue !== null) {
          element.setAttribute('aria-hidden', originalValue);
        } else {
          element.removeAttribute('aria-hidden');
        }
      });
      addedAfter.forEach(el => {
        el.removeAttribute('aria-hidden');
      });

    };

    modalRef.closed.subscribe(restoreAriaHiddenAndFocus);
    modalRef.dismissed.subscribe(restoreAriaHiddenAndFocus);

    return modalRef;
  };

  return modalService;
}
