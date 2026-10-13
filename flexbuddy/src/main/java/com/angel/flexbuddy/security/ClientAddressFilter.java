package com.angel.flexbuddy.security;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Makes {@code getRemoteAddr()} the address Render's proxy saw, not one the visitor wrote. Spring's forwarded-header
 * filter takes the first (left-most) {@code X-Forwarded-For} entry, which a visitor can set to anything, so every limit
 * keyed on the connection could be dodged by sending a different made-up address each time. Render's proxy appends the
 * address it saw, so the right-most entry is the one to trust; a named header or more trusted hops can be set instead.
 * It is registered by {@code ClientAddressConfig}, inside the forwarded-header filter and before Spring Security.
 */
public class ClientAddressFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ClientAddressFilter.class);
    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final long LOG_EVERY_MILLIS = Duration.ofHours(1).toMillis();
    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3}(?:\\.\\d{1,3}){3})(?::\\d{1,5})?");
    private static final Pattern PORT = Pattern.compile(":\\d{1,5}");
    private static final Pattern IPV6_CHARACTERS = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    private final ClientAddressProperties properties;
    private final Clock clock;
    private final Consumer<String> diagnostics;
    private final AtomicLong lastLogged = new AtomicLong();

    public ClientAddressFilter(ClientAddressProperties properties, Clock clock) {
        this(properties, clock, log::info);
    }

    ClientAddressFilter(ClientAddressProperties properties, Clock clock, Consumer<String> diagnostics) {
        this.properties = properties;
        this.clock = clock;
        this.diagnostics = diagnostics;
    }

    /** The visitor's address, and which rule produced it (for the diagnostic line). */
    private record Resolved(String address, String source) {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpServletRequest raw = innermost(request);
        String peer = raw.getRemoteAddr();
        List<String> entries = entries(headerValue(raw, FORWARDED_FOR));
        Resolved resolved = resolve(properties.header(), properties.trustedHops(),
                headerValue(raw, properties.header()), entries, peer);
        if (dueForLog()) {
            diagnostics.accept(describe(raw, entries, resolved, peer));
        }
        chain.doFilter(new ResolvedAddressRequest(request, resolved.address()), response);
    }

    /** The request as the container delivered it, under whatever wrappers earlier filters added (they hide headers). */
    private static HttpServletRequest innermost(HttpServletRequest request) {
        ServletRequest current = request;
        while (current instanceof ServletRequestWrapper wrapper && wrapper.getRequest() != current) {
            current = wrapper.getRequest();
        }
        return (HttpServletRequest) current;
    }

    private static String headerValue(HttpServletRequest request, String name) {
        if (name == null || name.isBlank()) return null;
        Enumeration<String> values = request.getHeaders(name);
        if (values == null || !values.hasMoreElements()) return null;
        return String.join(",", Collections.list(values));
    }

    /** The comma-separated entries, trimmed, without the empty ones. */
    static List<String> entries(String forwardedFor) {
        List<String> entries = new ArrayList<>();
        if (forwardedFor == null) return entries;
        for (String part : forwardedFor.split(",")) {
            String entry = part.strip();
            if (!entry.isEmpty()) entries.add(entry);
        }
        return entries;
    }

    private static Resolved resolve(String header, int trustedHops, String headerValue, List<String> entries, String peer) {
        if (header != null && !header.isBlank() && headerValue != null) {
            String address = validAddress(headerValue);
            if (address != null) return new Resolved(address, "header " + header.toLowerCase(Locale.ROOT));
        }
        int index = entries.size() - Math.max(1, trustedHops);
        if (index >= 0) {
            String address = validAddress(entries.get(index));
            if (address != null) return new Resolved(address, "entry " + (index + 1) + " of " + entries.size());
        }
        return new Resolved(peer, "peer");
    }

    /** The bare IPv4 or IPv6 address in a header value (a port or brackets removed), or null if it is anything else. */
    static String validAddress(String raw) {
        if (raw == null) return null;
        String value = raw.strip();
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            if (close < 0) return null;
            String rest = value.substring(close + 1);
            if (!rest.isEmpty() && !PORT.matcher(rest).matches()) return null;
            String inner = value.substring(1, close);
            return isIpv6(inner) ? inner : null;
        }
        var ipv4 = IPV4.matcher(value);
        if (ipv4.matches()) {
            String address = ipv4.group(1);
            for (String octet : address.split("\\.")) {
                if (Integer.parseInt(octet) > 255) return null;
            }
            return address;
        }
        return isIpv6(value) ? value : null;
    }

    /** Checked by shape first, so only a literal is ever handed to InetAddress and no hostname is looked up. */
    private static boolean isIpv6(String value) {
        if (!IPV6_CHARACTERS.matcher(value).matches() || value.chars().filter(c -> c == ':').count() < 2) return false;
        try {
            return InetAddress.getByName(value) != null;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private boolean dueForLog() {
        long now = clock.millis();
        long last = lastLogged.get();
        return (last == 0 || now - last >= LOG_EVERY_MILLIS) && lastLogged.compareAndSet(last, now);
    }

    /** Which forwarding headers arrive and how many entries they have. It never contains an address. */
    private String describe(HttpServletRequest raw, List<String> entries, Resolved resolved, String peer) {
        return "client address: x-forwarded-for entries=" + entries.size()
                + "; cf-connecting-ip=" + presence(raw, "CF-Connecting-IP", entries)
                + "; true-client-ip=" + presence(raw, "True-Client-IP", entries)
                + "; x-real-ip=" + presence(raw, "X-Real-IP", entries)
                + "; peer private=" + isPrivate(peer)
                + "; using " + resolved.source();
    }

    private static String presence(HttpServletRequest raw, String name, List<String> entries) {
        String value = headerValue(raw, name);
        if (value == null) return "absent";
        String address = validAddress(value);
        for (int i = 0; address != null && i < entries.size(); i++) {
            if (address.equals(validAddress(entries.get(i)))) {
                return "present (equals entry " + (i + 1) + " of " + entries.size() + ")";
            }
        }
        return "present (matches no entry)";
    }

    private static boolean isPrivate(String peer) {
        String address = validAddress(peer);
        if (address == null) return false;
        try {
            InetAddress parsed = InetAddress.getByName(address);
            byte[] bytes = parsed.getAddress();
            boolean uniqueLocal = bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
            boolean carrierGrade = bytes.length == 4 && (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 0x40;
            return parsed.isSiteLocalAddress() || parsed.isLoopbackAddress() || uniqueLocal || carrierGrade;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /** The same request, answering getRemoteAddr and getRemoteHost with the visitor's address. */
    private static final class ResolvedAddressRequest extends HttpServletRequestWrapper {

        private final String address;

        ResolvedAddressRequest(HttpServletRequest request, String address) {
            super(request);
            this.address = address;
        }

        @Override
        public String getRemoteAddr() {
            return address;
        }

        @Override
        public String getRemoteHost() {
            return address;
        }
    }
}
