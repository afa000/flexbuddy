package com.angel.flexbuddy.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/** Which address the per-connection limits see, whatever a visitor writes in the forwarding headers. */
class ClientAddressFilterTest {

    private static final String PEER = "10.0.0.5";

    private final List<String> lines = new ArrayList<>();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-12T12:00:00Z"));

    private String addressSeenBy(ClientAddressProperties properties, MockHttpServletRequest request) throws Exception {
        return runFilter(properties, request).getRemoteAddr();
    }

    private HttpServletRequest runFilter(ClientAddressProperties properties, MockHttpServletRequest request) throws Exception {
        return runFilter(new ClientAddressFilter(properties, clock, lines::add), request);
    }

    private static HttpServletRequest runFilter(ClientAddressFilter filter, MockHttpServletRequest request) throws Exception {
        AtomicReference<HttpServletRequest> passedOn = new AtomicReference<>();
        // Spring's forwarded-header filter wraps the request before this filter sees it; an inner wrapper hides the header.
        HttpServletRequest wrapped = new HiddenForwardedFor(request);
        filter.doFilter(wrapped, new MockHttpServletResponse(), (req, res) -> passedOn.set((HttpServletRequest) req));
        return passedOn.get();
    }

    private static ClientAddressProperties defaults() {
        return new ClientAddressProperties("", 1);
    }

    private static MockHttpServletRequest request(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(PEER);
        if (forwardedFor != null) request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }

    @Test
    void theRightMostEntryIsUsedSoAWrittenLeftEntryIsIgnored() throws Exception {
        assertThat(addressSeenBy(defaults(), request("203.0.113.9, 198.51.100.7"))).isEqualTo("198.51.100.7");
    }

    @Test
    void oneEntryIsTheVisitor() throws Exception {
        assertThat(addressSeenBy(defaults(), request("198.51.100.7"))).isEqualTo("198.51.100.7");
    }

    @Test
    void withoutTheHeaderThePeerIsUsed() throws Exception {
        assertThat(addressSeenBy(defaults(), request(null))).isEqualTo(PEER);
    }

    @Test
    void theRemoteHostFollowsTheAddress() throws Exception {
        assertThat(runFilter(defaults(), request("203.0.113.9, 198.51.100.7")).getRemoteHost()).isEqualTo("198.51.100.7");
    }

    @Test
    void moreTrustedHopsSkipEntriesFromTheRight() throws Exception {
        MockHttpServletRequest request = request("192.0.2.1, 192.0.2.2, 192.0.2.3");

        assertThat(addressSeenBy(new ClientAddressProperties("", 2), request)).isEqualTo("192.0.2.2");
        assertThat(addressSeenBy(new ClientAddressProperties("", 3), request)).isEqualTo("192.0.2.1");
    }

    @Test
    void fewerEntriesThanTrustedHopsFallBackToThePeer() throws Exception {
        assertThat(addressSeenBy(new ClientAddressProperties("", 3), request("192.0.2.1, 192.0.2.2"))).isEqualTo(PEER);
    }

    @Test
    void aNamedHeaderIsUsedAndTheForwardedForIsIgnored() throws Exception {
        MockHttpServletRequest request = request("203.0.113.9");
        request.addHeader("CF-Connecting-IP", "198.51.100.7");

        assertThat(addressSeenBy(new ClientAddressProperties("CF-Connecting-IP", 1), request)).isEqualTo("198.51.100.7");
    }

    @Test
    void aMissingNamedHeaderFallsBackToTheForwardedRule() throws Exception {
        assertThat(addressSeenBy(new ClientAddressProperties("CF-Connecting-IP", 1), request("203.0.113.9, 198.51.100.7")))
                .isEqualTo("198.51.100.7");
    }

    @Test
    void aNamedHeaderThatIsNotAnAddressFallsBackToo() throws Exception {
        MockHttpServletRequest request = request("203.0.113.9, 198.51.100.7");
        request.addHeader("CF-Connecting-IP", "not an address");

        assertThat(addressSeenBy(new ClientAddressProperties("CF-Connecting-IP", 1), request)).isEqualTo("198.51.100.7");
    }

    @Test
    void valuesThatAreNotAddressesFallBackToThePeer() throws Exception {
        for (String value : new String[] {"203.0.113.9, unknown", "203.0.113.9, evil.example", "203.0.113.9, 1.2.3",
                "203.0.113.9, 1.2.3.999", "203.0.113.9, 198.51.100. 7", "", " , ", "203.0.113.9, ::g"}) {
            assertThat(addressSeenBy(defaults(), request(value))).as(value).isEqualTo(PEER);
        }
    }

    @Test
    void portsAndBracketsAreStripped() throws Exception {
        assertThat(addressSeenBy(defaults(), request("198.51.100.7:4433"))).isEqualTo("198.51.100.7");
        assertThat(addressSeenBy(defaults(), request("[2001:db8::1]:443"))).isEqualTo("2001:db8::1");
        assertThat(addressSeenBy(defaults(), request("2001:db8::1"))).isEqualTo("2001:db8::1");
    }

    @Test
    void severalForwardedForLinesCountTogetherInOrder() throws Exception {
        MockHttpServletRequest request = request("203.0.113.9");
        request.addHeader("X-Forwarded-For", "198.51.100.7");

        assertThat(addressSeenBy(defaults(), request)).isEqualTo("198.51.100.7");
    }

    @Test
    void theOriginalHeadersAreReadThroughTheWrapper() throws Exception {
        MockHttpServletRequest request = request("203.0.113.9, 198.51.100.7");
        HttpServletRequest hidden = new HiddenForwardedFor(request);
        assertThat(hidden.getHeader("X-Forwarded-For")).isNull();

        assertThat(addressSeenBy(defaults(), request)).isEqualTo("198.51.100.7");
    }

    @Test
    void theDiagnosticLineCarriesNoAddressAndIsWrittenOncePerHour() throws Exception {
        MockHttpServletRequest request = request("203.0.113.9, 198.51.100.7");
        request.addHeader("CF-Connecting-IP", "203.0.113.9");
        ClientAddressFilter filter = new ClientAddressFilter(defaults(), clock, lines::add);

        runFilter(filter, request);
        runFilter(filter, request);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("entries=2").contains("cf-connecting-ip=present (equals entry 1 of 2)")
                .contains("true-client-ip=absent").contains("peer private=true").contains("using entry 2 of 2");
        assertThat(lines.get(0)).doesNotContainPattern("\\d+\\.\\d+\\.\\d+\\.\\d+").doesNotContainPattern("[0-9a-fA-F]*:[0-9a-fA-F]+:");

        clock.advance(Duration.ofMinutes(59));
        runFilter(filter, request);
        assertThat(lines).hasSize(1);

        clock.advance(Duration.ofMinutes(1));
        runFilter(filter, request);
        assertThat(lines).hasSize(2);
    }

    @Test
    void requestsWithoutForwardingHeadersDoNotUseUpTheHoursLine() throws Exception {
        ClientAddressFilter filter = new ClientAddressFilter(defaults(), clock, lines::add);

        runFilter(filter, request(null));
        assertThat(lines).isEmpty();

        runFilter(filter, request("198.51.100.7"));
        assertThat(lines).hasSize(1).first().asString().contains("entries=1").contains("using entry 1 of 1");
    }

    /** What Spring's forwarded-header filter does to a request: later readers no longer see X-Forwarded-For. */
    private static final class HiddenForwardedFor extends HttpServletRequestWrapper {

        HiddenForwardedFor(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            return "X-Forwarded-For".equalsIgnoreCase(name) ? null : super.getHeader(name);
        }

        @Override
        public java.util.Enumeration<String> getHeaders(String name) {
            return "X-Forwarded-For".equalsIgnoreCase(name) ? java.util.Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public String getRemoteAddr() {
            return "203.0.113.200";
        }
    }
}
