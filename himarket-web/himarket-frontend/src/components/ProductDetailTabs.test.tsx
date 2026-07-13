import { cleanup, render } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import { ProductDetailTabs } from './ProductDetailTabs';

beforeAll(() => {
  vi.stubGlobal(
    'ResizeObserver',
    class {
      disconnect() {}
      observe() {}
      unobserve() {}
    },
  );
});

afterAll(() => {
  vi.unstubAllGlobals();
});
afterEach(() => cleanup());

describe('ProductDetailTabs', () => {
  it('兼容新旧 Ant Design Tabs DOM 并建立填充高度链', () => {
    const { container } = render(
      <ProductDetailTabs
        fillHeight
        items={[{ children: <div>长内容</div>, key: 'overview', label: '概览' }]}
      />,
    );

    const tabs = container.querySelector('.ant-tabs');
    const panel = container.querySelector('[role="tabpanel"]');

    expect(tabs).toHaveClass('flex', 'min-h-0', 'flex-1', 'flex-col');
    expect(tabs?.className).toContain('[&_.ant-tabs-content-holder]:overflow-hidden');
    expect(tabs?.className).toContain('[&_.ant-tabs-body-holder]:overflow-hidden');
    expect(tabs?.className).toContain('[&_.ant-tabs-body]:h-full');
    expect(tabs?.className).toContain('[&_.ant-tabs-content]:h-full');
    expect(tabs?.className).toContain('[&_.ant-tabs-tabpane]:h-full');
    expect(panel).toHaveClass('h-full', 'min-h-0', 'min-w-0', 'px-5', 'pb-5');
  });

  it('合并调用方提供的语义类名', () => {
    const { container } = render(
      <ProductDetailTabs
        classNames={{ content: 'custom-content' }}
        fillHeight
        items={[{ children: <div>内容</div>, key: 'overview', label: '概览' }]}
      />,
    );

    expect(container.querySelector('[role="tabpanel"]')).toHaveClass('custom-content');
  });

  it('未启用填充高度时保持原有布局', () => {
    const { container } = render(
      <ProductDetailTabs items={[{ children: <div>内容</div>, key: 'overview', label: '概览' }]} />,
    );

    const tabs = container.querySelector('.ant-tabs');
    const panel = container.querySelector('[role="tabpanel"]');

    expect(tabs).not.toHaveClass('flex', 'min-h-0', 'flex-1', 'flex-col');
    expect(tabs?.className).not.toContain('[&_.ant-tabs-body-holder]');
    expect(tabs?.className).not.toContain('[&_.ant-tabs-content-holder]');
    expect(panel).not.toHaveClass('h-full', 'min-h-0');
    expect(panel).toHaveClass('min-w-0', 'px-5', 'pb-5');
  });
});
