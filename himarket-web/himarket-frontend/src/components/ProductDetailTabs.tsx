import { Tabs } from 'antd';

import type { TabsProps } from 'antd';
import type { CSSProperties, ReactNode } from 'react';

const PRODUCT_DETAIL_TABS_CARD_CLASS =
  'overflow-hidden rounded-[14px] border border-[#DDE5F0] bg-white/90 shadow-[0_18px_50px_rgba(15,23,42,0.05)] backdrop-blur-sm';

const PRODUCT_DETAIL_TABS_NAV_CLASS =
  '[&_.ant-tabs-nav]:mb-5 [&_.ant-tabs-nav]:px-5 [&_.ant-tabs-tab]:py-4';

const PRODUCT_DETAIL_TABS_CONTENT_CLASS = 'min-w-0 px-5 pb-5';
const PRODUCT_DETAIL_TABS_FILL_ROOT_CLASS =
  'flex min-h-0 flex-1 flex-col [&_.ant-tabs-content-holder]:min-h-0 [&_.ant-tabs-content-holder]:flex-1 [&_.ant-tabs-content-holder]:overflow-hidden [&_.ant-tabs-body-holder]:min-h-0 [&_.ant-tabs-body-holder]:flex-1 [&_.ant-tabs-body-holder]:overflow-hidden [&_.ant-tabs-body]:h-full [&_.ant-tabs-content]:h-full [&_.ant-tabs-tabpane]:h-full';
const PRODUCT_DETAIL_TABS_FILL_CONTENT_CLASS = 'h-full min-h-0';

function classNames(...values: Array<string | undefined>) {
  return values.filter(Boolean).join(' ');
}

function mergeSemanticClassNames(
  semanticClassNames: TabsProps['classNames'] | undefined,
  contentPadded: boolean,
  fillHeight: boolean,
): TabsProps['classNames'] | undefined {
  if (!contentPadded && !fillHeight) {
    return semanticClassNames;
  }

  if (typeof semanticClassNames === 'function') {
    return (info) => {
      const resolvedClassNames = semanticClassNames(info) ?? {};

      return {
        ...resolvedClassNames,
        content: classNames(
          contentPadded ? PRODUCT_DETAIL_TABS_CONTENT_CLASS : undefined,
          fillHeight ? PRODUCT_DETAIL_TABS_FILL_CONTENT_CLASS : undefined,
          resolvedClassNames.content,
        ),
      };
    };
  }

  return {
    ...semanticClassNames,
    content: classNames(
      contentPadded ? PRODUCT_DETAIL_TABS_CONTENT_CLASS : undefined,
      fillHeight ? PRODUCT_DETAIL_TABS_FILL_CONTENT_CLASS : undefined,
      semanticClassNames?.content,
    ),
  };
}

interface ProductDetailTabLabelProps {
  children: ReactNode;
  icon: ReactNode;
}

interface ProductDetailTabsProps extends Omit<TabsProps, 'className' | 'size'> {
  cardClassName?: string;
  contentPadded?: boolean;
  fillHeight?: boolean;
  style?: CSSProperties;
  tabsClassName?: string;
}

export function ProductDetailTabLabel({ children, icon }: ProductDetailTabLabelProps) {
  return (
    <span className="flex items-center gap-1.5 font-semibold">
      <span className="inline-flex text-sm">{icon}</span>
      <span>{children}</span>
    </span>
  );
}

export function ProductDetailTabs({
  cardClassName,
  classNames: semanticClassNames,
  contentPadded = true,
  fillHeight = false,
  style,
  tabsClassName,
  ...tabsProps
}: ProductDetailTabsProps) {
  return (
    <div className={classNames(PRODUCT_DETAIL_TABS_CARD_CLASS, cardClassName)} style={style}>
      <Tabs
        {...tabsProps}
        className={classNames(
          PRODUCT_DETAIL_TABS_NAV_CLASS,
          fillHeight ? PRODUCT_DETAIL_TABS_FILL_ROOT_CLASS : undefined,
          tabsClassName,
        )}
        classNames={mergeSemanticClassNames(semanticClassNames, contentPadded, fillHeight)}
        size="large"
      />
    </div>
  );
}
