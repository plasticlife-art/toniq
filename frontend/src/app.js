export function getPublicApiBaseUrl(config = globalThis.window?.__PUBLIC_CONFIG__) {
  const configured = config?.PUBLIC_API_BASE_URL;
  return trimTrailingSlash(configured || "/api/public");
}

export function resolveApiRequest(pathname, apiBaseUrl) {
  const normalizedBaseUrl = trimTrailingSlash(apiBaseUrl);
  const megatixIdMatch = pathname.match(/^\/events\/id\/([^/]+)$/);
  if (megatixIdMatch) {
    return `${normalizedBaseUrl}/events/${encodeURIComponent(megatixIdMatch[1])}`;
  }

  const slugMatch = pathname.match(/^\/events\/([^/]+)$/);
  if (slugMatch && slugMatch[1] !== "id") {
    return `${normalizedBaseUrl}/events/by-slug/${encodeURIComponent(slugMatch[1])}`;
  }

  return null;
}

export function resolveAvailabilityRefreshRequest(megatixEventId, apiBaseUrl) {
  if (!megatixEventId) {
    return null;
  }
  return `${trimTrailingSlash(apiBaseUrl)}/events/${encodeURIComponent(megatixEventId)}/availability/refresh`;
}

export function formatDate(value) {
  if (!value) {
    return "";
  }
  try {
    return new Intl.DateTimeFormat("en", {
      dateStyle: "medium",
      timeStyle: "short"
    }).format(new Date(value));
  } catch {
    return value;
  }
}

export function formatDateRange(startAt, endAt, timezone) {
  if (!startAt) {
    return timezone || "Schedule pending";
  }
  const start = formatDate(startAt);
  if (!endAt) {
    return timezone ? `${start} · ${timezone}` : start;
  }
  const end = formatDate(endAt);
  return timezone ? `${start} - ${end} · ${timezone}` : `${start} - ${end}`;
}

export function formatMoney(value, currencyCode) {
  if (typeof value !== "number" || !currencyCode) {
    return currencyCode ? `${value} ${currencyCode}` : "Price pending";
  }
  try {
    return new Intl.NumberFormat("en", {
      style: "currency",
      currency: currencyCode,
      maximumFractionDigits: 2
    }).format(value);
  } catch {
    return `${value} ${currencyCode}`;
  }
}

export function toAvailabilityLabel(state) {
  switch (state) {
    case "available":
      return "Available";
    case "low":
      return "Low availability";
    case "sold_out":
      return "Sold out";
    default:
      return "Availability pending";
  }
}

export function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
}

export function escapeAttr(value) {
  return escapeHtml(value);
}

function trimTrailingSlash(value) {
  return value.endsWith("/") ? value.slice(0, -1) : value;
}
