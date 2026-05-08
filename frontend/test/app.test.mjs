import test from "node:test";
import assert from "node:assert/strict";

import { getPublicApiBaseUrl, resolveApiRequest } from "../src/app.js";

test("resolveApiRequest prefers slug routes", () => {
  assert.equal(
    resolveApiRequest("/events/summer-night", "/api/public/"),
    "/api/public/events/by-slug/summer-night"
  );
});

test("resolveApiRequest supports id fallback routes", () => {
  assert.equal(
    resolveApiRequest("/events/id/evt-ota-123", "/api/public"),
    "/api/public/events/evt-ota-123"
  );
});

test("resolveApiRequest rejects unrelated paths", () => {
  assert.equal(resolveApiRequest("/admin/events", "/api/public"), null);
});

test("getPublicApiBaseUrl uses runtime config and trims trailing slash", () => {
  assert.equal(
    getPublicApiBaseUrl({ PUBLIC_API_BASE_URL: "/api/public/" }),
    "/api/public"
  );
});
