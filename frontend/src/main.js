import {
  escapeAttr,
  escapeHtml,
  formatDate,
  formatDateRange,
  formatMoney,
  getPublicApiBaseUrl,
  resolveApiRequest,
  toAvailabilityLabel
} from "./app.js";

const appRoot = document.getElementById("app");

boot().catch(() => {
  renderError();
});

async function boot() {
  const apiBaseUrl = getPublicApiBaseUrl();
  const requestUrl = resolveApiRequest(window.location.pathname, apiBaseUrl);
  if (!requestUrl) {
    renderNotFound();
    return;
  }

  const response = await fetch(requestUrl, {
    headers: {
      Accept: "application/json"
    }
  });

  if (response.status === 404) {
    renderNotFound();
    return;
  }
  if (!response.ok) {
    renderError();
    return;
  }

  const payload = await response.json();
  renderEvent(payload);
  if (payload?.event?.title) {
    document.title = `${payload.event.title} | Toniq`;
  }
}

function renderEvent(payload) {
  const event = payload.event || {};
  const schedule = payload.schedule || {};
  const venue = payload.venue || {};
  const tickets = Array.isArray(payload.tickets) ? payload.tickets : [];
  const cta = payload.cta || {};
  const hasPhotos = Array.isArray(event.galleryImageUrls) && event.galleryImageUrls.length > 0;
  const heroImageUrl = hasPhotos ? event.galleryImageUrls[0] : null;

  appRoot.className = "event-page";
  appRoot.innerHTML = `
    <section class="hero">
      <div class="hero__media ${hasPhotos ? "" : "hero__media--fallback"}">
        ${hasPhotos
          ? `<img class="hero__image" src="${escapeAttr(heroImageUrl)}" alt="${escapeAttr(event.title || "Event image")}">`
          : `<div class="hero__ambient">
               <span class="hero__orb hero__orb--one"></span>
               <span class="hero__orb hero__orb--two"></span>
               <span class="hero__grain"></span>
             </div>`}
      </div>
      <div class="hero__content">
        <p class="eyebrow">${escapeHtml(event.organiserName || "Toniq event")}</p>
        <h1 class="hero__title">${escapeHtml(event.title || "Untitled event")}</h1>
        <div class="hero__meta">
          ${renderMetaPill(formatDateRange(schedule.startAt, schedule.endAt, schedule.timezone))}
          ${venue.name ? renderMetaPill(escapeHtml(venue.name)) : ""}
        </div>
        <div class="hero__cta">
          <button class="cta-button" type="button" disabled>${escapeHtml(cta.label || "Ticket link coming soon")}</button>
        </div>
      </div>
    </section>
    <section class="content-grid">
      <aside class="panel panel--tickets">
        <div class="panel__header">
          <p class="panel__eyebrow">Tickets</p>
          <h2>Availability snapshot</h2>
        </div>
        ${renderTickets(tickets)}
      </aside>
      <div class="content-grid__main">
        ${renderDescription(event.descriptionHtml)}
        ${renderVenue(venue)}
      </div>
    </section>
  `;
}

function renderMetaPill(content) {
  return `<span class="meta-pill">${content}</span>`;
}

function renderStatusDetail(status) {
  if (status.effectiveStatus === "rescheduled" && status.rescheduledStartAt) {
    const rescheduled = formatDateRange(status.rescheduledStartAt, status.rescheduledEndAt, null);
    return `<p class="hero__status-note">New schedule: ${escapeHtml(rescheduled)}</p>`;
  }
  if (status.effectiveStatus === "cancelled") {
    return `<p class="hero__status-note">This event is visible for communication purposes, but ticket actions are unavailable.</p>`;
  }
  return "";
}

function renderTickets(tickets) {
  if (!tickets.length) {
    return `
      <div class="empty-panel">
        <p class="empty-panel__title">Tickets unavailable</p>
        <p class="empty-panel__body">Check back later for availability.</p>
      </div>
    `;
  }

  return `
    <div class="ticket-list">
      ${tickets.map((ticket) => `
        <article class="ticket-card">
          <div class="ticket-card__top">
            <h3>${escapeHtml(ticket.name || "Tier")}</h3>
            <span class="ticket-card__price">${formatMoney(ticket.facePrice, ticket.currencyCode)}</span>
          </div>
          ${ticket.description ? `<p class="ticket-card__description">${escapeHtml(ticket.description)}</p>` : ""}
          <div class="ticket-card__meta">
            <span>${renderAvailability(ticket)}</span>
            ${ticket.salesEndsAt ? `<span>Ends ${escapeHtml(formatDate(ticket.salesEndsAt))}</span>` : ""}
          </div>
        </article>
      `).join("")}
    </div>
  `;
}

function renderAvailability(ticket) {
  const state = ticket.availabilityState || "unknown";
  const count = ticket.availabilityCount;
  if (typeof count === "number") {
    return `${toAvailabilityLabel(state)} · ${count} left`;
  }
  return toAvailabilityLabel(state);
}

function renderDescription(descriptionHtml) {
  if (!descriptionHtml) {
    return "";
  }
  return `
    <section class="panel">
      <div class="panel__header">
        <p class="panel__eyebrow">Overview</p>
        <h2>About the event</h2>
      </div>
      <div class="rich-copy">${descriptionHtml}</div>
    </section>
  `;
}

function renderVenue(venue) {
  const rows = [
    venue.name ? ["Venue", venue.name] : null,
    venue.address ? ["Address", venue.address] : null
  ].filter(Boolean);

  if (!rows.length) {
    return "";
  }

  return `
    <section class="panel">
      <div class="panel__header">
        <p class="panel__eyebrow">Location</p>
        <h2>Venue details</h2>
      </div>
      <dl class="venue-list">
        ${rows.map(([label, value]) => `<div><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value)}</dd></div>`).join("")}
      </dl>
    </section>
  `;
}

function renderNotFound() {
  appRoot.className = "event-page event-page--not-found";
  appRoot.innerHTML = `
    <section class="empty-state">
      <p class="eyebrow">Toniq</p>
      <h1>Event not found</h1>
      <p>This page is unavailable because the event does not exist or is not publicly published.</p>
    </section>
  `;
}

function renderError() {
  appRoot.className = "event-page event-page--error";
  appRoot.innerHTML = `
    <section class="empty-state">
      <p class="eyebrow">Toniq</p>
      <h1>Unable to load event</h1>
      <p>Please try again later.</p>
    </section>
  `;
}
