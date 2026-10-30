import type { Metadata } from "next";
import "./globals.css";
export const metadata: Metadata = {
  title: "Kaizen — Invoice intelligence",
  description:
    "Your invoice-to-accounting workspace. Review exceptions, reconcile purchases and keep every decision traceable.",
};
export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
