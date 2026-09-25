/**
 * FluxGuard - Frontend principal
 * Navegación, UI, mapa, autenticación y formularios.
 */

const API_BASE = (window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1")
  ? (window.location.port === "8080" ? "" : "http://localhost:8080")
  : (localStorage.getItem("fluxg_api_url") || "");

// Limpieza automática de correos y sesiones de prueba previas
(function() {
  if (!localStorage.getItem("fluxg_v4_clean")) {
    localStorage.removeItem("fluxg_usuarios");
    localStorage.removeItem("usuario");
    localStorage.removeItem("fluxguard_active_subscription");
    localStorage.setItem("fluxg_v4_clean", "true");
  }
  // En las páginas de login y registro nunca debe haber sesión activa preexistente
  if (window.location.pathname.includes("login.html") || window.location.pathname.includes("crear-cuenta.html")) {
    localStorage.removeItem("usuario");
  }
})();

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
          <i class="bi bi-person-circle"></i> ${nombre} ${subBadge}
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
      localStorage.removeItem("usuario");
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
      mensaje: value("prospect-mensaje")
    };

    if (button) {
      button.disabled = true;
      button.innerHTML = `<span class="spinner-border spinner-border-sm me-2"></span> ENVIANDO...`;
    }

    try {
      let data = null;
      // 1. Intento con backend Spring Boot / MongoDB Atlas
      try {
        const response = await fetch(`${API_BASE}/api/prospectos`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload)
        });
        if (response.ok) {
          data = await response.json();
        }
      } catch (_) {}

      // 2. Fallback para GitHub Pages o modo demostración estática
      if (!data || !data.success) {
        const prospectos = JSON.parse(localStorage.getItem("fluxg_prospectos") || "[]");
        prospectos.push({ ...payload, fecha: new Date().toISOString() });
        localStorage.setItem("fluxg_prospectos", JSON.stringify(prospectos));
        data = { success: true, message: "¡Gracias! Tu información y solicitud fueron registradas exitosamente." };
      }

      showAlert(alertBox, "success", data.message || "¡Gracias! Tu información fue registrada exitosamente.");
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
      showAlert(alertBox, "danger", error.message || "No se pudo conectar con el servidor.");
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
      localStorage.removeItem("usuario");
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
        let data = null;
        let backendError = null;

        // Intento backend Spring Boot / MongoDB Atlas
        try {
          const response = await fetch(`${API_BASE}/api/auth/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email, password })
          });
          const resJson = await response.json().catch(() => null);
          if (resJson && resJson.requiereVerificacion) {
            // Cuenta creada pero sin verificar su correo
            return mostrarVerificacionPendiente(alertBox, "danger", resJson.message, resJson.correo);
          }
          if (response.ok && resJson && resJson.success) {
            data = resJson;
          } else if (resJson && resJson.message && response.status !== 404) {
            backendError = resJson.message;
          }
        } catch (_) {
          backendError = null;
        }

        // Si el backend respondió con un rechazo explícito (ej. cuenta no encontrada o contraseña errónea en MongoDB Atlas)
        if (backendError) {
          throw new Error(backendError);
        }

        // Fallback para GitHub Pages o persistencia local
        if (!data || !data.success) {
          const registrados = JSON.parse(localStorage.getItem("fluxg_usuarios") || "[]");
          const userFound = registrados.find(u => u.email.toLowerCase() === email);

          // REGLA ESTRICTA: Si la cuenta NO existe, TIENE PROHIBIDO ENTRAR
          if (!userFound) {
            throw new Error(`No existe ninguna cuenta registrada con el correo "${escapeHtml(email)}". Tienes prohibido el acceso; por favor crea una cuenta primero.`);
          }

          if (userFound.password !== password) {
            throw new Error("Contraseña incorrecta para esta cuenta.");
          }

          data = { success: true, usuario: { nombre: userFound.nombre, apellido: userFound.apellido, correo: userFound.email } };
        }

        // Preservar suscripción existente si ya la tenía en localStorage
        const prevUserRaw = localStorage.getItem("usuario");
        if (prevUserRaw) {
          try {
            const prevUser = JSON.parse(prevUserRaw);
            if (prevUser.correo === data.usuario.correo && prevUser.suscripcion) {
              data.usuario.suscripcion = prevUser.suscripcion;
            }
          } catch (_) {}
        }

        localStorage.setItem("usuario", JSON.stringify(data.usuario));

        if (redirect) {
          window.location.href = decodeURIComponent(redirect);
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

      // Verificación en frontend si ya existe localmente
      const registrados = JSON.parse(localStorage.getItem("fluxg_usuarios") || "[]");
      const yaExisteLocal = registrados.some(u => u.email.toLowerCase() === email);
      if (yaExisteLocal) {
        return showAlert(alertBox, "danger", `El correo "${escapeHtml(email)}" ya se encuentra registrado. Por favor inicia sesión.`);
      }

      if (button) {
        button.disabled = true;
        button.innerHTML = `<span class="spinner-border spinner-border-sm me-2"></span> CREANDO CUENTA...`;
      }

      try {
        let data = null;
        let backendError = null;

        // Intento backend Spring Boot / MongoDB Atlas
        try {
          const response = await fetch(`${API_BASE}/api/auth/register`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              nombre: get("reg-nombre"),
              apellido: get("reg-apellido"),
              email: email,
              password: password
            })
          });
          const resJson = await response.json().catch(() => null);
          if (resJson && resJson.requiereVerificacion) {
            // Cuenta guardada en MongoDB: no se inicia sesión hasta que el usuario abra el enlace enviado con Brevo
            if (resJson.success) registerForm.reset();
            return mostrarVerificacionPendiente(alertBox, resJson.success ? "success" : "danger", resJson.message, resJson.correo);
          }
          if (response.ok && resJson && resJson.success) {
            data = resJson;
          } else if (resJson && resJson.message && response.status !== 404) {
            backendError = resJson.message;
          }
        } catch (_) {
          backendError = null;
        }

        // Si el backend rechazó la creación (ej. cuenta duplicada en MongoDB Atlas)
        if (backendError) {
          throw new Error(backendError);
        }

        // Sin backend disponible (ej. GitHub Pages): guardar en la base de datos de usuarios registrados local
        const newUser = {
          nombre: get("reg-nombre"),
          apellido: get("reg-apellido"),
          email: email,
          password: password,
          fechaRegistro: new Date().toISOString()
        };
        registrados.push(newUser);
        localStorage.setItem("fluxg_usuarios", JSON.stringify(registrados));

        // INICIAR SESIÓN DIRECTAMENTE COMO USUARIO
        const activeUser = {
          nombre: newUser.nombre,
          apellido: newUser.apellido,
          correo: newUser.email
        };
        localStorage.setItem("usuario", JSON.stringify(activeUser));

        showAlert(alertBox, "success", `¡Cuenta creada exitosamente! Bienvenido, ${escapeHtml(activeUser.nombre)}. Iniciando sesión...`);

        setTimeout(() => {
          if (redirect) {
            window.location.href = decodeURIComponent(redirect);
          } else if (solicitar === "prototipo" || plan === "prototipo") {
            window.location.href = "pago.html?plan=prototipo";
          } else if (solicitar === "plan" && plan) {
            window.location.href = `pago.html?plan=${plan}`;
          } else {
            window.location.href = "index.html";
          }
        }, 1000);
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
      const rawUser = localStorage.getItem("usuario");
      const target = "pago.html?plan=prototipo";
      if (!rawUser) {
        window.location.href = `login.html?solicitar=plan&plan=prototipo&redirect=${encodeURIComponent(target)}`;
      } else {
        window.location.href = target;
      }
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
