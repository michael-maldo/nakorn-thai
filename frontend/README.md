# Frontend

Run `npm ci --include=optional` and `npm run dev`. The existing Vite proxy forwards
`/api/` to the backend on `127.0.0.1:8080`. Run the Spring backend alongside Vite.

- Homepage: `http://localhost:5173/`
- Initial dashboard: `http://localhost:5173/#/staff/menu`
- Request tests: `npm test`
- Production build: `npm run build`

See [the menu dashboard guide](../docs/menu/menu-dashboard.md) for admin configuration,
seed data, editing and the required production Nginx proxy. Admin secrets belong
only in the backend environment. The existing deployment workflow ships frontend
assets only; the API must be running before deploying this version.
