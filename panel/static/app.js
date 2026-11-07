"use strict";

function completionEdit(input, completion, text) {
  const { start, end } = completion;
  if (!Number.isInteger(start) || !Number.isInteger(end) || start < 0 || end < start || end > input.length || typeof text !== "string") throw new Error("Ungültiger Vorschlagsbereich.");
  return { value: input.slice(0, start) + text + input.slice(end), caret: start + text.length };
}
function completionChoices(data, input) {
  if (!data?.available || !data.ready || !Array.isArray(data.suggestions) || !Number.isInteger(data.start) || !Number.isInteger(data.end) || data.start < 0 || data.end < data.start || data.end > input.length) return null;
  return { start: data.start, end: data.end, suggestions: data.suggestions.filter((item) => typeof item?.text === "string").slice(0, 50) };
}


(async () => {
  if (window.ArkI18n) await ArkI18n.ready;
  const t = (text, vars) => window.ArkI18n ? ArkI18n.t(text, vars) : String(text);
  const confirmText = text => window.confirm(t(text));
  for (const id of ["console-output", "logs-output", "players-output"]) document.getElementById(id).dataset.i18nSkip = "";
  const $ = (id) => document.getElementById(id);
  const state = { authenticated: false, modules: null, csrf: null, view: "overview", loaded: new Set(), services: [], configs: [], mission: null, geyserOriginal: null, missionOriginal: null, settingsOriginal: null, pluginOriginals: { geyser: null, quests: null }, editorBusy: new Set(), configVersions: {}, advanced: null, advancedOriginal: null, allConfigs: [], worlds: [], currentWorld: null, formVersions: {}, sessionSerial: 0, worldBusy: false };
  const supportUI = ArkSupport.mount($("admin-support"), { api: (path, ...args) => api("/api/" + path, ...args), admin: true, authenticated: () => state.authenticated && state.modules?.support === true, onActivity: notify });
  let mapRequest = null;
  let mapData = null;
  let viewerRequest = null;
  let completionRequest = null;
  let completionTimer = null;
  let completionSerial = 0;
  let completion = null;
  let fieldSerial = 0;
  let worldsRequest = 0;
  let navigationRequest = 0;
  let pollBusy = false;
  const formRequests = { minecraft: 0, geyser: 0, quests: 0 };
  const rawRequests = { geyser: 0, mission: 0, advanced: 0 };
  const titles = { map: "Karte", overview: "Übersicht", minecraft: "Minecraft", geyser: "Geyser", missions: "Missionen", dns: "DNS", worlds: "Welten & Regeln", players: "Spielerverwaltung", configs: "Weitere Einstellungen", support: "Support" };
  const descriptions = {
    quests: "Die Missionen selbst: Ziele, Belohnungen sowie Einzel- und Gruppenfortschritt. Nach dem Speichern Quests neu laden.",
    "quest-settings": "Allgemeine Quest-Einstellungen. Nach dem Speichern Quests neu laden. Einzelne Plugin-Optionen können einen Minecraft-Neustart benötigen.",
    permissions: "Spielerrechte, zum Beispiel für das Erstellen und Verwalten von Gruppen. Nach dem Speichern Minecraft neu starten."
  };

  function displayText(value) {
    return String(value ?? "")
      .replace(/\u001b\][\s\S]*?(?:\u0007|\u001b\\)/g, "")
      .replace(/(?:\u001b\[|\u009b)[0-?]*[ -/]*[@-~]/g, "")
      .replace(/\u001b[@-_]/g, "")
      .replace(/§[0-9a-fk-orx]/gi, "")
      .replace(/\r\n?/g, "\n")
      .replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, "");
  }

  function message(element, text, error = false) {
    const value = t(displayText(text));
    if (element.textContent !== value) element.textContent = value;
    element.classList.toggle("error", error);
  }

  function notify(text, error = false) {
    message($("notification"), text, error);
    $("notification").hidden = false;
  }

  function clearAdmin() {
    state.authenticated = false; state.modules = null;
    supportUI.clear();
    $("reset-player-form").reset(); $("reset-player-names").replaceChildren(); message($("reset-player-message"), "");
    state.authenticated = false; state.modules = null;
    state.sessionSerial++;
    state.formVersions = {};
    state.csrf = null;
    mapRequest?.abort();
    mapRequest = null;
    mapData = null;
    viewerRequest?.abort();
    $("map-frame").src = "about:blank";
    $("map-frame-wrap").hidden = true;
    $("map-players").replaceChildren();
    closeCompletion();
    state.advancedOriginal = null;
    state.geyserOriginal = state.missionOriginal = state.settingsOriginal = null;
    state.pluginOriginals = { geyser: null, quests: null };
    state.worlds = []; state.currentWorld = null;
    $("geyser-content").value = $("mission-content").value = $("advanced-content").value = "";
    $("console-command").value = ""; $("console-output").textContent = $("logs-output").textContent = "";
    delete $("console-output").dataset.history;
    $("world-controls").replaceChildren(); $("world-rules").replaceChildren(); $("players-table").replaceChildren();
    document.querySelectorAll(".view").forEach(section => section.hidden = true);
    document.querySelectorAll(".view form").forEach(form => form.reset());
    document.querySelectorAll(".view .setting-state, .view .config-status, .view .form-message, .service-details dd, .service-state").forEach(node => node.textContent = "");
    state.loaded.clear(); state.services = []; state.configs = []; state.allConfigs = []; state.advanced = state.mission = null;
    for (const id of ["players-output", "whitelist-players", "banned-players", "player-names", "minecraft-dynamic-fields", "mission-config", "advanced-config"]) {
      $(id).replaceChildren();
    }
    document.documentElement.dataset.arkWardrobe = "false";
    window.dispatchEvent(new CustomEvent("ark:modules"));
    $("notification").textContent = ""; $("notification").hidden = true;
    $("dns-frame").removeAttribute("src");
    $("password-dialog").close();
    $("password-form").reset();
    message($("password-message"), "");
  }

  async function api(path, method = "GET", body, signal) {
    if (!state.authenticated) throw new Error("Keine Verwaltungsberechtigung.");
    const serial = state.sessionSerial;
    const headers = { Accept: "application/json", "Accept-Language": window.ArkI18n?.language || "de" };
    if (method !== "GET") {
      headers["Content-Type"] = "application/json";
      if (state.csrf) headers["X-CSRF-Token"] = state.csrf;
    }
    let response;
    try {
      response = await fetch(path, { method, headers, signal, credentials: "same-origin", cache: "no-store", ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
    } catch (error) {
      if (error.name === "AbortError") throw error;
      throw new Error("Keine Verbindung zur Verwaltung. Bitte die Verbindung prüfen und erneut versuchen.");
    }
    let data;
    try { data = await response.json(); } catch { data = {}; }
    if (!state.authenticated || serial !== state.sessionSerial) throw new DOMException("Verwaltungsberechtigung geändert.", "AbortError");
    if (!response.ok) {
      if (response.status === 401 || (response.status === 403 && ["admin_required", "operator_required"].includes(data.code))) { clearAdmin(); window.dispatchEvent(new Event("ark:session-refresh")); }
      const error = new Error(typeof data.error === "string" ? data.error : `Die Anfrage ist fehlgeschlagen (HTTP ${response.status}).`);
      error.status = response.status;
      throw error;
    }
    return data;
  }

  async function run(button, task, target = $("notification")) {
    button.disabled = true;
    button.setAttribute("aria-busy", "true");
    try { await task(); } catch (error) {
      if (target === $("notification")) notify(error.message, true);
      else message(target, error.message, true);
    } finally {
      button.disabled = false;
      button.removeAttribute("aria-busy");
      updateEditors();
    }
  }

  function inputValue(input) { return input.type === "checkbox" ? input.checked : input.type === "number" ? (input.value === "" ? null : Number(input.value)) : input.value; }
  function settingsFromForm() {
    return Object.fromEntries([...$("minecraft-dynamic-fields").querySelectorAll("[name]")].map((input) => [input.name, inputValue(input)]));
  }

  const geyserDirty = () => state.geyserOriginal !== null && $("geyser-content").value !== state.geyserOriginal;
  const missionDirty = () => state.missionOriginal !== null && $("mission-content").value !== state.missionOriginal;
  const settingsDirty = () => state.settingsOriginal !== null && JSON.stringify(settingsFromForm()) !== state.settingsOriginal;
  const pluginDirty = (target) => state.pluginOriginals[target] !== null && JSON.stringify(pluginFormValues(target)) !== state.pluginOriginals[target];
  const advancedDirty = () => state.advancedOriginal !== null && $("advanced-content").value !== state.advancedOriginal;
  const worldDirty = () => [...$("view-worlds").querySelectorAll("[data-saved-value]")].some((input) => JSON.stringify(inputValue(input)) !== input.dataset.savedValue);
  const anyDirty = () => supportUI.hasDirty() || advancedDirty() || worldDirty() || geyserDirty() || missionDirty() || settingsDirty() || pluginDirty("geyser") || pluginDirty("quests");
  const pluginForConfig = (id) => id === "geyser" ? "geyser" : id === "quest-settings" ? "quests" : null;
  const rawPrefixForPlugin = (target) => target === "geyser" ? "geyser" : state.mission?.id === "quest-settings" ? "mission" : null;
  const rawDirtyForPlugin = (target) => (state.advanced?.reload_target === target && advancedDirty()) || (target === "geyser" ? geyserDirty() : state.mission?.id === "quest-settings" && missionDirty());
  const dirtyForReload = (target) => (state.advanced?.reload_target === target && advancedDirty()) || pluginDirty(target) || (target === "geyser" ? geyserDirty() : state.mission?.reload_target === target && missionDirty());

  function pluginFormValues(target) {
    return Object.fromEntries([...$(`${target}-settings-form`).querySelectorAll("[name]")].map((input) => [input.name, inputValue(input)]));
  }

  async function withEditorLock(target, task) {
    if (state.editorBusy.has(target)) return false;
    state.editorBusy.add(target);
    updateEditors();
    try { await task(); return true; }
    finally { state.editorBusy.delete(target); updateEditors(); }
  }

  function runEditor(target, button, task, status) {
    if (state.editorBusy.has(target)) return;
    return run(button, () => withEditorLock(target, task), status);
  }

  function setInputValue(input, value) {
    if (input.type === "checkbox") input.checked = value === true;
    else if (input.value !== String(value ?? "")) input.value = value ?? "";
  }

  function valueText(value, key = "") {
    if (value === true) return "An";
    if (value === false) return "Aus";
    if (value === "") return "(leer)";
    if (value === null || value === undefined) return "nicht verfügbar";
    const names = { peaceful: "Friedlich", easy: "Einfach", normal: "Normal", hard: "Schwer", survival: "Überleben", creative: "Kreativ", adventure: "Abenteuer", spectator: "Zuschauer", clear: "Klar", rain: "Regen", thunder: "Gewitter" };
    return ["difficulty", "gamemode", "weather"].includes(key) && Object.hasOwn(names, value) ? names[value] : String(value);
  }

  function readTime(value) {
    const date = new Date(value);
    return value && !Number.isNaN(date.getTime()) ? date.toLocaleTimeString(window.ArkI18n?.language || "de", { hour: "2-digit", minute: "2-digit", second: "2-digit" }) : "";
  }

  function settingStates(target, data) {
    const form = $(target === "minecraft" ? "minecraft-form" : `${target}-settings-form`);
    for (const input of form.querySelectorAll("[name]")) {
      let detail = document.getElementById(`${input.id}-state`);
      if (!detail) {
        detail = document.createElement("small"); detail.id = `${input.id}-state`; detail.className = "setting-state";
        const toggle = input.closest(".toggle-field");
        (toggle?.querySelector("span") || input.closest(".field")).append(detail);
        input.setAttribute("aria-describedby", [...new Set([...(input.getAttribute("aria-describedby") || "").split(/\s+/).filter(Boolean), detail.id])].join(" "));
      }
      const key = input.name;
      const source = data.sources?.[key] === "default" ? "Standard (nicht gesetzt)" : data.sources?.[key] === "file" ? "Gespeichert" : "Konfiguriert";
      const saved = Object.hasOwn(data.settings || {}, key) ? valueText(data.settings[key], key) : "nicht mehr vorhanden";
      const active = data.runtime?.available === true && Object.hasOwn(data.runtime.settings || {}, key) && data.runtime.settings[key] !== null && data.runtime.settings[key] !== undefined;
      const scope = data.runtime?.scopes?.[key] === "primary_world" ? "Aktiv in der Hauptwelt" : "Aktiv";
      const live = active ? `${scope}: ${valueText(data.runtime.settings[key], key)}` : data.runtime?.available ? "Aktiver Wert nicht auslesbar" : "Aktiver Wert derzeit nicht verfügbar";
      const text = `${source}: ${saved} · ${live}`;
      if (detail.textContent !== text) detail.textContent = text;
      detail.classList.toggle("different", active && JSON.stringify(data.settings[key]) !== JSON.stringify(data.runtime.settings[key]));
    }
    const fileTime = readTime(data.observed_at);
    const activeTime = readTime(data.runtime?.observed_at);
    message($(`${target}-live-status`), [`Datei${fileTime ? ` gelesen um ${fileTime}` : " gelesen"}`, data.runtime?.available ? `Aktive Werte${activeTime ? ` von ${activeTime}` : " abgerufen"}` : "Live-Abfrage derzeit nicht verfügbar", "Automatisch alle 15 Sekunden"].join(" · "));
  }

  function settingsUnavailable(target) {
    const form = $(target === "minecraft" ? "minecraft-form" : `${target}-settings-form`);
    for (const detail of form.querySelectorAll(".setting-state")) {
      detail.textContent = detail.textContent.split(" · ")[0] + " · Aktiver Wert derzeit nicht verfügbar";
      detail.classList.remove("different");
    }
    message($(`${target}-live-status`), "Aktualisierung derzeit nicht erreichbar. Angezeigte Eingaben bleiben erhalten; nächster Abruf automatisch.");
  }

  async function loadPluginSettings(target, discard = false, background = false) {
    if (!background && !discard && pluginDirty(target) && !confirmText("Ungespeicherte Formulareinstellungen verwerfen und erneut laden?")) return;
    const serial = ++formRequests[target], session = state.sessionSerial, view = state.view;
    try {
      const data = await api(`/api/settings/${target}`);
      if (!state.authenticated || session !== state.sessionSerial || serial !== formRequests[target] || (background && state.view !== view)) return;
      const form = $(`${target}-settings-form`);
      const preserve = background && (pluginDirty(target) || (form.contains(document.activeElement) && document.activeElement.matches("[name]")));
      if (!preserve) {
        for (const input of form.querySelectorAll("[name]")) {
          if (!Object.hasOwn(data.settings, input.name)) throw new Error("Die Einstellungen sind unvollständig. Bitte die Verwaltungsseite erneut laden.");
          setInputValue(input, data.settings[input.name]);
        }
        state.pluginOriginals[target] = JSON.stringify(pluginFormValues(target));
        state.formVersions[target] = data.version;
      }
      settingStates(target, data);
      if (!background) message($(`${target}-settings-message`), "Gespeicherte Einstellungen geladen. Aktive Werte stehen jeweils darunter.");
    } catch (error) {
      if (!state.authenticated || session !== state.sessionSerial || serial !== formRequests[target] || (background && state.view !== view)) return;
      settingsUnavailable(target); throw error;
    } finally { updateEditors(); }
  }

  async function savePluginSettings(target) {
    if (rawDirtyForPlugin(target) && !confirmText("Ungespeicherte YAML-Änderungen verwerfen und die Formulareinstellungen speichern?")) return;
    const prefix = rawPrefixForPlugin(target);
    const settings = pluginFormValues(target);
    try {
      if (typeof state.formVersions[target] !== "string") throw new Error("Bitte die Einstellungen erneut laden; der Versionsnachweis fehlt.");
      const result = await api(`/api/settings/${target}`, "PUT", { settings, version: state.formVersions[target] });
      state.formVersions[target] = result.version;
      state.pluginOriginals[target] = JSON.stringify(settings);
      await loadPluginSettings(target, true);
      message($(`${target}-settings-message`), `Gespeichert und gesichert. ${target === "geyser" ? "Geyser" : "Quests"} zum Anwenden neu laden.`);
      if (state.advanced?.id === (target === "geyser" ? "geyser" : "quest-settings")) await loadAdvancedConfig(state.advanced.id, true);
      if (prefix) {
        if (prefix === "geyser") state.geyserOriginal = null;
        else state.missionOriginal = null;
        try { await loadConfig(target === "geyser" ? "geyser" : "quest-settings", prefix, true); }
        catch (error) { message($(`${prefix}-message`), `Einstellungen gespeichert. YAML-Datei bitte erneut laden: ${error.message}`, true); }
      }
    } finally {
      updateEditors();
    }
  }

  function updateEditors() {
    updateAdvancedEditor();
    $("minecraft-fields").disabled = state.settingsOriginal === null || state.editorBusy.has("minecraft") || state.editorBusy.has("advanced");
    $("refresh-settings").disabled = state.editorBusy.has("minecraft") || state.editorBusy.has("advanced") || $("refresh-settings").hasAttribute("aria-busy");
    for (const prefix of ["geyser", "mission"]) {
      const dirty = prefix === "geyser" ? geyserDirty() : missionDirty();
      const original = prefix === "geyser" ? state.geyserOriginal : state.missionOriginal;
      const content = $(`${prefix}-content`);
      const busy = state.editorBusy.has("advanced") || state.editorBusy.has(prefix === "geyser" ? "geyser" : "quests");
      content.disabled = busy || original === null;
      $(`save-${prefix}`).disabled = busy || original === null || $(`save-${prefix}`).hasAttribute("aria-busy");
      $(`refresh-${prefix}`).disabled = busy || $(`refresh-${prefix}`).hasAttribute("aria-busy") || (prefix === "mission" && !state.configs.length);
      $(`${prefix}-counter`).textContent = original === null ? "YAML" : `${dirty ? "Ungespeichert · " : ""}${content.value.length.toLocaleString(window.ArkI18n?.language || "de")} Zeichen`;
      const plugin = prefix === "geyser" ? "geyser" : state.mission?.reload_target;
      $(`reload-${prefix}`).disabled = busy || $(`reload-${prefix}`).hasAttribute("aria-busy") || original === null || dirty || Boolean(plugin && pluginDirty(plugin));
    }
    $("mission-config").disabled = state.editorBusy.has("advanced") || state.editorBusy.has("quests") || !state.configs.length || $("mission-config").hasAttribute("aria-busy");
    for (const target of ["geyser", "quests"]) {
      const busy = state.editorBusy.has("advanced") || state.editorBusy.has(target);
      $(`${target}-settings-fields`).disabled = busy || state.pluginOriginals[target] === null;
      $(`refresh-${target}-settings`).disabled = busy || $(`refresh-${target}-settings`).hasAttribute("aria-busy");
      $(`save-${target}-settings`).disabled = busy || state.pluginOriginals[target] === null || $(`save-${target}-settings`).hasAttribute("aria-busy");
      const button = $(`apply-${target}-settings`);
      button.disabled = busy || button.hasAttribute("aria-busy") || state.pluginOriginals[target] === null || Boolean(dirtyForReload(target));
    }
  }

  async function loadModules() {
    const session = state.sessionSerial;
    try {
      const modules = await api("/api/modules");
      if (modules.accounts !== true || typeof modules.wardrobe !== "boolean" || typeof modules.support !== "boolean") throw new Error(t("Modulstatus nicht verfügbar. Bitte den Status erneut aktualisieren."));
      if (!state.authenticated || session !== state.sessionSerial) return;
      state.modules = modules;
    } catch (error) { if (!state.authenticated || session !== state.sessionSerial) return; state.modules = null; notify(error.message, true); }
    const modules = state.modules;
    document.querySelector('[data-view="support"]').hidden = modules?.support === false;
    $("admin-support").hidden = modules?.support === false;
    $("signing-open").hidden = modules?.wardrobe === false;
    $("signing-open").setAttribute("aria-disabled", String(modules?.wardrobe !== true));
    document.documentElement.dataset.arkWardrobe = modules?.wardrobe === true ? "true" : modules?.wardrobe === false ? "false" : "unknown";
    supportUI.updateAvailability();
    window.dispatchEvent(new CustomEvent("ark:modules"));
    if (modules?.support) supportUI.refresh({ silent: true });
  }
  async function refreshStatus() {
    const data = await api("/api/status");
    state.services = data.services;
    for (const service of data.services) {
      const card = document.querySelector(`.service-card[data-service="${service.id === "minecraft" ? "minecraft" : "dns"}"]`);
      if (!card || !["minecraft", "dns"].includes(service.id)) continue;
      const states = { running: "Läuft", exited: "Gestoppt", created: "Bereit", restarting: "Startet neu", paused: "Pausiert", dead: "Fehler", removing: "Wird entfernt", not_found: "Nicht gefunden", missing: "Nicht gefunden" };
      const badge = card.querySelector(".service-state");
      badge.textContent = states[service.state] || service.state || "Unbekannt";
      badge.className = `badge service-state ${service.health === "unhealthy" || service.state === "restarting" ? "warning" : service.state === "running" ? "healthy" : "offline"}`;
      const health = { healthy: "Gesund", unhealthy: "Fehler", starting: "Startet", none: "Kein Healthcheck" };
      card.querySelector(".service-health").textContent = health[service.health] || service.health || "Kein Healthcheck";
      const date = service.started_at && new Date(service.started_at);
      card.querySelector(".service-started").textContent = date && !Number.isNaN(date.getTime()) && date.getFullYear() > 1970 ? date.toLocaleString(window.ArkI18n?.language || "de", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" }) : "—";
      card.querySelector(".service-restarts").textContent = service.restart_count ?? "—";
      updateServiceControls(card, service.state);
    }
    $("players-output").textContent = displayText(data.players);
    updateAdvancedEditor();
  }

  function renderMap() {
    $("map-players").replaceChildren();
    for (const player of mapData?.players || []) {
      const item = document.createElement("li");
      const name = document.createElement("strong"); name.dataset.i18nSkip = ""; name.textContent = player.name;
      const place = document.createElement("span");
      place.textContent = `${player.world} · X ${Number(player.x).toFixed(1)} · Y ${Number(player.y).toFixed(1)} · Z ${Number(player.z).toFixed(1)}`;
      item.append(name, place); $("map-players").append(item);
    }
    if (!mapData?.players?.length) $("map-players").textContent = mapData?.online ? "Gerade ist niemand online." : "Keine aktuellen Positionen verfügbar.";
    message($("map-status"), mapData?.online ? `${mapData.players.length} Spieler online · alle fünf Sekunden` : mapData?.error || "Positionen werden vorbereitet.", !mapData?.online);
  }

  async function refreshMapViewer() {
    if (!state.authenticated || state.view !== "map" || viewerRequest) return;
    const controller = new AbortController(); viewerRequest = controller;
    try {
      const data = await api("/api/map/viewer", "GET", undefined, controller.signal);
      if (!state.authenticated || state.view !== "map" || controller.signal.aborted) return;
      const available = data.available === true;
      $("map-frame-wrap").hidden = !available;
      $("map-fullscreen").disabled = !available;
      $("map-open").hidden = !available;
      if (available && $("map-frame").getAttribute("src") !== "/map/") $("map-frame").src = "/map/";
      if (!available) $("map-frame").src = "about:blank";
      message($("map-viewer-status"), data.notice || (available ? "Gespeicherte Bauten in der 2D-Karte. Änderungen folgen nach der Kartenberechnung." : "Die 2D-Karte wird vorbereitet. Bitte später erneut aktualisieren."));
    } catch (error) { if (error.name !== "AbortError" && state.authenticated) message($("map-viewer-status"), error.message, true); }
    finally { if (viewerRequest === controller) viewerRequest = null; }
  }

  async function refreshMap() {
    if (!state.authenticated || state.view !== "map" || document.hidden || mapRequest) return;
    const controller = new AbortController();
    mapRequest = controller;
    const timeout = setTimeout(() => controller.abort(), 10000);
    try {
      const data = await api("/api/map/players", "GET", undefined, controller.signal);
      if (!state.authenticated || controller.signal.aborted) return;
      mapData = data;
      renderMap();
    } catch (error) {
      if (!state.authenticated || state.view !== "map") return;
      if (mapData) { mapData.players = []; mapData.online = false; mapData.error = "Aktuelle Positionen nicht erreichbar."; renderMap(); }
      else $("map-players").replaceChildren();
      message($("map-status"), error.name === "AbortError" ? "Positionen derzeit nicht erreichbar. Der nächste Abruf erfolgt automatisch." : error.message, true);
    } finally {
      clearTimeout(timeout);
      if (mapRequest === controller) mapRequest = null;
    }
  }

  function updateServiceControls(card, serviceState) {
    if (card.hasAttribute("aria-busy")) return;
    card.querySelectorAll("[data-action]").forEach((button) => {
      button.disabled = button.dataset.action === "start" ? ["running", "restarting"].includes(serviceState) : serviceState !== "running";
    });
  }

  async function serviceAction(id, action) {
    const name = id === "minecraft" ? "Minecraft" : "DNS";
    if (action !== "start") {
      const impact = id === "minecraft" ? "Alle Spieler werden getrennt." : "Die DNS-Auflösung wird kurz unterbrochen.";
      if (!confirmText(`${name} jetzt ${action === "stop" ? "stoppen" : "neu starten"}? ${impact}`)) return;
    }
    const card = document.querySelector(`.service-card[data-service="${id}"]`);
    card.setAttribute("aria-busy", "true");
    card.querySelectorAll("button").forEach((button) => { button.disabled = true; });
    try {
      await api(`/api/services/${id}/${action}`, "POST", {});
      notify(`${name}: ${action === "start" ? "Start" : action === "stop" ? "Stopp" : "Neustart"} wurde angefordert.`);
    } finally {
      card.removeAttribute("aria-busy");
      updateServiceControls(card, state.services.find((service) => service.id === id)?.state);
    }
    await refreshStatus();
  }

  function addField(container, field, value, prefix) {
    const wrap = document.createElement("div"); wrap.className = "field"; wrap.dataset.search = `${field.label || ""} ${field.key} ${field.description || ""}`.toLocaleLowerCase("de");
    const label = document.createElement("label"); label.textContent = field.label || field.key;
    const input = document.createElement(field.type === "choice" ? "select" : "input");
    input.id = `${prefix}-${++fieldSerial}`; input.name = field.key;
    if (field.type === "choice") for (const option of field.options || []) {
      const item = document.createElement("option"); item.value = typeof option === "object" ? option.value : option; item.textContent = typeof option === "object" ? option.label || option.value : valueText(option, field.key); if (field.originalOptions) item.dataset.i18nSkip = ""; input.append(item);
    }
    else input.type = field.type === "boolean" ? "checkbox" : ["integer", "number", "double"].includes(field.type) ? "number" : "text";
    if (input.type === "checkbox") input.checked = Boolean(value);
    else { input.value = value ?? ""; input.required = field.type !== "string"; }
    if (input.type === "number") input.step = field.type === "integer" ? "1" : "any";
    if (field.min !== undefined) input.min = field.min;
    if (field.max !== undefined) input.max = field.max;
    label.htmlFor = input.id;
    wrap.append(label, input);
    if (field.description) { const hint = document.createElement("p"); hint.id = `${input.id}-hint`; hint.className = "field-hint"; hint.textContent = field.description; input.setAttribute("aria-describedby", hint.id); wrap.append(hint); }
    container.append(wrap); return input;
  }

  async function loadSettings({ background = false } = {}) {
    if (!background && settingsDirty() && !confirmText("Ungespeicherte Spieleinstellungen verwerfen und erneut laden?")) return;
    if (!background) $("minecraft-fields").disabled = true;
    const serial = ++formRequests.minecraft, session = state.sessionSerial, view = state.view;
    try {
      const data = await api("/api/settings/minecraft");
      if (!state.authenticated || session !== state.sessionSerial || serial !== formRequests.minecraft || (background && state.view !== view)) return;
      const { settings, fields } = data;
      const preserve = background && (settingsDirty() || ($("minecraft-form").contains(document.activeElement) && document.activeElement.matches("[name]")));
      const common = {
        motd: { label: "Serverbeschreibung", type: "string" },
        gamemode: { label: "Standard-Spielmodus", type: "choice", options: [{ value: "survival", label: "Überleben" }, { value: "creative", label: "Kreativ" }, { value: "adventure", label: "Abenteuer" }, { value: "spectator", label: "Zuschauer" }] },
        difficulty: { label: "Standard-Schwierigkeit", type: "choice", options: [{ value: "peaceful", label: "Friedlich" }, { value: "easy", label: "Einfach" }, { value: "normal", label: "Normal" }, { value: "hard", label: "Schwer" }] },
        "max-players": { label: "Maximale Spielerzahl", type: "integer", min: 1, max: 100 },
        "view-distance": { label: "Sichtweite in Chunks", type: "integer", min: 2, max: 16 },
        "simulation-distance": { label: "Simulationsweite in Chunks", type: "integer", min: 2, max: 16 },
        "white-list": { label: "Whitelist", type: "boolean" }
      };
      const fallback = Object.entries(settings).filter(([key]) => key !== "pvp").map(([key, value]) => ({ key, label: key, type: typeof value === "boolean" ? "boolean" : typeof value === "number" ? "integer" : "string", group: "Server", ...common[key] }));
      const groups = new Map([...$("minecraft-dynamic-fields").children].map(section => [section.dataset.group, section.querySelector(".form-grid")]));
      if (!preserve) {
      const keys = new Set(Object.keys(settings));
      for (const input of $("minecraft-dynamic-fields").querySelectorAll("[name]")) if (!keys.has(input.name)) input.closest(".field").remove();
      for (const field of fields || fallback) {
        if (!Object.hasOwn(settings, field.key)) continue;
        const group = field.group || "Allgemein";
        if (!groups.has(group)) {
          const section = document.createElement("section"); section.className = "settings-group"; section.dataset.group = group;
          const heading = document.createElement("h3"); heading.textContent = group;
          const grid = document.createElement("div"); grid.className = "form-grid"; section.append(heading, grid); $("minecraft-dynamic-fields").append(section); groups.set(group, grid);
        }
        const existing = [...$("minecraft-dynamic-fields").querySelectorAll("[name]")].find(input => input.name === field.key);
        if (existing) setInputValue(existing, settings[field.key]);
        else addField(groups.get(group), field, settings[field.key], "setting");
      }
      state.settingsOriginal = JSON.stringify(settingsFromForm());
      state.formVersions.minecraft = data.version;
      }
      settingStates("minecraft", data);
      if (!background) message($("minecraft-message"), "Gespeicherte Startvorgaben geladen. Aktive Werte können je Welt abweichen.");
    } catch (error) {
      if (!state.authenticated || session !== state.sessionSerial || serial !== formRequests.minecraft || (background && state.view !== view)) return;
      settingsUnavailable("minecraft"); throw error;
    } finally { updateEditors(); }
  }

  async function loadConfig(id, prefix, discard = false, background = false) {
    const dirty = prefix === "geyser" ? geyserDirty() : missionDirty();
    if (!background && !discard && dirty && !confirmText("Ungespeicherte Änderungen verwerfen und die Datei laden?")) return false;
    const serial = ++rawRequests[prefix], session = state.sessionSerial;
    try {
      const data = await api(`/api/configs/${encodeURIComponent(id)}`);
      if (!state.authenticated || session !== state.sessionSerial || serial !== rawRequests[prefix]) return false;
      const content = $(`${prefix}-content`);
      const preserve = background && ((prefix === "geyser" ? geyserDirty() : missionDirty()) || content === document.activeElement);
      message($(`${prefix}-file-status`), preserve && data.version !== state.configVersions[prefix] ? "Die gespeicherte Datei wurde geändert. Dein Entwurf und sein Versionsstand bleiben erhalten." : `Gespeicherte Datei · geladen um ${readTime(data.observed_at || new Date().toISOString())}`);
      if (preserve) return true;
      const scroll = content.scrollTop;
      if (content.value !== data.content) content.value = data.content;
      if (background) content.scrollTop = scroll;
      state.configVersions[prefix] = data.version;
      if (prefix === "geyser") state.geyserOriginal = data.content;
      else {
        state.missionOriginal = data.content;
        state.mission = state.configs.find((config) => config.id === id);
        $("mission-config").value = id;
        $("mission-description").textContent = state.mission?.description || descriptions[id] || state.mission?.protected_note || "Erweiterte Konfiguration.";
        $("reload-mission").textContent = state.mission.reload_target ? "Quests neu laden" : "Minecraft neu starten";
      }
      if (!background) message($(`${prefix}-message`), "Gespeicherte Datei geladen. Beim Speichern wird eine Sicherung angelegt.");
      return true;
    } finally {
      updateEditors();
    }
  }

  async function loadMissions() {
    const data = await api("/api/configs");
    state.configs = data.configs.filter((config) => ["quests", "quest-settings", "permissions"].includes(config.id) && config.available !== false);
    const select = $("mission-config");
    select.replaceChildren();
    for (const config of state.configs) {
      const option = document.createElement("option");
      option.value = config.id;
      option.textContent = config.name;
      select.append(option);
    }
    if (!state.configs.length) throw new Error("Keine Missionsdateien verfügbar.");
    await loadConfig(state.configs[0].id, "mission");
  }

  async function saveConfig(id, prefix) {
    if (state.advanced?.id === id && advancedDirty() && !confirmText("Ungespeicherte Änderungen im erweiterten Editor verwerfen und diese Datei speichern?")) return;
    const plugin = pluginForConfig(id);
    if (plugin && pluginDirty(plugin) && !confirmText("Ungespeicherte Formulareinstellungen verwerfen und die YAML-Datei speichern?")) return;
    const content = $(`${prefix}-content`).value;
    if (new TextEncoder().encode(content).length > 256 * 1024) throw new Error("Die Datei darf höchstens 256 KiB groß sein.");
    try {
      const data = await api(`/api/configs/${encodeURIComponent(id)}`, "PUT", { content, ...(state.configVersions[prefix] === undefined ? {} : { version: state.configVersions[prefix] }) });
      if (data.version !== undefined) state.configVersions[prefix] = data.version;
      if (prefix === "geyser") state.geyserOriginal = content;
      else state.missionOriginal = content;
      message($(`${prefix}-message`), data.restart_required ? "Gespeichert und gesichert. Minecraft zum Anwenden neu starten." : `Gespeichert und gesichert. ${prefix === "geyser" ? "Geyser" : "Quests"} zum Anwenden neu laden.`);
      if (state.advanced?.id === id) await loadAdvancedConfig(id, true);
      if (plugin) {
        state.pluginOriginals[plugin] = null;
        try { await loadPluginSettings(plugin, true); }
        catch (error) { message($(`${plugin}-settings-message`), `YAML-Datei gespeichert. Formular bitte erneut laden: ${error.message}`, true); }
      }
    } finally {
      updateEditors();
    }
  }

  async function reload(target) {
    if (dirtyForReload(target)) { notify("Die Änderungen zuerst speichern und anschließend neu laden.", true); return; }
    if (target === "geyser" && !confirmText("Geyser jetzt neu laden? Alle Bedrock-Spieler werden getrennt und müssen erneut beitreten.")) return;
    const data = await api(`/api/reload/${target}`, "POST", {});
    notify(t("Neuladen von {plugin} angefordert.", { plugin: target === "geyser" ? "Geyser" : "Quests" }) + (data.output ? `\n${data.output}` : ""));
    await loadPluginSettings(target, true);
  }

  async function navigate(view) {
    if (!state.authenticated || !Object.hasOwn(titles, view)) return;
    if (view === "support" && state.modules?.support === false) view = "overview";
    const serial = ++navigationRequest;
    state.view = view;
    if (view !== "map") { mapRequest?.abort(); mapRequest = null; viewerRequest?.abort(); viewerRequest = null; }
    if (view !== "minecraft") closeCompletion();
    document.querySelectorAll(".view").forEach((section) => { section.hidden = section.id !== `view-${view}`; });
    document.querySelectorAll("[data-view]").forEach((link) => {
      if (link.dataset.view === view) link.setAttribute("aria-current", "page");
      else link.removeAttribute("aria-current");
    });
    $("page-title").textContent = titles[view];
    document.title = `${t(titles[view])} · ARK Expedition`;
    if (view === "map") { await Promise.allSettled([refreshMap(), refreshMapViewer()]); return; }
    if (view === "support") { await supportUI.refresh(); return; }
    if (state.loaded.has(view)) { await refreshVisibleSettings(true); return; }
    try {
      if (view === "overview") await refreshStatus();
      if (view === "minecraft") await loadSettings();
      if (view === "worlds") await loadWorlds();
      if (view === "players") await Promise.allSettled([loadPlayers(), loadResetAccounts()]);
      if (view === "configs") await loadAdvancedConfigs();
      if (view === "geyser" || view === "missions") {
        const target = view === "geyser" ? "geyser" : "quests";
        const prefix = view === "geyser" ? "geyser" : "mission";
        const loaded = await withEditorLock(target, async () => {
          const results = await Promise.allSettled([loadPluginSettings(target), view === "geyser" ? loadConfig("geyser", "geyser") : loadMissions()]);
          const errors = [];
          results.forEach((result, index) => {
            if (result.status === "rejected") {
              message($(index === 0 ? `${target}-settings-message` : `${prefix}-message`), result.reason.message, true);
              errors.push(result.reason.message);
            }
          });
          if (errors.length) throw new Error(errors.join("\n"));
        });
        if (!loaded) return;
      }
      if (view === "dns") $("dns-frame").src = "/dns/";
      if (serial === navigationRequest) state.loaded.add(view);
    } catch (error) { notify(error.message, true); }
  }

  async function showApp(data) {
    state.authenticated = true;
    state.sessionSerial++;
    state.formVersions = {};
    state.csrf = data.csrf;
    state.loaded.clear();
    state.mission = null;
    state.geyserOriginal = null;
    state.missionOriginal = null;
    state.settingsOriginal = null;
    state.advancedOriginal = null; state.advanced = null; state.configVersions = {};
    $("world-controls").replaceChildren(); $("world-rules").replaceChildren();
    state.pluginOriginals = { geyser: null, quests: null };
    $("notification").hidden = true;
    $("signed-in-user").textContent = data.username;
    await loadModules();
    await navigate(location.hash.slice(1) || "overview");
  }

  function updateAdvancedEditor() {
    const selected = state.advanced;
    const busy = state.editorBusy.size > 0;
    const stopped = state.services.find((service) => service.id === "minecraft")?.state;
    const canSave = !selected?.requires_stopped || ["exited", "created", "dead"].includes(stopped);
    $("advanced-content").disabled = busy || state.advancedOriginal === null;
    $("advanced-config").disabled = busy || !state.allConfigs.length;
    $("save-advanced").disabled = busy || state.advancedOriginal === null || !canSave;
    $("refresh-advanced").disabled = busy || !(selected || $("advanced-config").value);
    $("advanced-counter").textContent = state.advancedOriginal === null ? "" : `${advancedDirty() ? "Ungespeichert · " : ""}${$("advanced-content").value.length.toLocaleString(window.ArkI18n?.language || "de")} Zeichen`;
  }

  async function loadAdvancedConfigs() {
    const data = await api("/api/configs"); state.allConfigs = data.configs;
    const picker = $("advanced-config"); picker.replaceChildren();
    const groups = new Map();
    for (const config of data.configs) {
      const group = config.group || "Weitere Dateien";
      if (!groups.has(group)) { const section = document.createElement("optgroup"); section.label = group; picker.append(section); groups.set(group, section); }
      const option = document.createElement("option"); option.value = config.id; option.textContent = `${config.name}${config.available === false ? " · nicht vorhanden" : ""}`; option.disabled = config.available === false; groups.get(group).append(option);
    }
    const first = data.configs.find((config) => config.available !== false);
    await refreshStatus().catch(() => {});
    if (first) await loadAdvancedConfig(first.id);
    else message($("advanced-message"), "Keine Konfigurationsdatei vorhanden.");
    updateAdvancedEditor();
  }

  async function loadAdvancedConfig(id, discard = false, background = false) {
    if (!background && !discard && advancedDirty() && !confirmText("Ungespeicherte Dateiänderungen verwerfen?")) return false;
    const serial = ++rawRequests.advanced, session = state.sessionSerial;
    const data = await api(`/api/configs/${encodeURIComponent(id)}`);
    if (!state.authenticated || session !== state.sessionSerial || serial !== rawRequests.advanced) return false;
    const content = $("advanced-content");
    const preserve = background && (advancedDirty() || content === document.activeElement);
    message($("advanced-file-status"), preserve && data.version !== state.configVersions.advanced ? "Die gespeicherte Datei wurde geändert. Dein Entwurf und sein Versionsstand bleiben erhalten." : `Gespeicherte Datei · geladen um ${readTime(data.observed_at || new Date().toISOString())}`);
    if (preserve) return true;
    const scroll = content.scrollTop;
    state.advanced = state.allConfigs.find((config) => config.id === id);
    state.advancedOriginal = data.content; state.configVersions.advanced = data.version;
    $("advanced-config").value = id; if (content.value !== data.content) content.value = data.content;
    if (background) content.scrollTop = scroll;
    const config = state.advanced;
    $("advanced-note").textContent = [config.description, config.format?.toUpperCase(), config.requires_stopped ? "Zum Speichern Minecraft zuerst auf der Übersicht stoppen." : config.restart_required ? "Änderungen nach einem Minecraft-Neustart wirksam." : "Hinweise der Komponente zum Anwenden beachten.", config.protected_note].filter(Boolean).join(" · ");
    if (!background) message($("advanced-message"), "Gespeicherte Datei geladen. Beim Speichern wird eine Sicherung angelegt.");
    updateAdvancedEditor(); return true;
  }

  async function saveAdvancedConfig() {
    const config = state.advanced;
    const otherDirty = (config.id === "geyser" && geyserDirty()) || (state.mission?.id === config.id && missionDirty()) || (pluginForConfig(config.id) && pluginDirty(pluginForConfig(config.id)));
    if (otherDirty && !confirmText("Ungespeicherte Änderungen im zugehörigen Formular oder Datei-Editor verwerfen und diese Datei speichern?")) return;
    const content = $("advanced-content").value;
    if (new TextEncoder().encode(content).length > 256 * 1024) throw new Error("Die Datei darf höchstens 256 KiB groß sein.");
    const result = await api(`/api/configs/${encodeURIComponent(config.id)}`, "PUT", { content, ...(state.configVersions.advanced === undefined ? {} : { version: state.configVersions.advanced }) });
    state.advancedOriginal = content;
    if (result.version !== undefined) state.configVersions.advanced = result.version;
    message($("advanced-message"), result.restart_required || config.restart_required ? "Gespeichert und gesichert. Minecraft zum Anwenden neu starten." : "Gespeichert und gesichert. Änderungen nach den Hinweisen der Komponente anwenden.");
    const tasks = [];
    if (config.id === "geyser" && state.geyserOriginal !== null) tasks.push(loadConfig(config.id, "geyser", true));
    if (state.mission?.id === config.id) tasks.push(loadConfig(config.id, "mission", true));
    const plugin = pluginForConfig(config.id);
    if (plugin && state.pluginOriginals[plugin] !== null) tasks.push(loadPluginSettings(plugin, true));
    for (const task of await Promise.allSettled(tasks)) if (task.status === "rejected") message($("advanced-message"), `Datei gespeichert. Zugehöriges Formular bitte erneut laden: ${task.reason.message}`, true);
    updateEditors();
  }

  function markSaved(form) { for (const input of form.querySelectorAll("[name]")) input.dataset.savedValue = JSON.stringify(inputValue(input)); }
  function formValues(form) { return Object.fromEntries([...form.querySelectorAll("[name]")].map(input => [input.name, inputValue(input)])); }
  function worldValues(world, action, rule) {
    if (action === "rule") return { [rule]: world.rules.find(entry => entry.name === rule)?.value };
    if (["spawn", "border"].includes(action)) return world[action] || {};
    return { [action]: world[action] };
  }

  function updateWorldForm(form, world, applied) {
    const values = worldValues(world, form.dataset.action, form.dataset.rule);
    for (const input of form.querySelectorAll("[name]")) {
      if (!Object.hasOwn(values, input.name) || values[input.name] === undefined) continue;
      const value = values[input.name];
      const dirty = JSON.stringify(inputValue(input)) !== input.dataset.savedValue;
      const submittedUnchanged = applied?.form === form && JSON.stringify(inputValue(input)) === JSON.stringify(applied.inputs[input.name]);
      if (submittedUnchanged || (!dirty && document.activeElement !== input)) {
        setInputValue(input, value); input.dataset.savedValue = JSON.stringify(value);
      }
      input.dataset.liveValue = JSON.stringify(value);
      let detail = document.getElementById(`${input.id}-live`);
      if (!detail) {
        detail = document.createElement("small"); detail.id = `${input.id}-live`; detail.className = "world-live-value"; input.closest(".field").append(detail);
        input.setAttribute("aria-describedby", [input.getAttribute("aria-describedby"), detail.id].filter(Boolean).join(" "));
      }
      const text = `Aktiv: ${valueText(value, input.name)}`;
      if (detail.textContent !== text) detail.textContent = text;
    }
    const button = form.querySelector("button[type=submit]");
    if (!button.hasAttribute("aria-busy")) button.disabled = false;
  }

  function worldForm(action, title, fields, rule = "") {
    const form = document.createElement("form"); form.className = "world-action";
    form.dataset.action = action; form.dataset.rule = rule; form.dataset.world = state.currentWorld;
    const heading = document.createElement("h3"); heading.textContent = title; form.append(heading);
    const grid = document.createElement("div"); grid.className = "form-grid"; form.append(grid);
    for (const field of fields) addField(grid, field, field.value, "world");
    const button = document.createElement("button"); button.type = "submit"; button.className = "button"; button.textContent = "Anwenden";
    const status = document.createElement("p"); status.className = "form-message"; status.setAttribute("role", "status");
    form.append(button, status); markSaved(form);
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      run(button, async () => {
        state.worldBusy = true;
        try {
          const key = form.dataset.world, inputs = formValues(form);
          const changes = Object.fromEntries([...form.querySelectorAll("[name]")].filter(input => JSON.stringify(inputs[input.name]) !== input.dataset.savedValue).map(input => [input.name, inputs[input.name]]));
          const fresh = (await api("/api/worlds").catch(error => { if (state.currentWorld === key && state.view === "worlds") worldsUnavailable(); throw error; })).worlds.find(world => world.key === key);
          if (!fresh) throw new Error("Diese Welt ist derzeit nicht geladen. Dein Entwurf bleibt erhalten.");
          const current = { ...worldValues(fresh, action, rule), ...changes };
          const value = fields.length === 1 ? current[fields[0].key] : current;
          const body = action === "rule" ? { action, name: rule, value } : { action, value };
          await api(`/api/worlds/${encodeURIComponent(key)}`, "PUT", body);
          if (state.currentWorld === key && $("world-select").value === key && state.view === "worlds") await loadWorlds(undefined, { background: true, applied: { form, inputs } });
          message(status, "Angewendet und aktuellen Weltwert nachgelesen.");
        } finally { state.worldBusy = false; }
      }, status);
    });
    return form;
  }

  function renderWorld(applied) {
    const world = state.worlds.find((entry) => entry.key === state.currentWorld);
    if (!world) return;
    if ($("world-controls").dataset.world === world.key && $("world-controls").children.length) {
      for (const form of $("view-worlds").querySelectorAll("form[data-action]")) updateWorldForm(form, world, applied);
      return;
    }
    $("world-controls").replaceChildren(); $("world-rules").replaceChildren(); $("world-controls").dataset.world = world.key;
    const choices = (values, labels) => values.map((value, index) => ({ value, label: labels[index] }));
    const controls = $("world-controls"); controls.className = "world-controls";
    controls.append(worldForm("difficulty", "Schwierigkeit", [{ key: "difficulty", label: "Schwierigkeit", description: "Bestimmt die Stärke und Gefährlichkeit der Gegner in dieser Welt.", type: "choice", options: choices(["peaceful", "easy", "normal", "hard"], ["Friedlich", "Einfach", "Normal", "Schwer"]), value: world.difficulty }]),
      worldForm("time", "Tageszeit", [{ key: "time", label: "Zeit · 0–23.999", description: "0 = Morgen, 6.000 = Mittag, 12.000 = Abend, 18.000 = Mitternacht.", type: "integer", min: 0, max: 23999, value: world.time }]),
      worldForm("weather", "Wetter", [{ key: "weather", label: "Wetter", description: "Setzt klares Wetter, Regen oder Gewitter ausschließlich in dieser Welt.", type: "choice", options: choices(["clear", "rain", "thunder"], ["Klar", "Regen", "Gewitter"]), value: world.weather }]));
    const labels = { x: "X", y: "Y", z: "Z", yaw: "Blickrichtung", pitch: "Blickneigung", size: "Breite in Blöcken", centerX: "Mittelpunkt X", centerZ: "Mittelpunkt Z" };
    const help = { x: "Startposition von Westen nach Osten; positive X-Werte liegen im Osten.", y: "Höhe des Startpunkts. Wähle einen sicheren Boden mit freiem Platz darüber.", z: "Startposition von Norden nach Süden; positive Z-Werte liegen im Süden.", yaw: "Drehung in Grad: 0 = Süden, 90 = Westen, 180 = Norden, −90 = Osten.", pitch: "Blickwinkel: 0 = geradeaus, 90 = nach unten, −90 = nach oben.", size: "Breite der quadratischen Weltgrenze; vorhandene Landschaft wird dadurch nicht gelöscht.", centerX: "Verschiebt den Mittelpunkt der Weltgrenze entlang der Ost-West-Achse.", centerZ: "Verschiebt den Mittelpunkt der Weltgrenze entlang der Nord-Süd-Achse." };
    for (const [action, title, keys] of [["spawn", "Startpunkt", ["x", "y", "z", "yaw", "pitch"]], ["border", "Weltgrenze", ["size", "centerX", "centerZ"]]]) {
      const detail = document.createElement("details"); detail.className = "world-detail";
      const summary = document.createElement("summary"); summary.textContent = title;
      detail.append(summary, worldForm(action, title, keys.map(key => ({ key, label: labels[key], description: help[key], type: "number", value: world[action]?.[key] })))); controls.append(detail);
    }
    for (const rule of world.rules || []) {
      const label = rule.label || (rule.name === "minecraft:pvp" ? "Schaden zwischen Spielern (PvP)" : rule.name);
      const form = worldForm("rule", label, [{ key: rule.name, label, description: rule.description || "Zusätzliche Spielregel dieser Serverversion; Wirkung vor dem Ändern prüfen.", type: rule.type, value: rule.value }], rule.name);
      form.classList.add("rule-row"); form.dataset.search = `${rule.name} ${label} ${rule.description || ""}`.toLocaleLowerCase("de");
      const technical = document.createElement("code"); technical.className = "rule-key"; technical.textContent = rule.name; form.querySelector("h3").append(technical);
      if (rule.default !== undefined) { const hint = document.createElement("p"); hint.className = "field-hint"; hint.textContent = `Standardwert: ${valueText(rule.default)}`; form.append(hint); }
      $("world-rules").append(form);
    }
    for (const form of $("view-worlds").querySelectorAll("form[data-action]")) updateWorldForm(form, world);
    filterRules();
  }

  function worldsUnavailable(text) {
    for (const value of $("view-worlds").querySelectorAll(".world-live-value")) value.textContent = value.textContent.replace(/^Aktiv:/, "Zuletzt bestätigt:");
    for (const button of $("view-worlds").querySelectorAll("form button[type=submit]")) if (!button.hasAttribute("aria-busy")) button.disabled = true;
    message($("worlds-live-status"), text || "Live-Werte derzeit nicht erreichbar. Entwürfe bleiben erhalten; nächster Abruf automatisch.");
  }

  async function loadWorlds(preferred, { background = false, applied } = {}) {
    if (!background && worldDirty() && !confirmText("Ungespeicherte Weltregeln verwerfen und erneut laden?")) { $("world-select").value = state.currentWorld; return; }
    const serial = ++worldsRequest, session = state.sessionSerial, view = state.view;
    if (!background) { $("world-controls").inert = true; $("world-rules").inert = true; }
    try {
      const data = await api("/api/worlds");
      if (!state.authenticated || session !== state.sessionSerial || serial !== worldsRequest || (background && state.view !== view)) return;
      const worlds = data.worlds || [], selected = preferred || state.currentWorld;
      if (background && worldDirty() && !worlds.some(world => world.key === selected)) { worldsUnavailable("Die gewählte Welt ist derzeit nicht geladen. Dein Entwurf bleibt erhalten."); return; }
      state.worlds = worlds;
      const next = state.worlds.some(world => world.key === selected) ? selected : state.worlds[0]?.key;
      if (!background && !preferred && state.currentWorld === next) { $("world-controls").replaceChildren(); $("world-rules").replaceChildren(); }
      state.currentWorld = next;
      const options = [...$("world-select").options];
      if (options.length !== worlds.length || worlds.some((world, i) => options[i]?.value !== world.key || options[i]?.textContent !== world.name)) {
        $("world-select").replaceChildren();
        for (const world of worlds) { const option = document.createElement("option"); option.value = world.key; option.dataset.i18nSkip = ""; option.textContent = world.name; $("world-select").append(option); }
      }
      $("world-select").value = state.currentWorld || ""; $("world-select").disabled = !worlds.length;
      renderWorld(applied);
      message($("worlds-live-status"), `Aktive Weltwerte${readTime(data.observed_at) ? ` von ${readTime(data.observed_at)}` : " abgerufen"} · automatisch alle 15 Sekunden`);
      if (!background) message($("worlds-message"), worlds.length ? "Aktuelle Weltwerte geladen. Jede Änderung einzeln anwenden." : "Weltdaten werden vorbereitet. Bitte erneut laden.");
    } catch (error) {
      if (!state.authenticated || session !== state.sessionSerial || serial !== worldsRequest || (background && state.view !== view)) { if (applied) throw error; return; }
      worldsUnavailable();
      if (!background) message($("worlds-message"), error.message, true);
      if (applied) throw error;
    } finally {
      if (serial === worldsRequest) { $("world-controls").inert = false; $("world-rules").inert = false; }
    }
  }
  function filterRules() { const query = $("rule-search").value.toLocaleLowerCase(window.ArkI18n?.language || "de"); for (const row of $("world-rules").children) row.hidden = !(row.dataset.search + " " + row.textContent.toLocaleLowerCase(window.ArkI18n?.language || "de")).includes(query); }

  function choosePlayer(name, action) {
    $("player-name").value = name; if (action) $("player-action").value = action; renderPlayerAction(); $("player-name").focus();
  }
  async function loadPlayers() {
    try {
      const data = await api("/api/players");
      $("players-table").replaceChildren(); $("player-names").replaceChildren();
      const names = new Set([...(data.players || []).map((player) => player.name), ...(data.whitelist || []), ...(data.banned || [])]);
      for (const name of names) { const option = document.createElement("option"); option.value = name; $("player-names").append(option); }
      for (const player of data.players || []) {
        const row = document.createElement("div"); row.className = "player-row";
        const detail = document.createElement("div"); const name = document.createElement("strong"); name.dataset.i18nSkip = ""; name.textContent = player.name;
        const info = document.createElement("p"); info.className = "field-hint"; info.textContent = [player.world, player.gamemode, player.op ? "Administrator" : "Spieler", player.whitelisted ? "Whitelist" : ""].filter(Boolean).join(" · ");
        detail.append(name, info); const button = document.createElement("button"); button.type = "button"; button.className = "button quiet"; button.textContent = "Verwalten"; button.addEventListener("click", () => choosePlayer(player.name)); row.append(detail, button); $("players-table").append(row);
      }
      if (!data.players?.length) $("players-table").textContent = "Gerade ist niemand online.";
      for (const [id, entries, action] of [["whitelist-players", data.whitelist, "whitelist-remove"], ["banned-players", data.banned, "pardon"]]) {
        $(id).replaceChildren();
        for (const name of entries || []) { const button = document.createElement("button"); button.type = "button"; button.className = "button quiet"; button.dataset.i18nSkip = ""; button.textContent = name; button.addEventListener("click", () => choosePlayer(name, action)); $(id).append(button); }
        if (!entries?.length) $(id).textContent = "Keine Einträge.";
      }
      if (!state.worlds.length) { try { state.worlds = (await api("/api/worlds")).worlds || []; } catch { } }
      renderPlayerAction(); message($("players-admin-message"), "Spieler und Zugangslisten geladen.");
    } catch (error) { message($("players-admin-message"), error.message, true); }
  }
  async function loadResetAccounts() {
    const session = state.sessionSerial;
    try {
      const data = await api("/api/player-accounts");
      if (!state.authenticated || session !== state.sessionSerial) return;
      $("reset-player-names").replaceChildren();
      for (const account of data.accounts) { const option = document.createElement("option"); option.value = account.name; $("reset-player-names").append(option); }
    } catch (error) { if (state.authenticated && session === state.sessionSerial) message($("reset-player-message"), error.message, true); }
  }
  $("reset-player-form").addEventListener("submit", event => {
    event.preventDefault();
    const username = $("reset-player-name").value.trim();
    if (!window.confirm(t("Serverpasswort für {name} zurücksetzen? Alle Websitzungen dieses Spielerkontos werden beendet.", { name: username }))) return;
    run($("reset-player-submit"), async () => {
      await api("/api/player-accounts/password", "POST", { username, new_password: $("reset-player-password").value, confirmation: $("reset-player-confirm").value });
      $("reset-player-password").value = $("reset-player-confirm").value = "";
      message($("reset-player-message"), "Serverpasswort zurückgesetzt. Alle Websitzungen dieses Spielerkontos wurden beendet; Wiederherstellungscodes bleiben erhalten.");
    }, $("reset-player-message"));
  });
  function renderPlayerAction() {
    const target = $("player-action-fields"); target.replaceChildren();
    const action = $("player-action").value;
    if (["kick", "ban"].includes(action)) addField(target, { key: "reason", label: "Grund (optional)", type: "string" }, "", "player");
    if (action === "gamemode") addField(target, { key: "value", label: "Spielmodus", type: "choice", options: [{ value: "survival", label: "Überleben" }, { value: "creative", label: "Kreativ" }, { value: "adventure", label: "Abenteuer" }, { value: "spectator", label: "Zuschauer" }] }, "survival", "player");
    if (action === "world") addField(target, { key: "value", label: "Zielwelt", type: "choice", originalOptions: true, options: state.worlds.map((world) => ({ value: world.key, label: world.name })) }, state.worlds[0]?.key, "player");
    if (action === "teleport") for (const key of ["x", "y", "z"]) addField(target, { key, label: key.toUpperCase(), type: "number" }, key === "y" ? 64 : 0, "player");
  }

  function closeCompletion() {
    clearTimeout(completionTimer); completionRequest?.abort(); completionRequest = null; completionSerial++;
    completion = null; $("console-completion-status").textContent = ""; $("console-suggestions").replaceChildren(); $("console-suggestions").hidden = true;
    $("console-command").setAttribute("aria-expanded", "false"); $("console-command").removeAttribute("aria-activedescendant");
  }
  function selectCompletion(index) {
    if (!completion?.suggestions.length) return;
    completion.active = (index + completion.suggestions.length) % completion.suggestions.length;
    for (const [position, item] of [...$("console-suggestions").children].entries()) item.setAttribute("aria-selected", String(position === completion.active));
    const item = $("console-suggestions").children[completion.active]; $("console-command").setAttribute("aria-activedescendant", item.id); item.scrollIntoView({ block: "nearest" });
  }
  function acceptCompletion() {
    const input = $("console-command");
    if (!completion || input.value !== completion.input || input.selectionStart !== completion.cursor || input.selectionEnd !== completion.cursor) { closeCompletion(); return; }
    const item = completion.suggestions[completion.active < 0 ? 0 : completion.active]; if (!item) return;
    const edited = completionEdit(input.value, completion, item.text);
    input.value = edited.value; input.setSelectionRange(edited.caret, edited.caret); closeCompletion(); input.focus(); queueCompletion();
  }
  function queueCompletion() {
    closeCompletion();
    const input = $("console-command");
    if (!state.authenticated || state.view !== "minecraft" || !input.value || input.selectionStart !== input.selectionEnd) return;
    const serial = completionSerial;
    completionTimer = setTimeout(async () => {
      const snapshot = { input: input.value, cursor: input.selectionStart };
      const controller = new AbortController(); completionRequest = controller;
      try {
        const data = await api("/api/console/complete", "POST", snapshot, controller.signal);
        if (serial !== completionSerial || controller.signal.aborted || !state.authenticated || input.value !== snapshot.input || input.selectionStart !== snapshot.cursor || input.selectionEnd !== snapshot.cursor) return;
        const result = completionChoices(data, snapshot.input);
        if (!result?.suggestions.length) { message($("console-completion-status"), !data.available || !data.ready ? "Vorschläge werden vorbereitet. Befehle lassen sich weiterhin senden." : ""); return; }
        completion = { ...snapshot, ...result, active: -1 };
        $("console-suggestions").replaceChildren();
        for (const [index, suggestion] of completion.suggestions.entries()) {
          const row = document.createElement("li"); row.id = `console-option-${index}`; row.setAttribute("role", "option"); row.setAttribute("aria-selected", "false");
          const button = document.createElement("button"); button.type = "button"; button.tabIndex = -1; button.textContent = displayText(suggestion.text); if (suggestion.tooltip) button.title = displayText(suggestion.tooltip);
          button.addEventListener("pointerdown", (event) => event.preventDefault());
          button.addEventListener("click", () => { if (completion) { completion.active = index; acceptCompletion(); } });
          row.append(button); $("console-suggestions").append(row);
        }
        $("console-suggestions").hidden = false; input.setAttribute("aria-expanded", "true"); message($("console-completion-status"), `${completion.suggestions.length} Vorschläge · Tab übernimmt, Pfeiltasten wählen.`);
      } catch (error) { if (error.name !== "AbortError" && serial === completionSerial && state.authenticated) message($("console-completion-status"), `Vorschläge derzeit nicht verfügbar: ${error.message}`); }
      finally { if (completionRequest === controller) completionRequest = null; }
    }, 180);
  }

  window.ArkAdmin = { hasDirty: () => state.authenticated && anyDirty() };
  window.addEventListener("ark:session", event => {
    const data = event.detail;
    if (!data.authenticated || data.is_admin !== true) { clearAdmin(); return; }
    state.csrf = data.csrf;
    if (!state.authenticated) showApp(data).catch(error => { if (state.authenticated) notify(error.message, true); });
  });
  $("account-button").addEventListener("click", () => {
    $("password-form").reset();
    message($("password-message"), "");
    $("password-dialog").showModal();
  });
  $("password-close").addEventListener("click", () => $("password-dialog").close());
  $("password-dialog").addEventListener("close", () => $("password-form").reset());
  $("password-form").addEventListener("submit", (event) => {
    event.preventDefault();
    run($("password-save"), async () => {
      const data = await api("/api/password", "POST", {
        current_password: $("current-password").value,
        new_password: $("new-password").value,
        confirm_password: $("confirm-password").value
      });
      state.csrf = data.csrf;
      window.dispatchEvent(new CustomEvent("ark:csrf", { detail: data.csrf }));
      $("signed-in-user").textContent = data.username;
      $("password-form").reset();
      message($("password-message"), "Passwort geändert. Andere Anmeldungen wurden beendet.");
    }, $("password-message"));
  });
  window.addEventListener("hashchange", () => { if (state.authenticated) navigate(location.hash.slice(1)); });
  window.addEventListener("beforeunload", (event) => { if (state.authenticated && anyDirty()) { event.preventDefault(); event.returnValue = ""; } });
  $("refresh-status").addEventListener("click", () => run($("refresh-status"), async () => { await loadModules(); await refreshStatus(); }));
  document.querySelectorAll(".service-card [data-action]").forEach((button) => button.addEventListener("click", () => {
    serviceAction(button.closest("[data-service]").dataset.service, button.dataset.action).catch((error) => notify(error.message, true));
  }));
  $("refresh-logs").addEventListener("click", () => run($("refresh-logs"), async () => {
    const data = await api(`/api/logs/${$("log-service").value}?tail=200`);
    $("logs-output").textContent = displayText(data.text) || "Keine Meldungen vorhanden.";
    $("logs-output").scrollTop = $("logs-output").scrollHeight;
  }));
  $("refresh-settings").addEventListener("click", () => runEditor("minecraft", $("refresh-settings"), loadSettings, $("minecraft-message")));
  $("minecraft-form").addEventListener("submit", (event) => {
    event.preventDefault();
    runEditor("minecraft", event.submitter, async () => {
      const settings = settingsFromForm();
      if (typeof state.formVersions.minecraft !== "string") throw new Error("Bitte die Einstellungen erneut laden; der Versionsnachweis fehlt.");
      const result = await api("/api/settings/minecraft", "PUT", { settings, version: state.formVersions.minecraft });
      state.settingsOriginal = JSON.stringify(settings); state.formVersions.minecraft = result.version;
      await loadSettings();
      message($("minecraft-message"), "Gespeichert. Aktive Werte wurden neu abgefragt; Startvorgaben werden beim nächsten Neustart gelesen.");
    }, $("minecraft-message"));
  });
  $("minecraft-restart").addEventListener("click", () => {
    if (settingsDirty()) { message($("minecraft-message"), "Die Änderungen zuerst speichern, damit sie beim Neustart aktiv werden.", true); return; }
    run($("minecraft-restart"), () => serviceAction("minecraft", "restart"));
  });
  $("console-form").addEventListener("submit", (event) => {
    event.preventDefault();
    closeCompletion();
    run(event.submitter || $("console-form").querySelector("button[type=submit]"), async () => {
      const command = $("console-command").value.trim();
      if (!command || /[\r\n\0]/.test(command)) throw new Error("Bitte genau einen Befehl ohne Zeilenumbruch eingeben.");
      const data = await api("/api/console", "POST", { command });
      const output = $("console-output");
      output.textContent = `${output.dataset.history ? `${output.textContent}\n\n` : ""}› ${displayText(command)}\n${displayText(data.output) || "Befehl ohne Ausgabe ausgeführt."}`.slice(-128 * 1024);
      output.dataset.history = "true";
      output.scrollTop = output.scrollHeight;
      $("console-command").value = "";
      $("console-command").focus();
    });
  });
  for (const target of ["geyser", "quests"]) {
    $(`${target}-settings-form`).addEventListener("input", updateEditors);
    $(`${target}-settings-form`).addEventListener("change", updateEditors);
    $(`${target}-settings-form`).addEventListener("submit", (event) => {
      event.preventDefault();
      runEditor(target, event.submitter, () => savePluginSettings(target), $(`${target}-settings-message`));
    });
    $(`refresh-${target}-settings`).addEventListener("click", () => runEditor(target, $(`refresh-${target}-settings`), () => loadPluginSettings(target), $(`${target}-settings-message`)));
    $(`apply-${target}-settings`).addEventListener("click", () => runEditor(target, $(`apply-${target}-settings`), () => reload(target)));
  }
  for (const prefix of ["geyser", "mission"]) {
    $(`${prefix}-content`).addEventListener("input", updateEditors);
    $(`${prefix}-form`).addEventListener("submit", (event) => {
      event.preventDefault();
      runEditor(prefix === "geyser" ? "geyser" : "quests", event.submitter, () => saveConfig(prefix === "geyser" ? "geyser" : state.mission.id, prefix), $(`${prefix}-message`));
    });
  }
  $("refresh-geyser").addEventListener("click", () => runEditor("geyser", $("refresh-geyser"), () => loadConfig("geyser", "geyser"), $("geyser-message")));
  $("reload-geyser").addEventListener("click", () => runEditor("geyser", $("reload-geyser"), () => reload("geyser")));
  $("mission-config").addEventListener("change", () => runEditor("quests", $("mission-config"), async () => {
    const previous = state.mission?.id || state.configs[0]?.id;
    try { if (!await loadConfig($("mission-config").value, "mission")) $("mission-config").value = previous; }
    catch (error) { $("mission-config").value = previous; throw error; }
  }, $("mission-message")));
  $("refresh-mission").addEventListener("click", () => runEditor("quests", $("refresh-mission"), () => loadConfig(state.mission?.id || $("mission-config").value, "mission"), $("mission-message")));
  $("reload-mission").addEventListener("click", () => runEditor("quests", $("reload-mission"), () => state.mission.reload_target ? reload(state.mission.reload_target) : serviceAction("minecraft", "restart")));

  $("refresh-map").addEventListener("click", () => run($("refresh-map"), () => Promise.allSettled([refreshMap(), refreshMapViewer()]), $("map-viewer-status")));
  $("settings-search").addEventListener("input", () => {
    const query = $("settings-search").value.toLocaleLowerCase(window.ArkI18n?.language || "de");
    for (const field of $("minecraft-dynamic-fields").querySelectorAll("[data-search]")) field.hidden = !(field.dataset.search + " " + field.textContent.toLocaleLowerCase(window.ArkI18n?.language || "de")).includes(query);
    for (const group of $("minecraft-dynamic-fields").children) group.hidden = ![...group.querySelectorAll("[data-search]")].some((field) => !field.hidden);
  });
  $("advanced-content").addEventListener("input", updateAdvancedEditor);
  $("advanced-config").addEventListener("change", () => runEditor("advanced", $("advanced-config"), async () => {
    const previous = state.advanced?.id;
    try { if (!await loadAdvancedConfig($("advanced-config").value)) $("advanced-config").value = previous; }
    catch (error) { $("advanced-config").value = previous; throw error; }
  }, $("advanced-message")));
  $("refresh-advanced").addEventListener("click", () => runEditor("advanced", $("refresh-advanced"), () => loadAdvancedConfig(state.advanced?.id || $("advanced-config").value), $("advanced-message")));
  $("advanced-form").addEventListener("submit", (event) => { event.preventDefault(); runEditor("advanced", $("save-advanced"), saveAdvancedConfig, $("advanced-message")); });
  $("refresh-worlds").addEventListener("click", () => run($("refresh-worlds"), () => loadWorlds(), $("worlds-message")));
  $("world-select").addEventListener("change", () => run($("world-select"), () => loadWorlds($("world-select").value), $("worlds-message")));
  $("rule-search").addEventListener("input", filterRules);
  $("refresh-players").addEventListener("click", () => run($("refresh-players"), loadPlayers, $("players-admin-message")));
  $("player-action").addEventListener("change", renderPlayerAction);
  $("player-action-form").addEventListener("submit", (event) => {
    event.preventDefault();
    run($("player-action-submit"), async () => {
      const action = $("player-action").value, player = $("player-name").value.trim();
      if (["op", "ban", "kick"].includes(action) && !confirmText(`${player}: ${$("player-action").selectedOptions[0].textContent} jetzt anwenden?`)) return;
      const values = Object.fromEntries([...$("player-action-fields").querySelectorAll("[name]")].map((input) => [input.name, inputValue(input)]));
      const result = await api("/api/players/action", "POST", { action, player, ...values });
      $("player-action-message").dataset.i18nSkip = ""; $("player-action-message").textContent = result.output ? displayText(result.output) : t("Aktion angewendet."); await loadPlayers();
    }, $("player-action-message"));
  });
  $("console-command").addEventListener("input", queueCompletion);
  $("console-command").addEventListener("click", queueCompletion);
  $("console-command").addEventListener("keyup", (event) => { if (["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) queueCompletion(); });
  $("console-command").addEventListener("blur", closeCompletion);
  $("console-command").addEventListener("keydown", (event) => {
    if (event.key === "Escape") { closeCompletion(); return; }
    if (!completion?.suggestions.length) return;
    if (["ArrowDown", "ArrowUp"].includes(event.key)) {
      event.preventDefault();
      selectCompletion(completion.active < 0 ? event.key === "ArrowDown" ? 0 : completion.suggestions.length - 1 : completion.active + (event.key === "ArrowDown" ? 1 : -1));
    } else if (event.key === "Tab" || (event.key === "Enter" && completion.active >= 0)) { event.preventDefault(); acceptCompletion(); }
  });
  $("map-fullscreen").addEventListener("click", () => run($("map-fullscreen"), async () => {
    try { await $("map-frame-wrap").requestFullscreen(); }
    catch { throw new Error("Vollbild ist in diesem Browser nicht verfügbar. Bitte „Separat öffnen“ verwenden."); }
  }, $("map-viewer-status")));
  async function refreshVisibleSettings(includeRaw = false) {
    if (!state.authenticated || document.hidden || pollBusy || state.editorBusy.size || state.worldBusy || !state.loaded.has(state.view) || !Object.hasOwn(titles, location.hash.slice(1))) return;
    const view = state.view; pollBusy = true;
    try {
      if (view === "minecraft") {
        try { await loadSettings({ background: true }); } catch { if (state.view === view) settingsUnavailable("minecraft"); }
      } else if (view === "worlds") await loadWorlds(undefined, { background: true });
      else if (view === "geyser" || view === "missions") {
        const target = view === "geyser" ? "geyser" : "quests", prefix = view === "geyser" ? "geyser" : "mission";
        const tasks = [loadPluginSettings(target, false, true).catch(() => { if (state.view === view) settingsUnavailable(target); })];
        const editor = $(`${prefix}-content`), details = editor.closest("details");
        const id = prefix === "geyser" ? "geyser" : state.mission?.id;
        if (id && (includeRaw || details?.open)) tasks.push(loadConfig(id, prefix, false, true).catch(() => message($(`${prefix}-file-status`), "Gespeicherte Datei derzeit nicht erreichbar. Dein Entwurf bleibt erhalten.")));
        await Promise.allSettled(tasks);
      } else if (view === "configs" && state.advanced?.id) await loadAdvancedConfig(state.advanced.id, false, true).catch(() => message($("advanced-file-status"), "Gespeicherte Datei derzeit nicht erreichbar. Dein Entwurf bleibt erhalten."));
    } finally {
      pollBusy = false;
      if (state.view !== view) refreshVisibleSettings(true);
    }
  }
  document.addEventListener("visibilitychange", () => { if (!document.hidden) { refreshMap(); refreshMapViewer(); refreshVisibleSettings(true); } });
  document.addEventListener("toggle", event => { if (event.target.open && event.target.matches("#view-configs details, #view-geyser details, #view-missions details")) refreshVisibleSettings(true); }, true);
  setInterval(() => { refreshMap(); refreshMapViewer(); }, 5000);
  setInterval(() => {
    if (state.authenticated && !document.hidden && Object.hasOwn(titles, location.hash.slice(1))) { refreshStatus().catch((error) => { if (state.authenticated) notify(error.message, true); }); refreshVisibleSettings(); }
  }, 15000);
  window.addEventListener("ark:language", () => { document.title = `${t(titles[state.view])} · ARK Expedition`; updateEditors(); });
})();
