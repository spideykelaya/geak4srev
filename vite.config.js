import { defineConfig } from "vite";
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs";
import basicSsl from "@vitejs/plugin-basic-ssl";

export default defineConfig({
  plugins: [
    scalaJSPlugin(),
    basicSsl() // Required for Google OAuth (HTTPS)
  ],
  base: process.env.NODE_ENV === 'production' ? '/geak4srev/' : '/',
  server: {
    https: true,
    port: 5173,
    host: 'localhost',
    proxy: {
      '/generate': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/generate-excel': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
});