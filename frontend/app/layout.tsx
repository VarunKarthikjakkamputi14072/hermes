import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Hermes — Order Fulfillment Engine",
  description:
    "Live console for Hermes: a Kafka-decoupled order pipeline that absorbs load spikes and never oversells.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="min-h-screen antialiased">{children}</body>
    </html>
  );
}
