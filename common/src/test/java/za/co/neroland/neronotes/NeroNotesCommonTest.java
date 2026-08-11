package za.co.neroland.neronotes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.neronotes.network.NotesNetwork;

/**
 * Plain-JVM smoke tests for the Stage 0 foundation. The shared common tests
 * run on the NeoForge nodes only ({@code :neoforge:<mc>:test}) — see
 * neoforge/build.gradle for the source-set wiring.
 */
class NeroNotesCommonTest {

    @Test
    void modIdIsStable() {
        assertEquals("neronotes", NeroNotesCommon.MOD_ID);
    }

    @Test
    void networkChannelIsOwnNamespace() {
        // Payloads live on NeroNotes' own channel, never Core's CoreNetwork.
        assertEquals("neronotes:main", NotesNetwork.CHANNEL_ID);
    }
}
