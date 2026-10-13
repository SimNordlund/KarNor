# Karnor Kotlin/Spring backend

This project contains all backend logic for Karnor: PDF resources, the product catalog and admin API, authentication, private file storage, Stripe/Swish checkout, payment confirmation, protected downloads and email delivery.

The React frontend is maintained separately in `C:\Users\TaraR\Desktop\KarNor\karnor-app`. Copy this project's `.env.example` to `.env`, configure PostgreSQL and the server settings, and start Spring from IntelliJ or your normal Maven workflow. The default port is 8080. Run `npm run dev` in the frontend project and open `http://localhost:5173`.

See [CMS.md](CMS.md) for API configuration, storage, payments, email delivery and production routing. No JavaScript backend or Node server is needed.
