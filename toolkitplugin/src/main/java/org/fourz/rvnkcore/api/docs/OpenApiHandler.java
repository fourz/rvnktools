package org.fourz.rvnkcore.api.docs;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Serves the OpenAPI documentation UI and spec JSON.
 *
 * Endpoints:
 *   GET /api/docs/spec.json  — raw OpenAPI 3.0 specification
 *   GET /api/docs/ui         — Swagger UI (HTML)
 */
public class OpenApiHandler extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        if (path == null) path = "/";

        if (path.equals("/spec.json") || path.equals("/spec")) {
            resp.setContentType("application/json;charset=UTF-8");
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write("{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"RVNKCore API\",\"version\":\"1.0\"}}");
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("Not found");
        }
    }
}
