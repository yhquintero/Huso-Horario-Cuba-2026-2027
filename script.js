(() => {
  "use strict";

  const root = document.documentElement;
  const header = document.querySelector(".site-header");
  const menuToggle = document.querySelector(".menu-toggle");
  const primaryNav = document.querySelector("#primary-nav");
  const toast = document.querySelector("#toast");
  let toastTimeout;

  root.classList.add("js-enabled");

  function showToast(message) {
    if (!toast) return;
    toast.textContent = message;
    toast.classList.add("is-visible");
    window.clearTimeout(toastTimeout);
    toastTimeout = window.setTimeout(() => toast.classList.remove("is-visible"), 2600);
  }

  function closeMenu() {
    if (!header || !menuToggle) return;
    header.dataset.menuOpen = "false";
    menuToggle.setAttribute("aria-expanded", "false");
    menuToggle.setAttribute("aria-label", "Abrir menú");
  }

  if (menuToggle && primaryNav && header) {
    menuToggle.hidden = false;
    menuToggle.addEventListener("click", () => {
      const isOpen = header.dataset.menuOpen === "true";
      header.dataset.menuOpen = String(!isOpen);
      menuToggle.setAttribute("aria-expanded", String(!isOpen));
      menuToggle.setAttribute("aria-label", isOpen ? "Abrir menú" : "Cerrar menú");
    });

    primaryNav.addEventListener("click", (event) => {
      if (event.target instanceof Element && event.target.closest("a")) closeMenu();
    });

    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") closeMenu();
    });
  }

  document.querySelectorAll(".copy-hash").forEach((button) => {
    button.hidden = false;
    button.addEventListener("click", async () => {
      const target = document.getElementById(button.dataset.copyTarget || "");
      const value = target?.textContent?.trim();
      if (!value) return;

      try {
        if (!navigator.clipboard?.writeText) throw new Error("Clipboard API unavailable");
        await navigator.clipboard.writeText(value);
        showToast("Huella SHA-256 copiada al portapapeles.");
      } catch {
        showToast("No se pudo copiar. Selecciona y copia la huella mostrada.");
      }
    });
  });

  const fileInput = document.querySelector("#file-input");
  const dropZone = document.querySelector("#drop-zone");
  const verifyStatus = document.querySelector("#verify-status");
  const verifyMessage = document.querySelector("#verify-message");
  const profileCards = [...document.querySelectorAll(".profile-card[data-file][data-sha256]")];
  const maxFileSize = 1024 * 1024;

  function setVerifyStatus(kind, message) {
    if (!verifyStatus) return;
    verifyStatus.classList.remove("is-success", "is-error", "is-working");
    if (kind) verifyStatus.classList.add(`is-${kind}`);

    const symbol = verifyStatus.querySelector(".status-symbol");
    if (symbol) {
      symbol.textContent = kind === "success" ? "✓" : kind === "error" ? "!" : kind === "working" ? "…" : "i";
    }

    if (verifyMessage) verifyMessage.textContent = message;
  }

  function toHex(buffer) {
    return [...new Uint8Array(buffer)]
      .map((byte) => byte.toString(16).padStart(2, "0"))
      .join("")
      .toUpperCase();
  }

  async function verifyFile(file) {
    if (!file || !verifyStatus) return;

    if (file.size === 0) {
      setVerifyStatus("error", "El archivo está vacío. No lo importes.");
      return;
    }

    if (file.size > maxFileSize) {
      setVerifyStatus("error", "El archivo supera el límite de 1 MB. No se procesó.");
      return;
    }

    if (!window.crypto?.subtle || !window.isSecureContext) {
      setVerifyStatus("error", "Este navegador no permite el cálculo SHA-256 en este contexto. Abre la página por HTTPS o usa una herramienta local de confianza.");
      return;
    }

    setVerifyStatus("working", "Calculando la huella en este dispositivo…");

    try {
      const digest = await window.crypto.subtle.digest("SHA-256", await file.arrayBuffer());
      const fingerprint = toHex(digest);
      const match = profileCards.find((card) => card.dataset.sha256?.toUpperCase() === fingerprint);

      if (match) {
        const publishedName = match.dataset.file || "perfil publicado";
        setVerifyStatus("success", `Coincidencia exacta con ${publishedName}. El archivo tiene los mismos bytes que el perfil publicado en esta página.`);
      } else {
        setVerifyStatus("error", `No coincide con ninguno de los perfiles publicados. Huella calculada: ${fingerprint}. No importes el archivo.`);
      }
    } catch {
      setVerifyStatus("error", "No se pudo leer el archivo para calcular su huella. Vuelve a seleccionarlo o usa una herramienta local de confianza.");
    }
  }

  if (fileInput) {
    fileInput.addEventListener("change", () => {
      const [file] = fileInput.files || [];
      if (file) verifyFile(file);
    });
  }

  if (dropZone) {
    ["dragenter", "dragover"].forEach((eventName) => {
      dropZone.addEventListener(eventName, (event) => {
        event.preventDefault();
        dropZone.classList.add("is-dragging");
      });
    });

    ["dragleave", "drop"].forEach((eventName) => {
      dropZone.addEventListener(eventName, (event) => {
        event.preventDefault();
        dropZone.classList.remove("is-dragging");
      });
    });

    dropZone.addEventListener("drop", (event) => {
      const files = event.dataTransfer?.files;
      if (!files?.length) return;
      if (files.length !== 1) {
        setVerifyStatus("error", "Selecciona un solo archivo .reg a la vez.");
        return;
      }
      verifyFile(files[0]);
    });
  }
})();
