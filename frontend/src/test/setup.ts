import "@testing-library/jest-dom/vitest";

import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

afterEach(() => cleanup());

class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}

Object.defineProperty(globalThis, "ResizeObserver", {
  value: ResizeObserverMock,
  writable: true,
});

Object.defineProperty(globalThis, "matchMedia", {
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  }),
  writable: true,
});

Object.defineProperties(Element.prototype, {
  hasPointerCapture: {
    value: () => false,
    writable: true,
  },
  setPointerCapture: {
    value: () => undefined,
    writable: true,
  },
  releasePointerCapture: {
    value: () => undefined,
    writable: true,
  },
  scrollIntoView: {
    value: () => undefined,
    writable: true,
  },
});
