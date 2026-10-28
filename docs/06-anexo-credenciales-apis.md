# Anexo: credenciales de APIs y servicios

**Proyecto:** FluxGuard — Comercio electrónico de monitoreo eléctrico IoT
**Criterio de la guía que cubre:** 14 (credenciales de APIs como MongoDB, PayPal, Stripe, etc.)

> **Importante:** este documento se sube a GitHub, así que **no contiene ninguna llave ni contraseña**. Solo indica qué credencial usa cada servicio, en qué variable de entorno se configura y dónde se obtiene. Los valores reales están en el `.env` local (ignorado por git) y en las variables de entorno de Render. Para las capturas de los paneles, **tapen o recorten las llaves** antes de subirlas.

---

## 1. Servicios utilizados

| Servicio | Uso en FluxGuard | Variables de entorno | Dónde se obtiene | Modo para la evaluación |
|---|---|---|---|---|
| **MongoDB Atlas** | Base de datos en la nube | `MONGODB_URI`, `MONGODB_DATABASE` | cloud.mongodb.com → Database → Connect | Clúster en la nube |
| **Stripe** | Pago con tarjeta (Stripe Checkout) | `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | dashboard.stripe.com → Developers → API keys / Webhooks | Prueba (`sk_test_…`) |
| **PayPal** | Pago con cuenta PayPal | `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`, `PAYPAL_MODE`, `PAYPAL_CURRENCY` | developer.paypal.com → Apps & Credentials (Sandbox) | `sandbox`, MXN |
| **Resend** | Correos: verificación, contacto y ticket con PDF | `RESEND_API_KEY`, `RESEND_FROM_EMAIL` | resend.com → API Keys / Domains | Dominio `fluxguardcorporation.com` verificado |
| **Brevo** | Correo alternativo si no hay llave de Resend | `BREVO_API_KEY`, `BREVO_SENDER_EMAIL`, `BREVO_SENDER_NAME` | app.brevo.com → SMTP & API → API Keys | Respaldo |
| **Render** | Hosting del sitio y la API | Panel *Environment* del servicio | dashboard.render.com | https://fluxguardcorporation.onrender.com |
| **GitHub** | Repositorio y control de versiones | — | github.com/brianherna/FluxGuardCorporation | Ramas por integrante y Pull Requests |

Variables internas de la aplicación: `APP_BASE_URL` (URL pública), `CORS_ALLOWED_ORIGINS` (dominios que pueden llamar a la API), `ADMIN_API_KEY` (consulta de prospectos) y `CONTACTO_CORREO_EQUIPO` (avisos del formulario de contacto).

## 2. Cómo se protegen las credenciales

1. Nunca están en el código ni en el repositorio: `.env` está en `.gitignore`.
2. `.env.example` y `application.properties` solo tienen los **nombres** de las variables, sin valores.
3. En producción se configuran en el panel de Render.
4. Las llaves de Stripe y PayPal son **de prueba**: no mueven dinero real.
5. La llave de Resend tiene permisos **solo para enviar correos**.

## 3. Evidencias (capturas)

Agreguen una captura por servicio **con las llaves tapadas**. Pueden guardar las imágenes en `docs/img/` y enlazarlas aquí.

| Servicio | Qué mostrar en la captura | Captura |
|---|---|---|
| MongoDB Atlas | Clúster activo y las colecciones `usuarios`, `pedidos`, `resenas`… | `![MongoDB](img/mongodb.png)` |
| Stripe | Panel en *Test mode* con un pago de prueba exitoso | `![Stripe](img/stripe.png)` |
| PayPal | App en Sandbox (Apps & Credentials) | `![PayPal](img/paypal.png)` |
| Resend | Dominio verificado y el correo del ticket enviado (Emails) | `![Resend](img/resend.png)` |
| Render | Servicio en estado *Live* y la lista de variables (solo nombres) | `![Render](img/render.png)` |
