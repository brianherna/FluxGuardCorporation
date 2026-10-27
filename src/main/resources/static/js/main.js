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
            password: password
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
