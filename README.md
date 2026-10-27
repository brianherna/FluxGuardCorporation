# FluxGuard

Entra a la pagina con el siguiente link: https://fluxguardcorporation.onrender.com

Plataforma web de monitoreo eléctrico IoT. Backend en Spring Boot 3.5 (Java 17) con MongoDB Atlas; el frontend estático se sirve desde `src/main/resources/static`.

## Configuración

1. Copia `.env.example` a `.env` y llena los valores (el `.env` nunca se sube a git).
2. `application.properties` sí está en git: solo referencia variables de entorno, no contiene secretos.

| Variable | Uso |
| --- | --- |
| `MONGODB_URI`, `MONGODB_DATABASE` | Conexión a MongoDB Atlas |
| `APP_BASE_URL` | URL pública; se usa en el enlace del correo de verificación |
| `BREVO_API_KEY`, `BREVO_SENDER_EMAIL`, `BREVO_SENDER_NAME` | Envío del correo de verificación. Sin clave, el enlace se imprime en la consola |
| `RESEND_API_KEY`, `RESEND_FROM_EMAIL` | Alternativa a Brevo: si `RESEND_API_KEY` tiene valor, los correos se envían con Resend. `RESEND_FROM_EMAIL` vacío usa `BREVO_SENDER_EMAIL` |
| `CORS_ALLOWED_ORIGINS` | Dominios extra que pueden llamar a la API (separados por coma) |
| `ADMIN_API_KEY` | Clave para `GET /api/prospectos` (encabezado `X-Admin-Key`). Vacía = deshabilitado |
| `CONTACTO_CORREO_EQUIPO` | Correo que recibe el aviso de cada solicitud del formulario de contacto (y el "Responder a" del correo al cliente) |
| `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET` | Credenciales de la app de PayPal (pestaña Sandbox para pruebas) |
| `PAYPAL_MODE` | `sandbox` (pagos de prueba, por defecto) o `live` (pagos reales) |
| `PAYPAL_CURRENCY` | Moneda de cobro, por defecto `MXN` |
| `STRIPE_SECRET_KEY` | Llave secreta de Stripe (`sk_test_...` = modo de prueba). Vacía = no aparece el pago con tarjeta |
| `STRIPE_WEBHOOK_SECRET` | Secreto del webhook de Stripe (`whsec_...`), para confirmar pagos aunque el cliente no regrese a la página |

En Render, define las mismas variables en el panel del servicio.

## Ejecutar

```bash
mvn spring-boot:run
```

o con Docker:

```bash
docker build -t fluxguard . && docker run --env-file .env -p 8080:8080 fluxguard
```

## API

| Método | Ruta | Descripción |
| --- | --- | --- |
| POST | `/api/auth/register` | Crea la cuenta y envía el enlace de verificación |
| GET | `/api/auth/verify?token=` | Activa la cuenta desde el correo |
| POST | `/api/auth/resend` | Reenvía el enlace de verificación |
| POST | `/api/auth/login` | Devuelve el usuario y un token de sesión (7 días) |
| GET | `/api/auth/me` | Usuario de la sesión (`Authorization: Bearer <token>`) |
| POST | `/api/auth/logout` | Cierra la sesión |
| GET | `/api/catalogo` | Productos de la tienda (precios en MXN con IVA) |
| GET | `/api/carrito` | Carrito de la sesión |
| POST | `/api/carrito/items` | Agrega un producto (`productoId`, `cantidad`) |
| PUT / DELETE | `/api/carrito/items/{productoId}` | Cambia la cantidad o quita el producto |
| GET | `/api/pagos/metodos` | Métodos de pago configurados (`paypal`, `stripe`) |
| POST | `/api/carrito/comprar?metodo=paypal\|stripe` | Crea el pedido pendiente y devuelve `urlPago` (PayPal o Stripe) |
| GET | `/api/pedidos/{folio}` | Estado de un pedido del usuario |
| POST | `/api/pedidos/{folio}/confirmar?token=` | Confirma el pago (orden de PayPal o sesión de Stripe); si se completa activa el plan y vacía el carrito |
| POST | `/api/pagos/stripe/webhook` | Aviso de Stripe (`checkout.session.completed`), verificado con `STRIPE_WEBHOOK_SECRET` |
| POST | `/api/pedidos/{folio}/cancelar` | Marca el pedido como cancelado (el carrito se conserva) |
| POST | `/api/prospectos` | Guarda el formulario de contacto con un folio (`SOL-12345`), envía confirmación al cliente y aviso al equipo. Límite: 1 por correo cada 10 min y 5 por IP por hora |
| GET | `/api/prospectos` | Lista prospectos (requiere `X-Admin-Key`) |
| GET | `/api/health` | Estado del servicio |

## Pagos con PayPal Sandbox (modo de prueba)

Los pagos usan PayPal Sandbox: el flujo es igual al real pero con cuentas ficticias, sin dinero real.

1. Entra a https://developer.paypal.com con tu cuenta de PayPal y abre **Apps & Credentials** (pestaña **Sandbox**).
2. Crea una app (tipo *Merchant*) y copia **Client ID** y **Secret** a `PAYPAL_CLIENT_ID` y `PAYPAL_CLIENT_SECRET` en tu `.env`.
3. En **Testing Tools > Sandbox Accounts** hay una cuenta *Business* (la que recibe) y una *Personal* (la compradora).
   Para cobrar en MXN, la cuenta Business de prueba debe ser de **México**; si la tuya es de otro país, crea una nueva Business con país México y crea la app con ella.
4. En la tienda, al pagar te lleva a PayPal: inicia sesión con el correo y contraseña de la cuenta **Personal** de prueba
   (se ven en *Sandbox Accounts > View/Edit account*). Al aprobar regresas a `carrito.html` y el pedido queda pagado.

El cobro se confirma cuando el cliente regresa a la página, así que funciona en `localhost` sin configurar webhooks.

## Pagos con tarjeta (Stripe, modo de prueba)

1. Crea una cuenta en https://dashboard.stripe.com y activa **Test mode** (arriba a la derecha).
2. En **Developers > API keys** copia la **Secret key** (`sk_test_...`) a `STRIPE_SECRET_KEY`.
3. (Recomendado en Render) En **Developers > Webhooks > Add endpoint** pon `https://fluxguardcorporation.com/api/pagos/stripe/webhook`,
   elige el evento `checkout.session.completed` y copia el **Signing secret** (`whsec_...`) a `STRIPE_WEBHOOK_SECRET`.
4. En el carrito aparece **Pagar con tarjeta**. Tarjeta de prueba: `4242 4242 4242 4242`, cualquier fecha futura y cualquier CVC.

### Compras sin cuenta (invitado)

El carrito y el pago funcionan sin iniciar sesión: el navegador genera un token de invitado (encabezado `X-Carrito-Invitado`)
y el carrito se guarda con ese token. Los invitados pueden comprar el kit, accesorios y garantía; los **planes** requieren
cuenta porque se activan en ella. El nombre y correo del invitado se toman de su cuenta de PayPal al cobrar.
Si el invitado inicia sesión, su carrito pasa a la cuenta (`POST /api/carrito/fusionar`).
