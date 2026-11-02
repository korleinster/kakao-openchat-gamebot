package com.korleinster.kakaobot
import org.junit.Assert.*
import org.junit.Test

class SyncEndpointTest {
    @Test fun acceptsUserConfiguredHttpsWithoutAnyDefaultHost() {
        assertEquals("https://sync.example.org/api/chatbot",SyncEndpoint.normalize(" https://sync.example.org/ "))
        assertEquals("https://sync.example.org:8443/custom",SyncEndpoint.normalize("https://sync.example.org:8443/custom/"))
    }
    @Test fun rejectsCleartextCredentialsQueriesAndAmbiguousPaths() {
        listOf("http://sync.example.org","https://user:pass@sync.example.org","https://sync.example.org/?secret=x","https://sync.example.org/#x","https://sync.example.org/../x","https://sync.example.org/%2e%2e/x","").forEach {
            try {SyncEndpoint.normalize(it);fail("Unsafe endpoint accepted")}catch(_:IllegalArgumentException){}catch(_:java.net.URISyntaxException){}
        }
    }
}
