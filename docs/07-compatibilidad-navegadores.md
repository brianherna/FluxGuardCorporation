# Compatibilidad en diferentes navegadores

**Proyecto:** FluxGuard — Comercio electrónico de monitoreo eléctrico IoT
**Criterio de la guía que cubre:** 17 (comprobar compatibilidad en diferentes navegadores)

> Este documento es el **formato de evidencia**. La sección 2 explica qué tecnologías usa el sitio; las secciones 3 y 4 se llenan al probar el sitio en cada navegador, con una captura por prueba.

---

## 1. Sitio a probar

https://fluxguardcorporation.onrender.com

Usen el modo de prueba de pagos: tarjeta `4242 4242 4242 4242`, cualquier fecha futura y cualquier CVC.

## 2. Tecnologías del frontend y soporte esperado

| Tecnología | Uso | Navegadores compatibles |
|---|---|---|
| HTML5 y CSS3 (Flexbox, Grid, variables CSS) | Diseño de todas las páginas | Chrome, Edge, Firefox, Safari y Opera actuales |
| Bootstrap 5.3 | Menús, ventanas (modales) y panel del carrito | Navegadores modernos (no Internet Explorer) |
| JavaScript moderno (`async/await`, `fetch`, `?.`, `??`) | Lógica de la tienda, carrito y pagos | Chrome/Edge 80+, Firefox 74+, Safari 13.1+ |
| `Blob` y `URL.createObjectURL` | Descarga automática del ticket en PDF | Navegadores modernos |
| `@media print` | Impresión del ticket | Todos los navegadores modernos |
| Diseño adaptable (*responsive*) | Celular, tableta y escritorio | — |

Internet Explorer 11 **no** es compatible (Bootstrap 5 tampoco lo soporta).

## 3. Matriz de pruebas

Marquen ✅ si funciona, ❌ si falla (y anoten el problema en Observaciones).

| Prueba | Chrome (Windows) | Edge (Windows) | Firefox (Windows) | Safari o Chrome (celular) | Observaciones |
|---|---|---|---|---|---|
| 1. Página de inicio y menú (también el menú móvil) | | | | | |
| 2. Tienda: búsqueda y filtros por categoría | | | | | |
| 3. Tienda: stock, garantía y precios netos visibles | | | | | |
| 4. Crear cuenta: Aviso de Privacidad y casilla obligatoria | | | | | |
| 5. Iniciar sesión y abrir «Mis compras» | | | | | |
| 6. Carrito: + / − / Quitar y políticas | | | | | |
| 7. Pago con tarjeta de prueba (Stripe) | | | | | |
| 8. Confirmación: descarga automática del PDF | | | | | |
| 9. Imprimir ticket | | | | | |
| 10. Reseña con estrellas al terminar la compra | | | | | |
| 11. Carrusel de reseñas sin sesión | | | | | |

**Versiones probadas:**

| Navegador | Versión | Sistema operativo | Fecha | Probó |
|---|---|---|---|---|
| Chrome | | | | |
| Edge | | | | |
| Firefox | | | | |
| Celular | | | | |

## 4. Capturas de evidencia

Guarden las capturas en `docs/img/navegadores/` y enlácenlas así:

| Navegador | Tienda | Carrito | Pago confirmado |
|---|---|---|---|
| Chrome | `![](img/navegadores/chrome-tienda.png)` | `![](img/navegadores/chrome-carrito.png)` | `![](img/navegadores/chrome-pago.png)` |
| Edge | `![](img/navegadores/edge-tienda.png)` | `![](img/navegadores/edge-carrito.png)` | `![](img/navegadores/edge-pago.png)` |
| Firefox | `![](img/navegadores/firefox-tienda.png)` | `![](img/navegadores/firefox-carrito.png)` | `![](img/navegadores/firefox-pago.png)` |
| Celular | `![](img/navegadores/movil-tienda.png)` | `![](img/navegadores/movil-carrito.png)` | `![](img/navegadores/movil-pago.png)` |

## 5. Notas para las pruebas

- En Firefox y Safari, la primera descarga automática puede pedir permiso: si aparece el aviso, elijan «Permitir».
- Si el correo del ticket no llega, revisen la carpeta de Spam.
- Para probar en celular desde la computadora: en Chrome, F12 → ícono de dispositivo (*Toggle device toolbar*).
