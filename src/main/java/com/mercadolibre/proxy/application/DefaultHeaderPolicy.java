package com.mercadolibre.proxy.application;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class DefaultHeaderPolicy implements HeaderPolicy {

    private static final String RXI = "X-Request-Id";

    @Override
    public HttpHeaders toBackend(HttpHeaders inbound, String requestId) {
        HttpHeaders out = new HttpHeaders();
        copyIfPresent(inbound, out, HttpHeaders.ACCEPT);
        copyIfPresent(inbound, out, HttpHeaders.ACCEPT_LANGUAGE);
        copyIfPresent(inbound, out, HttpHeaders.ACCEPT_CHARSET);
        copyIfPresent(inbound, out, HttpHeaders.CONTENT_TYPE);
        copyIfPresent(inbound, out, HttpHeaders.AUTHORIZATION);
        copyIfPresent(inbound, out, "User-Agent");
        if (!out.containsKey(HttpHeaders.ACCEPT)) out.set(HttpHeaders.ACCEPT, "application/json");
        out.set(RXI, requestId);
        return out;
    }

    @Override
    public HttpHeaders toClient(HttpHeaders backend, String requestId) {
        HttpHeaders out = new HttpHeaders();
        backend.forEach(out::addAll);
        removeHopByHop(out);
        out.setCacheControl("no-store, no-cache, must-revalidate");
        out.setPragma("no-cache");
        out.setExpires(0);
        out.set("X-Content-Type-Options", "nosniff");
        out.set("X-Frame-Options", "DENY");
        out.set("X-XSS-Protection", "1; mode=block");
        out.set(RXI, requestId);
        return out;
    }

    private void copyIfPresent(HttpHeaders src, HttpHeaders dst, String name) {
        var vals = src.get(name);
        if (vals != null && !vals.isEmpty()) dst.addAll(name, vals);
    }

    private void removeHopByHop(HttpHeaders headers) {
        headers.remove(HttpHeaders.CONNECTION);
        headers.remove(HttpHeaders.TRANSFER_ENCODING);
        headers.remove("Keep-Alive");
        headers.remove("Proxy-Authenticate");
        headers.remove("Proxy-Authorization");
        headers.remove("TE");
        headers.remove("Trailer");
        headers.remove("Upgrade");
    }
}
