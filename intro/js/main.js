const menuButton = document.querySelector(".menu-button");
const mobileNav = document.querySelector(".mobile-nav");
const copyButton = document.querySelector(".copy-button");
const sourceMenu = document.querySelector(".source-menu");
const sourceMenuTrigger = document.querySelector(".source-menu-trigger");
const sourceOptions = document.querySelector(".source-options");
const downloadMenu = document.querySelector(".download-menu");
const downloadMenuTrigger = document.querySelector(".download-menu-trigger");
const downloadOptions = document.querySelector(".download-options");
const webInstallTrigger = document.querySelector(".web-install-trigger");
const webInstallOptions = document.querySelector(".web-install-options");
const installCommand = document.querySelector(".install-command");
const installCommandLabel = document.querySelector(".install-command-label");
const installCommandText = document.querySelector(".install-command-text");
const installCopyButton = document.querySelector(".install-copy-button");

const closeSourceMenu = () => {
  sourceMenuTrigger?.setAttribute("aria-expanded", "false");
  sourceOptions?.setAttribute("hidden", "");
};

const closeDownloadMenu = () => {
  downloadMenuTrigger?.setAttribute("aria-expanded", "false");
  downloadOptions?.setAttribute("hidden", "");
  webInstallTrigger?.setAttribute("aria-expanded", "false");
  webInstallOptions?.setAttribute("hidden", "");
  installCommand?.setAttribute("hidden", "");
};

const copyText = async (text, button, defaultLabel) => {
  try {
    await navigator.clipboard.writeText(text);
    button.textContent = "已复制";
  } catch {
    button.textContent = "复制失败";
  }

  window.setTimeout(() => {
    button.textContent = defaultLabel;
  }, 1600);
};

menuButton?.addEventListener("click", () => {
  const isOpen = mobileNav.classList.toggle("open");
  menuButton.setAttribute("aria-expanded", String(isOpen));
});

document.querySelectorAll(".mobile-nav a").forEach((link) => {
  link.addEventListener("click", () => {
    mobileNav.classList.remove("open");
    menuButton?.setAttribute("aria-expanded", "false");
  });
});

sourceMenuTrigger?.addEventListener("click", () => {
  const isOpen = sourceMenuTrigger.getAttribute("aria-expanded") === "true";
  closeDownloadMenu();
  sourceMenuTrigger.setAttribute("aria-expanded", String(!isOpen));
  sourceOptions.toggleAttribute("hidden", isOpen);
});

downloadMenuTrigger?.addEventListener("click", () => {
  const isOpen = downloadMenuTrigger.getAttribute("aria-expanded") === "true";
  closeSourceMenu();
  downloadMenuTrigger.setAttribute("aria-expanded", String(!isOpen));
  downloadOptions.toggleAttribute("hidden", isOpen);
});

webInstallTrigger?.addEventListener("click", () => {
  const isOpen = webInstallTrigger.getAttribute("aria-expanded") === "true";
  webInstallTrigger.setAttribute("aria-expanded", String(!isOpen));
  webInstallOptions.toggleAttribute("hidden", isOpen);
  installCommand?.setAttribute("hidden", "");
});

document.querySelectorAll(".install-option").forEach((option) => {
  option.addEventListener("click", () => {
    installCommandLabel.textContent = option.dataset.platform;
    installCommandText.textContent = option.dataset.command;
    installCommand.removeAttribute("hidden");
  });
});

installCopyButton?.addEventListener("click", () => {
  copyText(installCommandText.textContent, installCopyButton, "复制命令");
});

copyButton?.addEventListener("click", () => {
  copyText(copyButton.dataset.copy, copyButton, "复制 Windows 命令");
});

document.addEventListener("click", (event) => {
  if (sourceMenu && !sourceMenu.contains(event.target)) {
    closeSourceMenu();
  }
  if (downloadMenu && !downloadMenu.contains(event.target)) {
    closeDownloadMenu();
  }
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    closeSourceMenu();
    closeDownloadMenu();
  }
});
