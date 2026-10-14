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
| `CORS_ALLOWED_ORIGINS` | Dominios extra que pueden llamar a la API (separados por coma) |
| `ADMIN_API_KEY` | Clave para `GET /api/prospectos` (encabezado `X-Admin-Key`). Vacía = deshabilitado |

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
| POST | `/api/suscripcion` | Activa un plan (`free`, `standard`, `ultimate`, `prototipo`) para la sesión |
| GET | `/api/catalogo` | Productos de la tienda (precios en MXN con IVA) |
| GET | `/api/carrito` | Carrito de la sesión |
| POST | `/api/carrito/items` | Agrega un producto (`productoId`, `cantidad`) |
| PUT / DELETE | `/api/carrito/items/{productoId}` | Cambia la cantidad o quita el producto |
| POST | `/api/carrito/comprar` | Crea el pedido, activa el plan incluido y vacía el carrito |
| POST | `/api/prospectos` | Guarda el formulario de contacto |
| GET | `/api/prospectos` | Lista prospectos (requiere `X-Admin-Key`) |
| GET | `/api/health` | Estado del servicio |

> El pago todavía es una activación sin cobro real: falta integrar una pasarela de pago.
