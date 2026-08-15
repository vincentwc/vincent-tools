class ResizeObserverStub {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

class MutationObserverStub {
  observe(): void {}
  disconnect(): void {}
  takeRecords(): MutationRecord[] {
    return [];
  }
}

globalThis.ResizeObserver = ResizeObserverStub;
globalThis.MutationObserver = MutationObserverStub;
