import type { NextConfig } from "next";
const config: NextConfig = {
  output: "standalone",
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${process.env.BACKEND_URL || "http://127.0.0.1:8187"}/api/:path*`,
      },
      {
        source: "/oauth2/:path*",
        destination: `${process.env.BACKEND_URL || "http://127.0.0.1:8187"}/oauth2/:path*`,
      },
      {
        source: "/login/oauth2/:path*",
        destination: `${process.env.BACKEND_URL || "http://127.0.0.1:8187"}/login/oauth2/:path*`,
      },
    ];
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "same-origin" },
          { key: "X-Frame-Options", value: "SAMEORIGIN" },
          {
            key: "Permissions-Policy",
            value: "camera=(), microphone=(), geolocation=()",
          },
        ],
      },
    ];
  },
};
export default config;
