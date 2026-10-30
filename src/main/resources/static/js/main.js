/**
 * FluxGuard - Frontend principal
 * Navegación, UI, mapa, autenticación y formularios.
 */

const API_BASE = (window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1")
  ? (window.location.port === "8080" ? "" : "http://localhost:8080")
  : (localStorage.getItem("fluxg_api_url") || "");

// Limpieza automática de correos y sesiones de prueba previas
(function() {
  // Versiones anteriores guardaban usuarios con su contraseña sin cifrar en el navegador: se borran siempre
  localStorage.removeItem("fluxg_usuarios");
  // Versiones anteriores guardaban en el navegador los datos del formulario de contacto cuando fallaba el servidor
  localStorage.removeItem("fluxg_prospectos");
  if (!localStorage.getItem("fluxg_v4_clean")) {
    localStorage.removeItem("usuario");
    localStorage.removeItem("fluxguard_active_subscription");
    localStorage.setItem("fluxg_v4_clean", "true");
  }
  // Un "usuario" sin token de sesión viene de versiones anteriores (sin validación en el servidor): se descarta
  if (!localStorage.getItem("fluxg_token")) {
    localStorage.removeItem("usuario");
  }
  // En las páginas de login y registro nunca debe haber sesión activa preexistente
  if (window.location.pathname.includes("login.html") || window.location.pathname.includes("crear-cuenta.html")) {
    cerrarSesion();
  }
})();

// ===== Sesión =====
// El servidor entrega un token al iniciar sesión; "usuario" en localStorage es solo una copia para mostrar el nombre.
// Las páginas protegidas confirman la sesión con /api/auth/me antes de mostrar datos.

function guardarSesion(token, usuario) {
  localStorage.setItem("fluxg_token", token);
  localStorage.setItem("usuario", JSON.stringify(usuario));
}

function actualizarUsuario(usuario) {
  localStorage.setItem("usuario", JSON.stringify(usuario));
}

function cerrarSesion() {
  const token = localStorage.getItem("fluxg_token");
  localStorage.removeItem("fluxg_token");
  localStorage.removeItem("usuario");
  if (token) {
    fetch(`${API_BASE}/api/auth/logout`, { method: "POST", headers: { "Authorization": `Bearer ${token}` } }).catch(() => {});
  }
}

// fetch con el token de sesión. Si el servidor responde 401, la sesión local se borra.
// Sin sesión, el carrito y los pedidos se identifican con un token de invitado propio de este navegador.
async function apiFetch(path, options = {}) {
  const token = localStorage.getItem("fluxg_token");
  const headers = { ...(options.headers || {}) };
  if (token) headers["Authorization"] = `Bearer ${token}`;
  else headers["X-Carrito-Invitado"] = tokenInvitado();
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  if (response.status === 401) {
    localStorage.removeItem("fluxg_token");
    localStorage.removeItem("usuario");
  }
  return response;
}

function tokenInvitado() {
  let token = localStorage.getItem("fluxg_invitado");
  if (!token) {
    const bytes = new Uint8Array(24);
    crypto.getRandomValues(bytes);
    token = Array.from(bytes, b => b.toString(16).padStart(2, "0")).join("");
    localStorage.setItem("fluxg_invitado", token);
  }
  return token;
}

// Al iniciar sesión, lo que se agregó como invitado pasa al carrito de la cuenta
async function fusionarCarritoInvitado() {
  const invitado = localStorage.getItem("fluxg_invitado");
  const token = localStorage.getItem("fluxg_token");
  if (!invitado || !token) return;
  try {
    const response = await fetch(`${API_BASE}/api/carrito/fusionar`, {
      method: "POST",
      headers: { "Authorization": `Bearer ${token}`, "X-Carrito-Invitado": invitado }
    });
    if (response.ok) localStorage.removeItem("fluxg_invitado");
  } catch (_) {}
}

// Devuelve el usuario de la sesión validado por el servidor, o null si no hay sesión válida
async function obtenerUsuarioSesion() {
  if (!localStorage.getItem("fluxg_token")) return null;
  try {
    const response = await apiFetch("/api/auth/me");
    const data = await response.json().catch(() => null);
    if (!response.ok || !data?.success || !data.usuario) return null;
    actualizarUsuario(data.usuario);
    return data.usuario;
  } catch (_) {
    return null;
  }
}

// Solo permite redirigir a páginas del mismo sitio (evita redirecciones a dominios externos o "javascript:")
function rutaSegura(destino) {
  if (!destino) return null;
  try {
    const url = new URL(destino, window.location.href);
    if (url.origin !== window.location.origin) return null;
    return url.pathname + url.search + url.hash;
  } catch (_) {
    return null;
  }
}

document.addEventListener("DOMContentLoaded", () => {
  initNavbar();
  initNavbarAuth();
  initHeroPromos();
  initPlatformCarousel();
  initBackToTop();
  initMap();
  initContactForm();
  initPasswordToggles();
  initAuthForms();
  initPlanes();
  initCarrito();
  initSobrepantallas();
});

function initNavbar() {
  const toggle = document.getElementById("navbarToggle");
  const menu = document.getElementById("navbarMenu");
  if (!toggle || !menu) return;
  toggle.addEventListener("click", () => {
    const open = menu.classList.toggle("is-open");
    toggle.setAttribute("aria-expanded", String(open));
    toggle.setAttribute("aria-label", open ? "Cerrar menú" : "Abrir menú");
    const icon = toggle.querySelector("i");
    if (icon) icon.className = open ? "bi bi-x fs-4" : "bi bi-list fs-4";
  });
  menu.querySelectorAll("a").forEach(link => link.addEventListener("click", () => {
    menu.classList.remove("is-open");
    toggle.setAttribute("aria-expanded", "false");
    const icon = toggle.querySelector("i");
    if (icon) icon.className = "bi bi-list fs-4";
  }));
  document.addEventListener("click", event => {
    if (!menu.contains(event.target) && !toggle.contains(event.target)) {
      menu.classList.remove("is-open");
      toggle.setAttribute("aria-expanded", "false");
      const icon = toggle.querySelector("i");
      if (icon) icon.className = "bi bi-list fs-4";
    }
  });
}

function initNavbarAuth() {
  const raw = localStorage.getItem("usuario");
  const container = document.getElementById("navbar-auth-action");
  if (!container || !raw) return;
  try {
    const usuario = JSON.parse(raw);
    const nombre = escapeHtml(usuario.nombre || "Usuario");
    const correo = escapeHtml(usuario.correo || "");
    const subBadge = usuario.suscripcion ? `<span class="badge bg-success bg-opacity-25 text-success ms-2 font-monospace" style="font-size:9px;">ACTIVO</span>` : "";
    container.innerHTML = `
      <div class="dropdown">
        <button class="btn-flux-outline dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false">
          <i class="bi bi-person-circle"></i> <span class="navbar-user-name">${nombre} ${subBadge}</span>
        </button>
        <ul class="dropdown-menu dropdown-menu-end dropdown-menu-dark">
          <li><h6 class="dropdown-header">${correo}</h6></li>
          <li><a class="dropdown-item fw-bold text-warning" href="dashboard.html"><i class="bi bi-speedometer2 me-2"></i>Mi Dashboard IoT</a></li>
          <li><button class="dropdown-item" type="button" data-sobrepantalla="historial"><i class="bi bi-receipt me-2"></i>Mis compras</button></li>
          <li><a class="dropdown-item" href="planes.html"><i class="bi bi-grid me-2"></i>Planes</a></li>
          <li><a class="dropdown-item" href="contacto.html"><i class="bi bi-chat-left-dots me-2"></i>Contacto</a></li>
          <li><hr class="dropdown-divider"></li>
          <li><button class="dropdown-item text-danger" id="btn-logout"><i class="bi bi-box-arrow-right me-2"></i>Cerrar sesión</button></li>
        </ul>
      </div>`;
    document.getElementById("btn-logout")?.addEventListener("click", () => {
      cerrarSesion();
      window.location.href = "index.html";
    });
  } catch {
    localStorage.removeItem("usuario");
  }
}

const heroPromos = [
  { eyebrow: "FLUXGUARD / MONITOREO INTELIGENTE", title: "DATOS QUE SE<br><span class='accent-line'>CONVIERTEN EN</span><br>DECISIONES.", desc: "Visualiza el comportamiento de tu infraestructura y detecta lo importante antes de que se convierta en un problema." },
  { eyebrow: "FLUXGUARD / INFORMACIÓN EN TIEMPO REAL", title: "TODO EL SISTEMA.<br><span class='accent-line'>EN UNA SOLA</span><br>VISTA.", desc: "Centraliza indicadores, actividad y alertas en una plataforma pensada para consultar información de forma rápida." },
  { eyebrow: "FLUXGUARD / CONTROL Y ANÁLISIS", title: "ENTIENDE LO QUE<br><span class='accent-line'>ESTÁ PASANDO.</span><br>ACTÚA A TIEMPO.", desc: "Convierte los datos recopilados por IoT en información clara para supervisar y mejorar tu infraestructura." }
];

function initHeroPromos() {
  const eyebrow = document.getElementById("hero-eyebrow");
  const title = document.getElementById("hero-title");
  const desc = document.getElementById("hero-desc");
  const counter = document.getElementById("hero-counter");
  const dots = [...document.querySelectorAll(".hero-dot")];
  const pause = document.getElementById("btn-hero-pause");
  if (!eyebrow || !title || !desc) return;
  let index = 0, timer = null, paused = false;
  const render = next => {
    index = (next + heroPromos.length) % heroPromos.length;
    const promo = heroPromos[index];
    eyebrow.innerHTML = `<span class="status-dot"></span>${promo.eyebrow}`;
    title.innerHTML = promo.title;
    desc.textContent = promo.desc;
    if (counter) counter.textContent = `0${index + 1} / 0${heroPromos.length}`;
    dots.forEach((dot, i) => dot.classList.toggle("active", i === index));
  };
  const start = () => {
    clearInterval(timer);
    if (!paused) timer = setInterval(() => render(index + 1), 6500);
  };
  dots.forEach((dot, i) => dot.addEventListener("click", () => { render(i); start(); }));
  pause?.addEventListener("click", () => {
    paused = !paused;
    const icon = pause.querySelector("i");
    if (icon) icon.className = paused ? "bi bi-play-fill" : "bi bi-pause-fill";
    start();
  });
  render(0); start();
  const metricV = document.getElementById("metric-voltage");
  const metricA = document.getElementById("metric-current");
  const metricW = document.getElementById("metric-power");
  if (metricV && metricA && metricW) setInterval(() => {
    const v = (127 + Math.random() * 1.8 - .9).toFixed(1);
    const a = (8.5 + Math.random() * .4 - .2).toFixed(2);
    const w = ((Number(v) * Number(a)) / 1000).toFixed(2);
    metricV.textContent = `${v} V`; metricA.textContent = `${a} A`; metricW.textContent = `${w} kW`;
  }, 2800);
}

function initPlatformCarousel() {
  const track = document.getElementById("platformCarouselTrack");
  const prev = document.getElementById("btn-platform-prev");
  const next = document.getElementById("btn-platform-next");
  const counter = document.getElementById("platform-counter");
  const dots = [...document.querySelectorAll("#platform-dots button")];
  const wrapper = document.getElementById("platformCarouselWrapper");
  if (!track || !dots.length) return;
  let index = 0;
  let timer = null;
  const total = dots.length;
  const render = nextIndex => {
    index = (nextIndex + total) % total;
    track.style.transform = `translateX(-${index * 100}%)`;
    dots.forEach((dot, i) => dot.classList.toggle("active", i === index));
    if (counter) counter.textContent = `0${index + 1} / 0${total}`;
  };
  const startTimer = () => {
    clearInterval(timer);
    timer = setInterval(() => render(index + 1), 6500);
  };
  prev?.addEventListener("click", () => { render(index - 1); startTimer(); });
  next?.addEventListener("click", () => { render(index + 1); startTimer(); });
  dots.forEach((dot, i) => dot.addEventListener("click", () => { render(i); startTimer(); }));
  wrapper?.addEventListener("mouseenter", () => clearInterval(timer));
  wrapper?.addEventListener("mouseleave", () => startTimer());
  render(0);
  startTimer();
}

function initBackToTop() {
  const button = document.getElementById("btn-back-to-top");
  if (!button) return;
  const update = () => button.classList.toggle("visible", window.scrollY > 650);
  window.addEventListener("scroll", update, { passive: true });
  button.addEventListener("click", (e) => {
    e.preventDefault();
    window.scrollTo({ top: 0, behavior: "smooth" });
  });
  update();
}

function initMap() {
  const container = document.getElementById("map");
  if (!container || typeof L === "undefined") return;
  const destination = [20.204881, -99.220931];
  const map = L.map(container, { center: destination, zoom: 14, scrollWheelZoom: true });
  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", { maxZoom: 19, attribution: "© OpenStreetMap contributors" }).addTo(map);
  const fluxIcon = L.divIcon({ className: "custom-div-icon", html: `<div style="background:#b9a7ff;color:#100b19;padding:5px 10px;border-radius:999px;font-weight:900;font-size:10px;box-shadow:0 0 18px rgba(185,167,255,.7);white-space:nowrap">FLUXGUARD</div>`, iconSize: [110, 30], iconAnchor: [55, 15] });
  L.marker(destination, { icon: fluxIcon }).addTo(map).bindPopup("<b>FluxGuard</b><br>Mixquiahuala de Juárez, Hidalgo.");

  let userMarker = null;
  let currentRouteLayer = null;

  const locate = document.getElementById("btn-map-locate");
  const status = document.getElementById("map-route-status");
  const distanceEl = document.getElementById("route-distance-val");
  const durationEl = document.getElementById("route-duration-val");

  function haversineKm(lat1, lon1, lat2, lon2) {
    const R = 6371;
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
              Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
              Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  async function renderRoute(userCoords) {
    if (userMarker) map.removeLayer(userMarker);
    if (currentRouteLayer) map.removeLayer(currentRouteLayer);

    const userIcon = L.divIcon({
      className: "custom-div-icon",
      html: `<div style="background:#58e0a0;color:#07110d;padding:4px 8px;border-radius:999px;font-weight:900;font-size:9px;box-shadow:0 0 14px rgba(88,224,160,.6)">TÚ</div>`,
      iconSize: [35, 24],
      iconAnchor: [17, 12]
    });
    userMarker = L.marker(userCoords, { icon: userIcon }).addTo(map);

    let routeDrawn = false;
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 3500);
      const url = `https://router.project-osrm.org/route/v1/driving/${userCoords[1]},${userCoords[0]};${destination[1]},${destination[0]}?overview=full&geometries=geojson`;
      const response = await fetch(url, { signal: controller.signal });
      clearTimeout(timeoutId);
      if (response.ok) {
        const data = await response.json();
        const route = data.routes?.[0];
        if (route && route.geometry && route.geometry.coordinates) {
          const coords = route.geometry.coordinates.map(c => [c[1], c[0]]);
          currentRouteLayer = L.polyline(coords, { color: "#b9a7ff", weight: 5, opacity: 0.95 }).addTo(map);
          map.fitBounds(currentRouteLayer.getBounds(), { padding: [40, 40] });
          if (distanceEl) distanceEl.textContent = `${(route.distance / 1000).toFixed(1)} km`;
          if (durationEl) durationEl.textContent = `${Math.max(1, Math.round(route.duration / 60))} min`;
          if (status) status.textContent = "Ruta vial calculada y trazada exitosamente hacia FluxGuard.";
          routeDrawn = true;
        }
      }
    } catch (_) {
      routeDrawn = false;
    }

    if (!routeDrawn) {
      // Fallback confiable: Trazo directo + cálculo Haversine
      const dist = haversineKm(userCoords[0], userCoords[1], destination[0], destination[1]);
      const roadKm = +(dist * 1.25).toFixed(1);
      const roadMin = Math.max(2, Math.round(roadKm * 1.15));

      currentRouteLayer = L.polyline([userCoords, destination], {
        color: "#b9a7ff",
        weight: 5,
        opacity: 0.95,
        dashArray: "8, 10"
      }).addTo(map);

      map.fitBounds(currentRouteLayer.getBounds(), { padding: [45, 45] });
      if (distanceEl) distanceEl.textContent = `${roadKm} km`;
      if (durationEl) durationEl.textContent = `${roadMin} min`;
      if (status) status.textContent = "Ruta directa trazada correctamente hacia FluxGuard (Mixquiahuala).";
    }
  }

  document.getElementById("btn-reset-map")?.addEventListener("click", () => {
    if (currentRouteLayer) {
      map.removeLayer(currentRouteLayer);
      currentRouteLayer = null;
    }
    if (userMarker) {
      map.removeLayer(userMarker);
      userMarker = null;
    }
    map.setView(destination, 14);
    if (status) status.textContent = "Vista del mapa restablecida en FluxGuard.";
    if (distanceEl) distanceEl.textContent = "-- km";
    if (durationEl) durationEl.textContent = "-- min";
  });

  locate?.addEventListener("click", () => {
    locate.disabled = true;
    locate.innerHTML = `<span class="spinner-border spinner-border-sm me-1"></span> CALCULANDO...`;
    if (status) status.textContent = "Obteniendo ubicación y calculando trazo...";

    if (!navigator.geolocation) {
      locate.disabled = false;
      locate.innerHTML = `<i class="bi bi-crosshair"></i> USAR MI UBICACIÓN`;
      renderRoute([20.11697, -98.73329]); // Demo Pachuca
      return;
    }

    navigator.geolocation.getCurrentPosition(
      async position => {
        const user = [position.coords.latitude, position.coords.longitude];
        await renderRoute(user);
        locate.disabled = false;
        locate.innerHTML = `<i class="bi bi-crosshair"></i> USAR MI UBICACIÓN`;
      },
      async () => {
        if (status) status.textContent = "Sin acceso a GPS local. Trazando ruta demostrativa desde Pachuca...";
        await renderRoute([20.11697, -98.73329]);
        locate.disabled = false;
        locate.innerHTML = `<i class="bi bi-crosshair"></i> USAR MI UBICACIÓN`;
      },
      { enableHighAccuracy: true, timeout: 8000 }
    );
  });
}

function initContactForm() {
  const form = document.getElementById("prospect-form");
  if (!form) return;

  // Precargar datos si el usuario ya inició sesión
  const rawUser = localStorage.getItem("usuario");
  if (rawUser) {
    try {
      const u = JSON.parse(rawUser);
      const nom = document.getElementById("prospect-nombre");
      const ape = document.getElementById("prospect-apellido");
      const mail = document.getElementById("prospect-correo");
      if (nom && !nom.value && u.nombre) nom.value = u.nombre;
      if (ape && !ape.value && u.apellido) ape.value = u.apellido;
      if (mail && !mail.value && u.correo) mail.value = u.correo;
    } catch (_) {}
  }

  // Preseleccionar interés si viene en la URL (?interes=Prototipo o ?plan=standard)
  const params = new URLSearchParams(window.location.search);
  const interesParam = params.get("interes");
  const planParam = params.get("plan");
  const selectInteres = document.getElementById("prospect-interes");
  if (selectInteres) {
    if (interesParam) {
      selectInteres.value = interesParam;
    } else if (planParam) {
      selectInteres.value = planParam.toLowerCase() === "prototipo" ? "Prototipo" : "Implementacion";
    }
  }

  const alertBox = document.getElementById("prospect-alert");
  const button = document.getElementById("btn-submit-prospect");

  form.addEventListener("submit", async event => {
    event.preventDefault();
    if (!document.getElementById("prospect-acepta")?.checked) {
      return showAlert(alertBox, "danger", "Debes aceptar el uso de tus datos de contacto para continuar.");
    }
    const value = id => document.getElementById(id)?.value.trim() || "";
    const payload = {
      nombre: value("prospect-nombre"),
      apellido: value("prospect-apellido"),
      correo: value("prospect-correo"),
      telefono: value("prospect-telefono"),
      empresa: value("prospect-empresa"),
      cargo: value("prospect-cargo"),
      sector: document.getElementById("prospect-sector")?.value || "",
      interes: document.getElementById("prospect-interes")?.value || "",
      mensaje: value("prospect-mensaje"),
      // Campo trampa oculto: una persona nunca lo llena
      sitioWeb: document.getElementById("prospect-sitio-web")?.value || ""
    };

    if (button) {
      button.disabled = true;
      button.innerHTML = `<span class="spinner-border spinner-border-sm me-2"></span> ENVIANDO...`;
    }

    try {
      // Solo se confirma si el servidor guardó la solicitud: así nadie cree que lo contactarán cuando no fue así
      const response = await fetch(`${API_BASE}/api/prospectos`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      }).catch(() => null);
      if (!response) {
        throw new Error("No se pudo conectar con el servidor. Revisa tu conexión e intenta de nuevo.");
      }
      const data = await response.json().catch(() => null);
      if (!response.ok || !data?.success) {
        throw new Error(data?.message || "No se pudo enviar tu solicitud. Intenta de nuevo en unos momentos.");
      }

      showAlert(alertBox, "success", escapeHtml(data.message || "Recibimos tu solicitud. Te contactaremos pronto."));
      form.reset();

      // Restaurar datos de sesión si existen
      if (rawUser) {
        try {
          const u = JSON.parse(rawUser);
          if (document.getElementById("prospect-nombre")) document.getElementById("prospect-nombre").value = u.nombre || "";
          if (document.getElementById("prospect-apellido")) document.getElementById("prospect-apellido").value = u.apellido || "";
          if (document.getElementById("prospect-correo")) document.getElementById("prospect-correo").value = u.correo || "";
        } catch (_) {}
      }
    } catch (error) {
      showAlert(alertBox, "danger", escapeHtml(error.message || "No se pudo conectar con el servidor."));
    } finally {
      if (button) {
        button.disabled = false;
        button.innerHTML = `<span>ENVIAR SOLICITUD</span> <i class="bi bi-send-fill ms-2"></i>`;
      }
    }
  });
}

function validarPassword(pwd) {
  if (!pwd || pwd.length < 8) {
    return "La contraseña debe tener al menos 8 caracteres.";
  }
  if (!/[A-Z]/.test(pwd)) {
    return "La contraseña debe incluir al menos una letra mayúscula (A-Z).";
  }
  if (!/[!@#$%^&*(),.?":{}|<>\_\-\\\/\[\]~`+=]/.test(pwd)) {
    return "La contraseña debe incluir al menos un carácter especial (ej. !@#$%&*).";
  }
  return null;
}

function initAuthForms() {
  const params = new URLSearchParams(window.location.search);
  const solicitar = params.get("solicitar");
  const plan = params.get("plan");
  const redirect = params.get("redirect");

  // LOGIN FORM
  const loginForm = document.getElementById("login-form");
  if (loginForm) {
    const alertBox = document.getElementById("login-alert");
    const button = document.getElementById("btn-login-submit");

    if (params.get("creada") === "1") {
      showAlert(alertBox, "success", "Cuenta creada exitosamente. Ahora inicia sesión con tus credenciales.");
    }

    // Resultado del enlace de verificación enviado por correo (Brevo)
    const verificacion = params.get("verificacion");
    if (verificacion === "ok") {
      showAlert(alertBox, "success", "<strong>¡Correo verificado!</strong> Tu cuenta está activa. Ya puedes iniciar sesión.");
    } else if (verificacion === "expirado") {
      showAlert(alertBox, "danger", "El enlace de verificación expiró. Inicia sesión con tu correo y contraseña para recibir uno nuevo.");
    } else if (verificacion === "invalido") {
      showAlert(alertBox, "danger", "El enlace de verificación no es válido o ya fue usado. Si ya verificaste tu cuenta, inicia sesión.");
    }

    if (solicitar === "prototipo" || plan === "prototipo") {
      showAlert(alertBox, "info", "<strong>Solicitud de Prototipo:</strong> Inicia sesión para continuar al pago y activación de tu kit piloto. ¿No tienes cuenta? <a href='crear-cuenta.html?solicitar=plan&plan=prototipo&redirect=" + encodeURIComponent(redirect || "pago.html?plan=prototipo") + "' class='text-white text-decoration-underline fw-bold'>Crea tu cuenta aquí</a>.");
    } else if (solicitar === "plan" || plan) {
      const planNombre = plan ? plan.toUpperCase() : "SELECCIONADO";
      showAlert(alertBox, "info", `<strong>Plan ${planNombre}:</strong> Inicia sesión para continuar al pago y activación de tu suscripción. ¿No tienes cuenta? <a href='crear-cuenta.html?solicitar=plan&plan=${plan || "standard"}&redirect=${encodeURIComponent(redirect || `pago.html?plan=${plan || "standard"}`)}' class='text-white text-decoration-underline fw-bold'>Crea tu cuenta aquí</a>.`);
    }

    const createLink = document.querySelector(".login-register a[href*='crear-cuenta']");
    if (createLink) {
      const queryStr = window.location.search;
      if (queryStr) createLink.href = "crear-cuenta.html" + queryStr;
    }

    // Botón para limpiar todos los datos y correos en caché
    document.getElementById("btn-reset-cache")?.addEventListener("click", () => {
      cerrarSesion();
      localStorage.removeItem("fluxg_usuarios");
      localStorage.removeItem("fluxguard_active_subscription");
      showAlert(alertBox, "info", "Se han borrado todos los correos y sesiones guardadas. Ahora el sistema está completamente limpio.");
    });

    loginForm.addEventListener("submit", async event => {
      event.preventDefault();
      const email = document.getElementById("login-email")?.value.trim().toLowerCase() || "";
      const password = document.getElementById("login-password")?.value || "";

      // Validación estricta de contraseña en frontend
      const pwdError = validarPassword(password);
      if (pwdError) {
        return showAlert(alertBox, "danger", pwdError);
      }

      if (button) {
        button.disabled = true;
        button.innerHTML = `<span class="spinner-border spinner-border-sm me-2"></span> VERIFICANDO...`;
      }

      try {
        // Las cuentas solo se validan en el backend (Spring Boot / MongoDB Atlas)
        const response = await fetch(`${API_BASE}/api/auth/login`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email, password })
        }).catch(() => null);
        if (!response) {
          throw new Error("No se pudo conectar con el servidor. Intenta de nuevo en unos momentos.");
        }

        const data = await response.json().catch(() => null);
        if (data && data.requiereVerificacion) {
          // Cuenta creada pero sin verificar su correo
          return mostrarVerificacionPendiente(alertBox, "danger", data.message, data.correo);
        }
        if (!response.ok || !data || !data.success) {
          throw new Error(escapeHtml(data?.message || "No se pudo iniciar sesión. Intenta de nuevo."));
        }

        // La suscripción viene del servidor junto con el usuario
        guardarSesion(data.token, data.usuario);
        await fusionarCarritoInvitado();

        const destino = rutaSegura(redirect);
        if (destino) {
          window.location.href = destino;
        } else if (solicitar === "prototipo" || plan === "prototipo") {
          window.location.href = "pago.html?plan=prototipo";
        } else if (solicitar === "plan" && plan) {
          window.location.href = `pago.html?plan=${plan}`;
        } else if (data.usuario.suscripcion) {
          window.location.href = "dashboard.html";
        } else {
          window.location.href = "index.html";
        }
      } catch (error) {
        showAlert(alertBox, "danger", error.message || "No se pudo conectar con el servidor.");
      } finally {
        if (button) {
          button.disabled = false;
          button.innerHTML = `ENTRAR A FLUXGUARD <i class="bi bi-arrow-up-right ms-2"></i>`;
        }
      }
    });
  }

  // REGISTRO FORM
  const registerForm = document.getElementById("create-account-form");
  if (registerForm) {
    const alertBox = document.getElementById("register-alert");
    const button = document.getElementById("btn-register-submit");

    if (solicitar === "prototipo" || plan === "prototipo") {
      showAlert(alertBox, "info", "<strong>Solicitud de Prototipo:</strong> Crea tu cuenta gratuita para continuar con el kit piloto de FluxGuard.");
    } else if (solicitar === "plan" || plan) {
      const planNombre = plan ? plan.toUpperCase() : "";
      showAlert(alertBox, "info", `<strong>Registro de Usuario ${planNombre}:</strong> Crea tu cuenta para continuar a la activación de tu suscripción.`);
    }

    // Actualizar enlace a login para preservar parámetros
    const loginLink = document.querySelector(".login-register a[href*='login']");
    if (loginLink) {
      const queryStr = window.location.search;
      if (queryStr) loginLink.href = "login.html" + queryStr;
    }

    registerForm.addEventListener("submit", async event => {
      event.preventDefault();
      const get = id => document.getElementById(id)?.value.trim() || "";
      const email = get("reg-email").toLowerCase();
      const password = document.getElementById("reg-password")?.value || "";
      const confirm = document.getElementById("reg-confirm")?.value || "";

      if (password !== confirm) return showAlert(alertBox, "danger", "Las contraseñas no coinciden.");

      // Validación estricta: 8+ caracteres, mayúscula y carácter especial
      const pwdError = validarPassword(password);
      if (pwdError) return showAlert(alertBox, "danger", pwdError);

      // Consentimiento expreso: el servidor también rechaza el registro sin él
      const aceptaPrivacidad = document.getElementById("reg-privacidad")?.checked === true;
      if (!aceptaPrivacidad) {
        return showAlert(alertBox, "danger", "Debes leer y aceptar el Aviso de Privacidad para crear tu cuenta.");
      }

      if (button) {
        button.disabled = true;
        button.innerHTML = `<span class="spinner-border spinner-border-sm me-2"></span> CREANDO CUENTA...`;
      }

      try {
        // Las cuentas solo se guardan en el backend (Spring Boot / MongoDB Atlas)
        const response = await fetch(`${API_BASE}/api/auth/register`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            nombre: get("reg-nombre"),
            apellido: get("reg-apellido"),
            email: email,
            password: password,
            aceptaPrivacidad
          })
        }).catch(() => null);
        if (!response) {
          throw new Error("No se pudo conectar con el servidor. Intenta de nuevo en unos momentos.");
        }

        const data = await response.json().catch(() => null);
        if (data && data.requiereVerificacion) {
          // Cuenta guardada en MongoDB: no se inicia sesión hasta que el usuario abra el enlace enviado con Brevo
          if (data.success) registerForm.reset();
          return mostrarVerificacionPendiente(alertBox, data.success ? "success" : "danger", data.message, data.correo);
        }
        if (!response.ok || !data || !data.success) {
          // Ej. cuenta duplicada o contraseña que no cumple las reglas
          throw new Error(escapeHtml(data?.message || "No se pudo crear la cuenta. Intenta de nuevo."));
        }

        registerForm.reset();
        showAlert(alertBox, "success", escapeHtml(data.message || "Cuenta creada. Ya puedes iniciar sesión."));
      } catch (error) {
        showAlert(alertBox, "danger", error.message || "No se pudo conectar con el servidor.");
      } finally {
        if (button) {
          button.disabled = false;
          button.innerHTML = `CREAR MI CUENTA <i class="bi bi-arrow-up-right ms-2"></i>`;
        }
      }
    });
  }
}

// Muestra el aviso de "verifica tu correo" con un botón para reenviar el enlace (POST /api/auth/resend)
function mostrarVerificacionPendiente(alertBox, type, message, correo) {
  showAlert(alertBox, type, `${escapeHtml(message)}
    <div class="mt-2">¿No te llegó? Revisa tu carpeta de spam o
      <button type="button" class="btn btn-link p-0 align-baseline text-white fw-bold" style="font-size:inherit;" data-reenviar-verificacion>reenviar correo</button>.
    </div>`);

  alertBox?.querySelector("[data-reenviar-verificacion]")?.addEventListener("click", async event => {
    const btn = event.currentTarget;
    btn.disabled = true;
    btn.textContent = "enviando...";
    try {
      const response = await fetch(`${API_BASE}/api/auth/resend`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: correo })
      });
      const resJson = await response.json().catch(() => null);
      const mensaje = resJson?.message || "No se pudo reenviar el correo. Intenta de nuevo en unos momentos.";
      if (response.ok && !resJson?.requiereVerificacion) {
        return showAlert(alertBox, "success", escapeHtml(mensaje)); // la cuenta ya estaba verificada
      }
      mostrarVerificacionPendiente(alertBox, response.ok ? "success" : "danger", mensaje, correo);
    } catch (_) {
      mostrarVerificacionPendiente(alertBox, "danger", "No se pudo conectar con el servidor.", correo);
    }
  });
}

function initPasswordToggles() {
  document.addEventListener("click", event => {
    const btn = event.target.closest(".password-toggle-btn");
    if (!btn) return;
    event.preventDefault();
    event.stopPropagation();
    const targetId = btn.getAttribute("data-target");
    if (!targetId) return;
    const input = document.getElementById(targetId);
    if (!input) return;
    const icon = btn.querySelector("i");
    if (input.type === "password") {
      input.type = "text";
      if (icon) {
        icon.className = "bi bi-eye-slash";
      }
    } else {
      input.type = "password";
      if (icon) {
        icon.className = "bi bi-eye";
      }
    }
  });
}

function initPlanes() {
  const btnProto = document.getElementById("btn-solicitar-prototipo");
  if (btnProto) {
    btnProto.addEventListener("click", (e) => {
      e.preventDefault();
      // El kit piloto se puede comprar sin cuenta
      window.location.href = "pago.html?plan=prototipo";
    });
  }

  document.querySelectorAll(".plan-button").forEach(btn => {
    btn.addEventListener("click", (e) => {
      e.preventDefault();
      let href = btn.getAttribute("href") || "";
      let plan = "standard";
      if (href.includes("free") || btn.textContent.toLowerCase().includes("gratis")) plan = "free";
      else if (href.includes("ultimate") || btn.textContent.toLowerCase().includes("ventas") || btn.textContent.toLowerCase().includes("ultimate")) plan = "ultimate";
      else if (href.includes("standard")) plan = "standard";

      const target = `pago.html?plan=${plan}`;
      const rawUser = localStorage.getItem("usuario");
      if (!rawUser) {
        window.location.href = `login.html?solicitar=plan&plan=${plan}&redirect=${encodeURIComponent(target)}`;
      } else {
        window.location.href = target;
      }
    });
  });
}

function showAlert(container, type, message) {
  if (!container) return;
  container.className = `alert alert-${type} py-2 px-3 mb-3 border-0`;
  container.style.fontSize = "13px";
  container.innerHTML = `<i class="bi ${type === "success" ? "bi-check-circle-fill" : type === "info" ? "bi-info-circle-fill" : "bi-exclamation-triangle-fill"} me-2"></i>${message}`;
  container.classList.remove("d-none");
}

function escapeHtml(value) {
  return String(value).replace(/[&<>'"]/g, char => ({"&":"&amp;","<":"&lt;",">":"&gt;","'":"&#39;","\"":"&quot;"}[char]));
}

// ===== Carrito de compras =====
// El carrito vive en el servidor (/api/carrito) ligado a la sesión; aquí solo se dibuja.

const dinero = n => "$" + Number(n || 0).toLocaleString("es-MX");

function initCarrito() {
  // Va al inicio del grupo de botones de sesión de la barra (cada página tiene su propia versión)
  const authAction = document.getElementById("navbar-auth-action") || document.querySelector("header.navbar .navbar-actions");
  if (authAction && !document.getElementById("navbar-cart-btn")) {
    authAction.insertAdjacentHTML("afterbegin", `
      <button type="button" class="navbar-cart" id="navbar-cart-btn" aria-label="Abrir carrito">
        <i class="bi bi-cart3"></i><span class="navbar-cart-count" id="navbar-cart-count" hidden>0</span>
      </button>`);
    document.getElementById("navbar-cart-btn").addEventListener("click", abrirCarrito);
  }

  if (!document.getElementById("carritoPanel")) {
    document.body.insertAdjacentHTML("beforeend", `
      <div class="offcanvas offcanvas-end cart-panel" tabindex="-1" id="carritoPanel" aria-labelledby="carritoPanelTitulo">
        <div class="offcanvas-header">
          <div>
            <span class="cart-panel-kicker">FLUXGUARD / TIENDA</span>
            <h2 class="offcanvas-title" id="carritoPanelTitulo">Tu carrito</h2>
          </div>
          <button type="button" class="btn-close btn-close-white" data-bs-dismiss="offcanvas" aria-label="Cerrar"></button>
        </div>
        <div class="offcanvas-body" id="carritoPanelBody"></div>
      </div>`);
  }

  // Con o sin sesión: sin sesión se usa el carrito de invitado
  cargarCarrito().then(renderPanelCarrito).catch(() => {});
}

async function cargarCarrito() {
  const response = await apiFetch("/api/carrito");
  const data = await response.json().catch(() => null);
  if (!response.ok || !data?.success) throw new Error(data?.message || "No se pudo cargar el carrito.");
  return data.carrito;
}

// Agrega un producto al carrito (con sesión o como invitado).
async function agregarAlCarrito(productoId, cantidad = 1) {
  const enviar = () => apiFetch("/api/carrito/items", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ productoId, cantidad })
  });
  let response = await enviar();
  if (response.status === 401) {
    // La sesión venció: apiFetch ya la olvidó, se reintenta como invitado
    response = await enviar();
  }
  const data = await response.json().catch(() => null);
  if (!response.ok || !data?.success) throw new Error(data?.message || "No se pudo agregar al carrito.");
  renderPanelCarrito(data.carrito);
  abrirCarrito();
  return data.carrito;
}

async function cambiarCantidadCarrito(productoId, cantidad) {
  const response = await apiFetch(`/api/carrito/items/${encodeURIComponent(productoId)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ cantidad })
  });
  const data = await response.json().catch(() => null);
  if (!response.ok || !data?.success) throw new Error(data?.message || "No se pudo actualizar el carrito.");
  renderPanelCarrito(data.carrito);
  document.dispatchEvent(new CustomEvent("carrito:actualizado", { detail: data.carrito }));
  return data.carrito;
}

function abrirCarrito() {
  const panel = document.getElementById("carritoPanel");
  if (!panel || typeof bootstrap === "undefined") return;
  bootstrap.Offcanvas.getOrCreateInstance(panel).show();
}

function actualizarContadorCarrito(carrito) {
  const badge = document.getElementById("navbar-cart-count");
  if (!badge) return;
  const n = carrito?.cantidadArticulos || 0;
  badge.textContent = n > 99 ? "99+" : String(n);
  badge.hidden = n === 0;
}

// HTML de las líneas del carrito; lo usan el panel lateral y carrito.html
function lineasCarritoHtml(carrito, editable = true) {
  return carrito.lineas.map(l => {
    const esPlan = l.categoria === "PLAN";
    const controles = editable ? `
        <div class="cart-qty">
          ${esPlan ? "" : `<button type="button" data-accion="menos" aria-label="Quitar uno"><i class="bi bi-dash"></i></button>
          <span>${l.cantidad}</span>
          <button type="button" data-accion="mas" aria-label="Agregar uno"${l.cantidad >= l.maxCantidad ? " disabled" : ""}><i class="bi bi-plus"></i></button>`}
          <button type="button" class="cart-remove" data-accion="quitar">Quitar</button>
        </div>` : `<span>Cantidad: ${l.cantidad}</span>`;
    return `
    <div class="cart-line" data-producto="${escapeHtml(l.productoId)}">
      <div class="cart-line-icon"><i class="bi ${escapeHtml(l.icono)}"></i></div>
      <div class="cart-line-info">
        <strong>${escapeHtml(l.nombre)}</strong>
        <span>${dinero(l.precio)} · ${escapeHtml(l.unidad)}</span>
        ${controles}
      </div>
      <b class="cart-line-total">${dinero(l.importe)}</b>
    </div>`;
  }).join("");
}

// Conecta los botones +/−/quitar de un contenedor con líneas del carrito
function conectarLineasCarrito(contenedor, carrito, alertBox) {
  contenedor.querySelectorAll(".cart-line button[data-accion]").forEach(btn => {
    btn.addEventListener("click", async () => {
      const id = btn.closest(".cart-line").dataset.producto;
      const linea = carrito.lineas.find(l => l.productoId === id);
      if (!linea) return;
      const accion = btn.dataset.accion;
      const nueva = accion === "mas" ? linea.cantidad + 1 : accion === "menos" ? linea.cantidad - 1 : 0;
      btn.disabled = true;
      try {
        await cambiarCantidadCarrito(id, nueva);
      } catch (error) {
        if (alertBox) showAlert(alertBox, "danger", escapeHtml(error.message));
        btn.disabled = false;
      }
    });
  });
}

function renderPanelCarrito(carrito) {
  actualizarContadorCarrito(carrito);
  const body = document.getElementById("carritoPanelBody");
  if (!body) return;

  if (!carrito || carrito.lineas.length === 0) {
    body.innerHTML = `
      <div class="cart-empty">
        <i class="bi bi-cart3"></i>
        <p>Tu carrito está vacío.</p>
        <a href="tienda.html" class="plan-button">IR A LA TIENDA <i class="bi bi-arrow-up-right"></i></a>
      </div>`;
    return;
  }

  body.innerHTML = `
    <div id="carritoPanelAlert" class="d-none"></div>
    <div class="cart-lines">${lineasCarritoHtml(carrito)}</div>
    <div class="cart-summary">
      <div class="cart-total"><span>TOTAL · IVA INCLUIDO</span><strong>${dinero(carrito.total)}</strong></div>
      <a href="carrito.html" class="plan-button cart-checkout">IR A PAGAR <i class="bi bi-arrow-up-right"></i></a>
      <a href="tienda.html" class="cart-continue">Seguir comprando</a>
    </div>`;
  const alertBox = document.getElementById("carritoPanelAlert");
  if (carrito.avisos?.length) showAlert(alertBox, "info", carrito.avisos.map(escapeHtml).join("<br>"));
  conectarLineasCarrito(body, carrito, alertBox);
}

// ===== Sobrepantallas: Aviso de Privacidad, Políticas de compra e Historial de compras =====
// Cualquier elemento con data-sobrepantalla="privacidad" | "politicas" | "historial" abre la ventana.
// En políticas, data-seccion="compra" | "envio" | "devoluciones" elige la pestaña inicial.

const FLUXGUARD_CORREO = "fluxguardcorporation@gmail.com";
const FLUXGUARD_DOMICILIO = "Paseo del Agrarismo 2000, Carr. Mixquiahuala–Tula km 2.5, Mixquiahuala de Juárez, Hidalgo, México";

function initSobrepantallas() {
  document.addEventListener("click", event => {
    const disparador = event.target.closest("[data-sobrepantalla]");
    if (!disparador) return;
    event.preventDefault();
    const tipo = disparador.dataset.sobrepantalla;
    if (tipo === "privacidad") abrirAvisoPrivacidad();
    else if (tipo === "politicas") abrirPoliticas(disparador.dataset.seccion);
    else if (tipo === "historial") abrirHistorialCompras();
  });
}

// Una sola ventana modal de Bootstrap que se reutiliza para todo el contenido
function abrirSobrepantalla({ kicker, titulo, cuerpo, pie }) {
  let modal = document.getElementById("fluxModal");
  if (!modal) {
    document.body.insertAdjacentHTML("beforeend", `
      <div class="modal fade flux-modal" id="fluxModal" tabindex="-1" aria-labelledby="fluxModalTitulo" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable modal-lg">
          <div class="modal-content">
            <div class="modal-header">
              <div>
                <span class="flux-modal-kicker" id="fluxModalKicker"></span>
                <h2 class="modal-title" id="fluxModalTitulo"></h2>
              </div>
              <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Cerrar"></button>
            </div>
            <div class="modal-body" id="fluxModalCuerpo"></div>
            <div class="modal-footer" id="fluxModalPie"></div>
          </div>
        </div>
      </div>`);
    modal = document.getElementById("fluxModal");
  }
  const cuerpoEl = document.getElementById("fluxModalCuerpo");
  const pieEl = document.getElementById("fluxModalPie");
  document.getElementById("fluxModalKicker").textContent = kicker;
  document.getElementById("fluxModalTitulo").textContent = titulo;
  cuerpoEl.innerHTML = cuerpo;
  cuerpoEl.scrollTop = 0;
  pieEl.innerHTML = pie || `<button type="button" class="plan-button" data-bs-dismiss="modal">CERRAR</button>`;
  bootstrap.Modal.getOrCreateInstance(modal).show();
  return { modal, cuerpo: cuerpoEl, pie: pieEl };
}

function cerrarSobrepantalla() {
  const modal = document.getElementById("fluxModal");
  if (modal) bootstrap.Modal.getOrCreateInstance(modal).hide();
}

// --- Aviso de Privacidad Integral ---
// Si cambias el texto, actualiza también AuthController.VERSION_AVISO_PRIVACIDAD para registrar la nueva versión.
const AVISO_PRIVACIDAD_HTML = `
  <p class="flux-legal-fecha">Última actualización: 27 de octubre de 2026</p>

  <h3>1. Identidad y domicilio del responsable</h3>
  <p>FluxGuard, con domicilio en ${FLUXGUARD_DOMICILIO}, es responsable del uso y protección de tus datos personales, conforme a la Ley Federal de Protección de Datos Personales en Posesión de los Particulares. Para cualquier asunto relacionado con este aviso escríbenos a <a href="mailto:${FLUXGUARD_CORREO}">${FLUXGUARD_CORREO}</a>.</p>

  <h3>2. Datos personales que recabamos</h3>
  <ul>
    <li><strong>Identificación y contacto:</strong> nombre, apellido y correo electrónico. Si usas el formulario de contacto, también teléfono, empresa, cargo y sector.</li>
    <li><strong>Cuenta:</strong> contraseña (se guarda cifrada y nadie en FluxGuard puede leerla), fecha de registro y fecha en que aceptaste este aviso.</li>
    <li><strong>Compras:</strong> productos, cantidades, montos, folios, fechas y estado de cada pedido. Si compras sin cuenta, el nombre y correo que te solicite PayPal.</li>
    <li><strong>Reseñas:</strong> la calificación y el comentario que decidas publicar sobre un producto que compraste. Junto a tu reseña mostramos públicamente solo tu nombre y la inicial de tu apellido.</li>
  </ul>
  <p><strong>No guardamos datos de tarjetas.</strong> Los pagos se capturan directamente en las páginas de PayPal o Stripe. Tampoco recabamos datos personales sensibles.</p>

  <h3>3. ¿Para qué usamos tus datos?</h3>
  <p><strong>Finalidades primarias</strong> (necesarias para darte el servicio):</p>
  <ul>
    <li>Crear y administrar tu cuenta y verificar tu correo electrónico.</li>
    <li>Procesar tus compras, pagos, envíos, garantías, cambios y devoluciones.</li>
    <li>Activar y prestar el plan de monitoreo que contrates.</li>
    <li>Atender tus solicitudes de información y soporte técnico.</li>
    <li>Publicar las reseñas que escribas sobre tus compras.</li>
  </ul>
  <p><strong>Finalidades secundarias</strong> (no son necesarias para el servicio): enviarte información de nuevos productos, promociones y encuestas de satisfacción. Si no quieres que usemos tus datos para esto, escríbenos a <a href="mailto:${FLUXGUARD_CORREO}">${FLUXGUARD_CORREO}</a>. Negarte no afecta los servicios que contrates.</p>

  <h3>4. ¿Con quién compartimos tus datos?</h3>
  <p>Solo con proveedores que nos ayudan a operar el sitio y que tratan los datos por cuenta nuestra: alojamiento del sitio y base de datos, envío de correos electrónicos, procesadores de pago (PayPal y Stripe) y la paquetería que entrega tu pedido. <strong>No vendemos ni rentamos tus datos personales.</strong> Solo los entregaremos a una autoridad cuando una ley o una orden judicial lo exijan.</p>

  <h3>5. Derechos ARCO</h3>
  <p>Tienes derecho a <strong>Acceder</strong> a tus datos, <strong>Rectificarlos</strong> si son inexactos, <strong>Cancelarlos</strong> cuando consideres que no se requieren y <strong>Oponerte</strong> a su uso para fines específicos. Envía tu solicitud a <a href="mailto:${FLUXGUARD_CORREO}">${FLUXGUARD_CORREO}</a> con tu nombre, el correo de tu cuenta, el derecho que quieres ejercer, una descripción clara de lo que solicitas y una identificación oficial que acredite tu identidad. Te responderemos en los plazos que establece la ley.</p>

  <h3>6. Revocación del consentimiento y limitación del uso</h3>
  <p>Puedes revocar en cualquier momento el consentimiento que nos diste o pedirnos que limitemos el uso de tus datos, por el mismo medio del punto anterior. Si la revocación impide prestarte un servicio (por ejemplo, mantener tu cuenta activa), te lo informaremos.</p>

  <h3>7. Almacenamiento en tu navegador</h3>
  <p>El sitio guarda en el almacenamiento local de tu navegador tu sesión iniciada y el carrito de compras de invitado. No usamos cookies de publicidad ni de rastreo. Puedes borrar estos datos desde la configuración de tu navegador; al hacerlo se cerrará tu sesión.</p>

  <h3>8. Cambios a este aviso</h3>
  <p>Publicaremos cualquier cambio en este sitio. Si cambian las finalidades para las que usamos tus datos, te pediremos de nuevo tu consentimiento.</p>

  <h3>9. Autoridad</h3>
  <p>Si consideras que tu derecho a la protección de datos personales fue vulnerado, puedes acudir ante la autoridad competente en materia de protección de datos personales.</p>`;

function abrirAvisoPrivacidad() {
  const casilla = document.getElementById("reg-privacidad");
  // En el registro, la ventana ofrece marcar la casilla al terminar de leer
  const pie = casilla
    ? `<button type="button" class="plan-button flux-modal-secundario" data-bs-dismiss="modal">CERRAR</button>
       <button type="button" class="plan-button cart-checkout" id="btn-aceptar-privacidad">LEÍ Y ACEPTO EL AVISO <i class="bi bi-check2"></i></button>`
    : null;
  const { pie: pieEl } = abrirSobrepantalla({
    kicker: "FLUXGUARD / DATOS PERSONALES",
    titulo: "Aviso de Privacidad Integral",
    cuerpo: `<div class="flux-legal">${AVISO_PRIVACIDAD_HTML}</div>`,
    pie
  });
  pieEl.querySelector("#btn-aceptar-privacidad")?.addEventListener("click", () => {
    casilla.checked = true;
    casilla.dispatchEvent(new Event("change"));
    cerrarSobrepantalla();
  });
}

// --- Políticas de Compra, Envío y Devoluciones ---
const POLITICAS = {
  compra: {
    icono: "bi-bag-check",
    nombre: "COMPRA",
    html: `
      <h3>Precios y pagos</h3>
      <ul>
        <li>Todos los precios están en pesos mexicanos (MXN) e incluyen IVA. Se cobra el total que ves en tu carrito al momento de pagar.</li>
        <li>Puedes pagar con tarjeta de crédito o débito (procesada por Stripe) o con PayPal. FluxGuard no recibe ni guarda los datos de tu tarjeta.</li>
        <li>Tu pedido queda confirmado cuando el procesador de pago confirma el cobro. En ese momento recibes un folio (por ejemplo FLX-P12345678) que puedes consultar en <strong>Mis compras</strong>.</li>
        <li>Si necesitas factura, escríbenos a <a href="mailto:${FLUXGUARD_CORREO}">${FLUXGUARD_CORREO}</a> con tu folio y tus datos fiscales.</li>
      </ul>
      <h3>Reglas del carrito</h3>
      <ul>
        <li>Puedes comprar hardware y servicios como invitado. Los planes de monitoreo requieren una cuenta, porque se activan en ella.</li>
        <li>Las personalizaciones y la garantía extendida son para el Kit Piloto: puedes agregar una de cada una por kit que tengas en el carrito o que ya hayas comprado.</li>
        <li>Solo puedes tener un plan a la vez. El pago de un plan lo activa por un mes; para continuar, lo renuevas desde Planes.</li>
        <li>La disponibilidad depende del stock que se muestra en la tienda. Si un producto se agota antes de que pagues, ajustamos tu carrito y te avisamos antes del cobro.</li>
      </ul>`
  },
  envio: {
    icono: "bi-truck",
    nombre: "ENVÍO",
    html: `
      <h3>Qué se envía</h3>
      <p>Solo el hardware: Kit Piloto, carcasa, pantalla y batería. Los planes de monitoreo y la garantía extendida se activan en línea y no requieren envío.</p>
      <h3>Cobertura y costo</h3>
      <ul>
        <li>Enviamos a toda la República Mexicana por paquetería.</li>
        <li>El costo del envío está incluido en el precio de los productos: no hay cargos adicionales al total de tu carrito.</li>
      </ul>
      <h3>Tiempos</h3>
      <ul>
        <li>Preparamos tu pedido en 1 a 3 días hábiles después de confirmado el pago. La carcasa personalizada puede requerir hasta 5 días hábiles más por su impresión en 3D.</li>
        <li>La paquetería entrega normalmente en 3 a 7 días hábiles, según tu ubicación.</li>
        <li>Te escribiremos al correo de tu pedido para confirmar tu dirección de entrega y compartirte el número de guía.</li>
      </ul>
      <h3>Al recibir tu paquete</h3>
      <p>Revisa que el empaque llegue en buen estado. Si llega dañado o le falta algo, avísanos dentro de las 48 horas siguientes a la entrega, con fotos y tu folio.</p>`
  },
  devoluciones: {
    icono: "bi-arrow-counterclockwise",
    nombre: "DEVOLUCIONES",
    html: `
      <h3>Cancelación de tu compra</h3>
      <p>Tienes <strong>5 días hábiles</strong> a partir de la entrega para cancelar tu compra sin ninguna responsabilidad, como lo prevé la Ley Federal de Protección al Consumidor para las ventas a distancia. El producto debe estar completo, sin uso y en su empaque original.</p>
      <h3>Garantía</h3>
      <ul>
        <li>Cada producto incluye la garantía que se indica en la tienda contra defectos de fabricación (1 año el Kit Piloto y la pantalla; 6 meses la carcasa y la batería). La garantía extendida añade un año al Kit Piloto.</li>
        <li>La garantía no cubre daños por instalación incorrecta, voltajes fuera de especificación, golpes, humedad o modificaciones al equipo.</li>
      </ul>
      <h3>Cómo solicitar una devolución o garantía</h3>
      <ol>
        <li>Escríbenos a <a href="mailto:${FLUXGUARD_CORREO}">${FLUXGUARD_CORREO}</a> con tu folio, el producto y el motivo. Si es una falla, agrega fotos o un video.</li>
        <li>Te enviaremos las instrucciones y la guía para regresar el producto.</li>
        <li>Al recibirlo y revisarlo, te reembolsamos por el mismo medio de pago o te reponemos el equipo, según corresponda.</li>
      </ol>
      <h3>Planes y servicios</h3>
      <p>Puedes dejar de renovar tu plan en cualquier momento y conservas el acceso hasta el final del periodo pagado. Si se te hizo un cobro indebido, escríbenos y lo revisamos.</p>`
  }
};

function abrirPoliticas(seccionInicial = "compra") {
  const pestanas = Object.keys(POLITICAS).map(clave => `
    <button type="button" class="shop-filter" data-politica="${clave}" aria-pressed="false">
      <i class="bi ${POLITICAS[clave].icono}"></i> ${POLITICAS[clave].nombre}
    </button>`).join("");
  const { cuerpo } = abrirSobrepantalla({
    kicker: "FLUXGUARD / ANTES DE COMPRAR",
    titulo: "Políticas de compra, envío y devoluciones",
    cuerpo: `
      <div class="shop-filters flux-modal-tabs" role="group" aria-label="Políticas">${pestanas}</div>
      <div class="flux-legal" id="fluxPoliticaContenido" aria-live="polite"></div>
      <p class="flux-legal-fecha mt-4 mb-0">Última actualización: octubre de 2026 · Dudas: <a href="mailto:${FLUXGUARD_CORREO}">${FLUXGUARD_CORREO}</a></p>`
  });
  const mostrar = clave => {
    cuerpo.querySelectorAll("[data-politica]").forEach(b => {
      const activo = b.dataset.politica === clave;
      b.classList.toggle("is-active", activo);
      b.setAttribute("aria-pressed", String(activo));
    });
    cuerpo.querySelector("#fluxPoliticaContenido").innerHTML = POLITICAS[clave].html;
  };
  cuerpo.querySelectorAll("[data-politica]").forEach(b => b.addEventListener("click", () => mostrar(b.dataset.politica)));
  mostrar(POLITICAS[seccionInicial] ? seccionInicial : "compra");
}

// --- Historial de compras ---
const METODOS_PAGO = {
  paypal_sandbox: "PayPal (modo de prueba)", paypal: "PayPal",
  stripe_test: "Tarjeta (modo de prueba)", stripe: "Tarjeta",
  paynet_sandbox: "Pago en tienda (modo de prueba)", paynet: "Pago en tienda", sin_costo: "Sin costo"
};

function nombreMetodoPago(metodo) {
  return METODOS_PAGO[metodo] || "Sin costo";
}

// Pedido apartado con ficha de pago en tienda (Paynet) que aún no se paga
function esFichaPendiente(pedido) {
  return pedido.estado === "PENDIENTE" && !!pedido.referenciaPago;
}

function fichaVencida(pedido) {
  return esFichaPendiente(pedido) && !!pedido.vencePago && new Date(pedido.vencePago) < new Date();
}

function estadoPedido(pedido) {
  if (pedido.estado === "PAGADO") return { clase: "is-ok", texto: "PAGADO" };
  if (fichaVencida(pedido)) return { clase: "is-off", texto: "FICHA VENCIDA" };
  if (esFichaPendiente(pedido)) return { clase: "is-low", texto: "ESPERANDO PAGO EN TIENDA" };
  if (pedido.estado === "PENDIENTE") return { clase: "is-low", texto: pedido.pagoId ? "EN REVISIÓN" : "PENDIENTE DE PAGO" };
  if (pedido.estado === "REEMBOLSADO") return { clase: "is-info", texto: "REEMBOLSADO" };
  if (pedido.estado === "RECHAZADO") return { clase: "is-out", texto: "RECHAZADO" };
  return { clase: "is-off", texto: "CANCELADO" };
}

async function abrirHistorialCompras() {
  const { cuerpo } = abrirSobrepantalla({
    kicker: "FLUXGUARD / MI CUENTA",
    titulo: "Historial de compras",
    cuerpo: `<div class="text-center py-4"><span class="spinner-border spinner-border-sm me-2"></span> Cargando tus pedidos...</div>`
  });

  if (!localStorage.getItem("fluxg_token")) {
    const regreso = window.location.pathname.split("/").pop() || "index.html";
    cuerpo.innerHTML = `
      <div class="cart-empty py-4">
        <i class="bi bi-person-lock"></i>
        <p>Inicia sesión para ver tu historial de compras.</p>
        <a href="login.html?redirect=${encodeURIComponent(regreso)}" class="plan-button d-inline-flex">INICIAR SESIÓN <i class="bi bi-arrow-up-right ms-2"></i></a>
      </div>`;
    return;
  }

  let pedidos;
  try {
    const response = await apiFetch("/api/pedidos");
    const data = await response.json().catch(() => null);
    if (!response.ok || !data?.success) throw new Error(data?.message || "No se pudo cargar tu historial de compras.");
    pedidos = data.pedidos || [];
  } catch (error) {
    cuerpo.innerHTML = `<div id="fluxHistorialAlert"></div>`;
    showAlert(cuerpo.querySelector("#fluxHistorialAlert"), "danger", escapeHtml(error.message));
    return;
  }

  if (pedidos.length === 0) {
    cuerpo.innerHTML = `
      <div class="cart-empty py-4">
        <i class="bi bi-receipt"></i>
        <p>Aún no tienes compras registradas.</p>
        <a href="tienda.html" class="plan-button d-inline-flex">IR A LA TIENDA <i class="bi bi-arrow-up-right ms-2"></i></a>
      </div>`;
    return;
  }

  const pagados = pedidos.filter(p => p.estado === "PAGADO");
  const totalPagado = pagados.reduce((suma, p) => suma + p.total, 0);
  const fecha = valor => valor ? new Date(valor).toLocaleDateString("es-MX", { day: "2-digit", month: "short", year: "numeric" }) : "—";
  cuerpo.innerHTML = `
    <div class="flux-historial-resumen">
      <div><span>PEDIDOS</span><strong>${pedidos.length}</strong></div>
      <div><span>PAGADOS</span><strong>${pagados.length}</strong></div>
      <div><span>TOTAL PAGADO</span><strong>${dinero(totalPagado)}</strong></div>
    </div>
    <div class="flux-historial-tabla">
      <table>
        <thead>
          <tr><th>Folio</th><th>Fecha</th><th>Productos</th><th>Método</th><th>Estado</th><th class="text-end">Total</th><th><span class="visually-hidden">Opinión</span></th></tr>
        </thead>
        <tbody>
          ${pedidos.map(p => {
            const estado = estadoPedido(p);
            const productos = (p.lineas || []).map(l => `${l.cantidad} × ${escapeHtml(l.nombre)}`).join("<br>");
            return `
              <tr>
                <td class="font-monospace">${escapeHtml(p.folio)}</td>
                <td>${escapeHtml(fecha(p.fecha))}</td>
                <td>${productos || "—"}</td>
                <td>${escapeHtml(nombreMetodoPago(p.metodoPago))}</td>
                <td><span class="shop-stock ${estado.clase}">${estado.texto}</span></td>
                <td class="text-end flux-historial-total">${dinero(p.total)}</td>
                <td>${p.estado === "PAGADO" ? `
                  <div class="flux-historial-acciones">
                    <button type="button" class="flux-opinar-btn flux-ticket-btn" data-ticket-pdf="${escapeHtml(p.folio)}"><i class="bi bi-file-earmark-pdf"></i> PDF</button>
                    <button type="button" class="flux-opinar-btn flux-ticket-btn" data-ticket="${escapeHtml(p.folio)}"><i class="bi bi-printer"></i> IMPRIMIR</button>
                    <button type="button" class="flux-opinar-btn" data-opinar="${escapeHtml(p.folio)}"><i class="bi bi-star"></i> OPINAR</button>
                  </div>` : esFichaPendiente(p) && !fichaVencida(p) ? `
                  <div class="flux-historial-acciones">
                    <a class="flux-opinar-btn flux-ticket-btn" href="carrito.html?ficha=${encodeURIComponent(p.folio)}"><i class="bi bi-upc-scan"></i> VER FICHA</a>
                  </div>` : ""}</td>
              </tr>`;
          }).join("")}
        </tbody>
      </table>
    </div>
    <p class="flux-legal-fecha mt-3 mb-0">¿Dudas con un pedido? Escríbenos a <a href="mailto:${FLUXGUARD_CORREO}">${FLUXGUARD_CORREO}</a> con tu folio. Consulta las <a href="#" data-sobrepantalla="politicas" data-seccion="devoluciones">políticas de devolución</a>.</p>`;

  cuerpo.querySelectorAll("[data-ticket]").forEach(boton => boton.addEventListener("click", () =>
    imprimirTicket(pedidos.find(p => p.folio === boton.dataset.ticket))));
  cuerpo.querySelectorAll("[data-ticket-pdf]").forEach(boton => boton.addEventListener("click", async () => {
    const original = boton.innerHTML;
    boton.disabled = true;
    boton.innerHTML = `<span class="spinner-border spinner-border-sm"></span>`;
    try {
      await descargarTicketPdf(boton.dataset.ticketPdf);
    } catch (error) {
      alert(error.message);
    } finally {
      boton.disabled = false;
      boton.innerHTML = original;
    }
  }));

  // "Opinar" cambia el contenido de la ventana por el formulario de reseñas de ese pedido
  cuerpo.querySelectorAll("[data-opinar]").forEach(boton => boton.addEventListener("click", () => {
    const pedido = pedidos.find(p => p.folio === boton.dataset.opinar);
    document.getElementById("fluxModalTitulo").textContent = "Califica tu compra";
    cuerpo.innerHTML = `
      <button type="button" class="flux-volver-btn" id="fluxVolverHistorial"><i class="bi bi-arrow-left"></i> VOLVER AL HISTORIAL</button>
      <div id="fluxResenasPedido"></div>`;
    cuerpo.scrollTop = 0;
    cuerpo.querySelector("#fluxVolverHistorial").addEventListener("click", abrirHistorialCompras);
    mostrarFormularioResenas(cuerpo.querySelector("#fluxResenasPedido"), pedido);
  }));
}


// ===== Reseñas post-compra =====
// Con sesión: formulario de estrellas y comentario por cada producto del pedido pagado.
// Sin sesión: solo un carrusel con reseñas positivas de otros clientes.

const TEXTO_ESTRELLAS = ["", "Muy malo", "Malo", "Regular", "Bueno", "Excelente"];

function estrellasHtml(calificacion) {
  return Array.from({ length: 5 }, (_, i) =>
    `<i class="bi ${i < calificacion ? "bi-star-fill" : "bi-star"}"></i>`).join("");
}

// Decide qué mostrar después de una compra confirmada
function initResenasPostCompra(contenedor, pedido) {
  if (!contenedor) return;
  if (localStorage.getItem("fluxg_token")) mostrarFormularioResenas(contenedor, pedido);
  else mostrarCarruselResenas(contenedor);
}

async function mostrarFormularioResenas(contenedor, pedido) {
  // Un formulario por producto (sin repetir) del pedido
  const productos = [];
  (pedido?.lineas || []).forEach(l => {
    if (!productos.some(p => p.productoId === l.productoId)) productos.push(l);
  });
  if (!productos.length) return;

  let yaCalificados = new Map();
  try {
    const response = await apiFetch("/api/resenas/mias");
    const data = await response.json().catch(() => null);
    if (response.status === 401) return mostrarCarruselResenas(contenedor);
    if (response.ok && data?.success) yaCalificados = new Map((data.resenas || []).map(r => [r.productoId, r]));
  } catch (_) {}

  contenedor.innerHTML = `
    <section class="review-section">
      <span class="section-eyebrow"><span></span> TU OPINIÓN</span>
      <h3 class="review-heading">¿Qué te pareció tu compra?</h3>
      <p class="review-sub">Califica cada producto. Publicaremos tu nombre y la inicial de tu apellido junto a tu opinión.</p>
      <div class="review-list">
        ${productos.map(l => yaCalificados.has(l.productoId)
          ? resenaEnviadaHtml(l.nombre, yaCalificados.get(l.productoId).calificacion, "Ya calificaste este producto.")
          : `
          <form class="review-card" data-producto="${escapeHtml(l.productoId)}" data-nombre="${escapeHtml(l.nombre)}" novalidate>
            <strong class="review-product">${escapeHtml(l.nombre)}</strong>
            <div class="review-stars" role="radiogroup" aria-label="Calificación de ${escapeHtml(l.nombre)}">
              ${[1, 2, 3, 4, 5].map(n => `
                <button type="button" role="radio" aria-checked="false" data-valor="${n}" aria-label="${n} ${n === 1 ? "estrella" : "estrellas"}: ${TEXTO_ESTRELLAS[n]}">
                  <i class="bi bi-star"></i>
                </button>`).join("")}
              <span class="review-stars-text" aria-live="polite">Elige de 1 a 5 estrellas</span>
            </div>
            <textarea maxlength="500" rows="3" placeholder="Cuéntanos tu experiencia (opcional)" aria-label="Comentario sobre ${escapeHtml(l.nombre)}"></textarea>
            <div class="review-actions">
              <small class="review-count">0 / 500</small>
              <button type="submit" class="plan-button cart-checkout">PUBLICAR OPINIÓN <i class="bi bi-send"></i></button>
            </div>
            <div class="review-feedback" aria-live="polite"></div>
          </form>`).join("")}
      </div>
    </section>`;

  contenedor.querySelectorAll("form.review-card").forEach(form => conectarFormularioResena(form, pedido.folio));
}

function resenaEnviadaHtml(nombre, calificacion, mensaje) {
  return `
    <div class="review-card is-done">
      <strong class="review-product">${escapeHtml(nombre)}</strong>
      <div class="review-stars-static" aria-label="${calificacion} de 5 estrellas">${estrellasHtml(calificacion)}</div>
      <p class="review-done"><i class="bi bi-check2-circle"></i> ${escapeHtml(mensaje)}</p>
    </div>`;
}

function conectarFormularioResena(form, folio) {
  const estrellas = [...form.querySelectorAll("[data-valor]")];
  const textoEstrellas = form.querySelector(".review-stars-text");
  const comentario = form.querySelector("textarea");
  const contador = form.querySelector(".review-count");
  const feedback = form.querySelector(".review-feedback");
  let calificacion = 0;

  const pintar = valor => estrellas.forEach(b => {
    const activa = Number(b.dataset.valor) <= valor;
    b.classList.toggle("is-active", activa);
    b.querySelector("i").className = `bi ${activa ? "bi-star-fill" : "bi-star"}`;
  });

  estrellas.forEach(boton => {
    boton.addEventListener("mouseenter", () => pintar(Number(boton.dataset.valor)));
    boton.addEventListener("click", () => {
      calificacion = Number(boton.dataset.valor);
      estrellas.forEach(b => b.setAttribute("aria-checked", String(b === boton)));
      textoEstrellas.textContent = TEXTO_ESTRELLAS[calificacion];
      feedback.textContent = "";
      pintar(calificacion);
    });
  });
  form.querySelector(".review-stars").addEventListener("mouseleave", () => pintar(calificacion));
  comentario.addEventListener("input", () => contador.textContent = `${comentario.value.length} / 500`);

  form.addEventListener("submit", async event => {
    event.preventDefault();
    if (!calificacion) {
      feedback.className = "review-feedback is-error";
      feedback.textContent = "Elige de 1 a 5 estrellas antes de publicar.";
      return;
    }
    const boton = form.querySelector("button[type=submit]");
    const textoOriginal = boton.innerHTML;
    boton.disabled = true;
    boton.innerHTML = `<span class="spinner-border spinner-border-sm me-2"></span> PUBLICANDO...`;
    try {
      const response = await apiFetch("/api/resenas", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ folio, productoId: form.dataset.producto, calificacion, comentario: comentario.value.trim() })
      });
      const data = await response.json().catch(() => null);
      if (!response.ok || !data?.success) throw new Error(data?.message || "No se pudo publicar tu opinión.");
      form.outerHTML = resenaEnviadaHtml(form.dataset.nombre, calificacion, "¡Gracias por tu opinión!");
    } catch (error) {
      feedback.className = "review-feedback is-error";
      feedback.textContent = error.message;
      boton.disabled = false;
      boton.innerHTML = textoOriginal;
    }
  });
}

async function mostrarCarruselResenas(contenedor) {
  let resenas = [];
  try {
    const response = await fetch(`${API_BASE}/api/resenas/destacadas`);
    const data = await response.json().catch(() => null);
    if (response.ok && data?.success) resenas = data.resenas || [];
  } catch (_) {}

  const encabezado = `
    <span class="section-eyebrow"><span></span> OPINIONES DE CLIENTES</span>
    <h3 class="review-heading">Lo que dicen de FluxGuard</h3>`;
  if (!resenas.length) {
    contenedor.innerHTML = `
      <section class="review-section">${encabezado}
        <p class="review-sub mb-0">Aún no hay opiniones publicadas. ¡Pronto verás aquí la experiencia de nuestros clientes!</p>
      </section>`;
    return;
  }

  // "octubre de 2026" → "Octubre de 2026"
  const fecha = valor => {
    if (!valor) return "";
    const texto = new Date(valor).toLocaleDateString("es-MX", { month: "long", year: "numeric" });
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  };
  contenedor.innerHTML = `
    <section class="review-section review-carousel" aria-roledescription="carrusel" aria-label="Reseñas de clientes">
      ${encabezado}
      <div class="review-carousel-window">
        <div class="review-carousel-track">
          ${resenas.map((r, i) => `
            <figure class="review-slide" role="group" aria-roledescription="reseña" aria-label="${i + 1} de ${resenas.length}">
              <div class="review-stars-static" aria-label="${r.calificacion} de 5 estrellas">${estrellasHtml(r.calificacion)}</div>
              <blockquote>&ldquo;${escapeHtml(r.comentario)}&rdquo;</blockquote>
              <figcaption><strong>${escapeHtml(r.autor)}</strong> · ${escapeHtml(r.productoNombre)}<span>${escapeHtml(fecha(r.fecha))}</span></figcaption>
            </figure>`).join("")}
        </div>
      </div>
      ${resenas.length > 1 ? `
      <div class="review-carousel-controls">
        <button type="button" data-mover="-1" aria-label="Reseña anterior"><i class="bi bi-arrow-left"></i></button>
        <div class="review-carousel-dots">${resenas.map((_, i) => `<button type="button" data-ir="${i}" aria-label="Ver reseña ${i + 1}"></button>`).join("")}</div>
        <button type="button" data-mover="1" aria-label="Reseña siguiente"><i class="bi bi-arrow-right"></i></button>
      </div>` : ""}
    </section>`;

  if (resenas.length < 2) return;
  const seccion = contenedor.querySelector(".review-carousel");
  const track = seccion.querySelector(".review-carousel-track");
  const puntos = [...seccion.querySelectorAll("[data-ir]")];
  let indice = 0;
  let timer = null;
  const ir = siguiente => {
    indice = (siguiente + resenas.length) % resenas.length;
    track.style.transform = `translateX(-${indice * 100}%)`;
    puntos.forEach((p, i) => {
      p.classList.toggle("active", i === indice);
      p.setAttribute("aria-current", i === indice ? "true" : "false");
    });
  };
  const reiniciar = () => {
    clearInterval(timer);
    timer = setInterval(() => ir(indice + 1), 6000);
  };
  seccion.querySelectorAll("[data-mover]").forEach(b => b.addEventListener("click", () => { ir(indice + Number(b.dataset.mover)); reiniciar(); }));
  puntos.forEach(p => p.addEventListener("click", () => { ir(Number(p.dataset.ir)); reiniciar(); }));
  seccion.addEventListener("mouseenter", () => clearInterval(timer));
  seccion.addEventListener("mouseleave", reiniciar);
  ir(0);
  reiniciar();
}


// ===== Ticket de compra imprimible =====
// El mismo ticket que se envía por correo (PlantillaTicket.java), en blanco y negro para imprimir.

const dineroConCentavos = n => Number(n || 0).toLocaleString("es-MX", { style: "currency", currency: "MXN" });

function ticketHtml(pedido) {
  const total = Number(pedido.total || 0);
  // Los precios ya incluyen IVA: solo se desglosa
  const subtotal = Math.round((total / 1.16) * 100) / 100;
  const iva = Math.round((total - subtotal) * 100) / 100;
  const fechaPago = pedido.pagadoEn || pedido.fecha;
  const fecha = fechaPago ? new Date(fechaPago).toLocaleString("es-MX", { dateStyle: "short", timeStyle: "short" }) : "";
  const fila = (etiqueta, valor) => valor ? `<div class="ticket-fila"><span>${etiqueta}</span><span>${escapeHtml(valor)}</span></div>` : "";
  return `
    <div class="ticket">
      <div class="ticket-marca">FLUXGUARD</div>
      <div class="ticket-centro">${escapeHtml(FLUXGUARD_DOMICILIO)}<br>${escapeHtml(FLUXGUARD_CORREO)}</div>
      <div class="ticket-titulo">TICKET DE COMPRA</div>
      ${fila("Folio", pedido.folio)}
      ${fila("Fecha", fecha)}
      ${fila("Cliente", pedido.nombreCliente)}
      ${fila("Correo", pedido.correoCliente)}
      ${fila("Pago", nombreMetodoPago(pedido.metodoPago))}
      ${fila("Referencia", pedido.pagoId)}
      <div class="ticket-linea"></div>
      ${(pedido.lineas || []).map(l => `
        <div class="ticket-producto">
          <div>${escapeHtml(l.nombre)}</div>
          <div class="ticket-fila"><span>${l.cantidad} × ${dineroConCentavos(l.precioUnitario)}</span><span>${dineroConCentavos(l.importe)}</span></div>
        </div>`).join("")}
      <div class="ticket-linea"></div>
      <div class="ticket-fila"><span>Subtotal</span><span>${dineroConCentavos(subtotal)}</span></div>
      <div class="ticket-fila"><span>IVA (16 %)</span><span>${dineroConCentavos(iva)}</span></div>
      <div class="ticket-fila ticket-total"><span>TOTAL</span><span>${dineroConCentavos(total)}</span></div>
      <div class="ticket-linea"></div>
      <div class="ticket-centro">¡Gracias por tu compra!<br>Conserva tu folio para garantías y devoluciones.</div>
      <div class="ticket-centro ticket-nota">Este ticket no es un comprobante fiscal (CFDI). Si necesitas factura, escríbenos con tu folio.</div>
    </div>`;
}

// Imprime solo el ticket: el resto de la página se oculta con @media print (styles.css)
function imprimirTicket(pedido) {
  let contenedor = document.getElementById("ticket-impresion");
  if (!contenedor) {
    contenedor = document.createElement("div");
    contenedor.id = "ticket-impresion";
    document.body.appendChild(contenedor);
  }
  contenedor.innerHTML = ticketHtml(pedido);
  document.body.classList.add("imprimiendo-ticket");
  const terminar = () => {
    document.body.classList.remove("imprimiendo-ticket");
    window.removeEventListener("afterprint", terminar);
  };
  window.addEventListener("afterprint", terminar);
  window.print();
}


// Descarga el PDF del ticket (lo genera el servidor; solo para el dueño del pedido pagado)
async function descargarTicketPdf(folio) {
  const response = await apiFetch(`/api/pedidos/${encodeURIComponent(folio)}/ticket`);
  if (!response.ok) {
    const data = await response.json().catch(() => null);
    throw new Error(data?.message || "No se pudo descargar el ticket.");
  }
  const url = URL.createObjectURL(await response.blob());
  const enlace = document.createElement("a");
  enlace.href = url;
  enlace.download = `ticket-${folio}.pdf`;
  document.body.appendChild(enlace);
  enlace.click();
  enlace.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}
