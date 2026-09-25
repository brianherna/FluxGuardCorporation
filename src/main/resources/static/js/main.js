document.addEventListener("DOMContentLoaded", () => {

    const registerForm = document.getElementById("create-account-form");

    if (registerForm) {

        registerForm.addEventListener("submit", async (event) => {

            event.preventDefault();

            const nombre = document.getElementById("reg-nombre").value.trim();
            const apellido = document.getElementById("reg-apellido").value.trim();
            const email = document.getElementById("reg-email").value.trim();
            const password = document.getElementById("reg-password").value;
            const confirmPassword = document.getElementById("reg-confirm").value;

            // Validar contraseñas
            if (password !== confirmPassword) {
                alert("Las contraseñas no coinciden.");
                return;
            }

            // Validar contraseña
            const passwordRegex = /^(?=.*[A-Z])(?=.*[!@#$%^&*]).{8,}$/;

            if (!passwordRegex.test(password)) {
                alert("La contraseña debe tener mínimo 8 caracteres, una mayúscula y un carácter especial.");
                return;
            }

            try {

                const response = await fetch("/api/auth/register", {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify({
                        nombre: nombre,
                        apellido: apellido,
                        email: email,
                        password: password
                    })
                });

                const data = await response.json();

                if (!response.ok) {
                    alert(data.message || "No se pudo crear la cuenta.");
                    return;
                }

                alert("¡Cuenta creada correctamente!");

                window.location.href = "login.html";

            } catch (error) {

                console.error("Error:", error);

                alert("No se pudo conectar con el servidor.");
            }

        });

    }

});