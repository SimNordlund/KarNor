# Kotlin CMS API

This Spring service now provides the API used by the React shop and admin page: `/api/catalog`, `/api/admin/*`, `/api/checkout`, `/api/orders/*`, `/api/downloads/*`, `/api/stripe/webhook`, and `/media/*`. The existing PDF endpoints remain available.

Set `APP_URL` to the exact frontend origin, `ADMIN_PASSWORD` to a unique password of at least 12 characters, and `KARNOR_DATA_DIR` to a durable, private directory. The default data directory is `./data`. Back up the entire directory, including `store.json` and `files`. Run one backend instance against that directory; concurrent replicas need shared database and object storage.

To enable payment, set `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET`. Register `/api/stripe/webhook` for `checkout.session.completed` and `checkout.session.async_payment_succeeded`. The API verifies the Stripe signature and payment amount before exposing download links. Purchases are tied to an HttpOnly browser cookie for 30 days. Admin sessions last eight hours in server memory and require a CSRF token for writes.

The catalog is seeded from `catalog.seed.json` only when `store.json` does not exist. Product images are public. Uploaded sale files are private until payment is confirmed. Image uploads accept PNG/JPG/WebP up to 8 MB; sale files also accept PDF and allow up to 40 MB.

For local development, start this backend on port 8080 and run `npm run dev:kotlin` in the React app. Vite proxies `/api` and `/media` to port 8080 in that mode. For deployment, route those paths to the Spring service from the same frontend origin, or adapt the frontend and cookie policy together.
