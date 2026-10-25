import type { Metadata } from 'next';
import { Provider } from '@/components/provider';
import '../global.css';
import { i18n } from '@/lib/i18n';

export const metadata: Metadata = {
  icons: {
    icon: '/logo.svg',
    apple: '/logo.svg',
  },
};

export default async function Layout({ params, children }: LayoutProps<'/[lang]'>) {
  const { lang } = await params;

  return (
    <html lang={lang} suppressHydrationWarning>
      <body className="flex flex-col min-h-screen">
        <Provider locale={lang}>{children}</Provider>
      </body>
    </html>
  );
}

export function generateStaticParams() {
  return i18n.languages.map((lang) => ({ lang }));
}
