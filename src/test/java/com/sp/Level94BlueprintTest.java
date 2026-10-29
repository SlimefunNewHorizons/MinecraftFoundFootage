package com.sp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sp.world.generation.chunk_generator.Level94Blueprint;
import com.sp.world.generation.chunk_generator.Level94Blueprint.Piece;
import com.sp.world.generation.chunk_generator.Level94Layout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the blocks Level 94 is built from, without starting Minecraft.
 */
class Level94BlueprintTest {

    /**
     * Collects the blocks written into an area.
     */
    private static final class AreaSink implements Level94Blueprint.Sink {
        final int minX, minZ, maxX, maxZ;
        final Map<String, Piece> blocks = new HashMap<>();
        final Set<Piece> pieces = new HashSet<>();

        AreaSink(int minX, int minZ, int maxX, int maxZ) {
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }

        @Override
        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        @Override
        public void set(int x, int y, int z, Piece piece) {
            if (!contains(x, z)) return;
            pieces.add(piece);
            blocks.put(x + "," + y + "," + z, piece);
        }
    }

    @Test
    @DisplayName("Every block and block state property used by Level 94 exists")
    void everyPieceExists() throws Exception {
        AreaSink sink = new AreaSink(-3000, -400, 3000, 400);
        for (int x = -3000; x < 3000; x += 400) {
            Level94Blueprint.placeFeatures(sink, x, -400, x + 399, 400);
        }
        for (int x = -60; x < 60; x++) {
            for (int z = -60; z < 60; z++) {
                sink.pieces.add(Level94Blueprint.surface(x, z));
            }
        }
        sink.pieces.add(Level94Blueprint.STONE);
        sink.pieces.add(Level94Blueprint.DIRT);
        assertTrue(sink.pieces.size() > 30, "Suspiciously few different blocks: " + sink.pieces.size());

        for (Piece piece : sink.pieces) {
            if (piece.id().equals("minecraft:air")) continue;
            String[] id = piece.id().split(":");
            String path = "assets/" + id[0] + "/blockstates/" + id[1] + ".json";
            String blockstate;
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, "Unknown block (no blockstate file): " + piece.id());
                blockstate = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            for (String pair : piece.properties().split(",")) {
                if (pair.isEmpty()) continue;
                String key = pair.split("=")[0];
                // Leaves keep "persistent" out of their blockstate file
                if (key.equals("persistent")) continue;
                assertTrue(blockstate.contains("\"" + key + "\"") || blockstate.contains(key + "="),
                        piece.id() + " has no property " + key);
            }
        }
    }

    @Test
    @DisplayName("Building chunk by chunk gives exactly the same blocks as building the whole area at once")
    void chunksJoinSeamlessly() {
        // An area with houses, crossings, a water tower and the main road, 4x4 chunks
        int minX = 96, minZ = -64;
        int maxX = minX + 63, maxZ = minZ + 63;

        AreaSink whole = new AreaSink(minX, minZ, maxX, maxZ);
        Level94Blueprint.placeFeatures(whole, minX, minZ, maxX, maxZ);

        AreaSink chunked = new AreaSink(minX, minZ, maxX, maxZ);
        for (int cx = minX; cx <= maxX; cx += 16) {
            for (int cz = minZ; cz <= maxZ; cz += 16) {
                AreaSink chunk = new AreaSink(cx, cz, cx + 15, cz + 15);
                Level94Blueprint.placeFeatures(chunk, cx, cz, cx + 15, cz + 15);
                chunked.blocks.putAll(chunk.blocks);
            }
        }

        assertFalse(whole.blocks.isEmpty(), "Nothing was built in the test area");
        assertEquals(whole.blocks.size(), chunked.blocks.size(), "Chunk by chunk built a different number of blocks");
        for (Map.Entry<String, Piece> entry : whole.blocks.entrySet()) {
            assertEquals(entry.getValue(), chunked.blocks.get(entry.getKey()), "Different block at " + entry.getKey());
        }
    }

    @Test
    @DisplayName("Ground columns: road on roads, curb beside them, grass elsewhere, solid all the way down")
    void groundColumns() {
        for (int x = -40; x <= 40; x += 3) {
            for (int z = -40; z <= 40; z += 3) {
                AreaSink sink = new AreaSink(x, z, x, z);
                Level94Blueprint.fillColumn(sink, x, z, 0);
                int height = Level94Layout.getTerrainHeight(x, z);

                Piece top = sink.blocks.get(x + "," + height + "," + z);
                double road = Level94Layout.nearestRoadDistance(x, z);
                Piece expected = road <= Level94Layout.ROAD_HALF_WIDTH ? Level94Blueprint.ROAD
                        : road <= Level94Layout.CURB_EDGE ? Level94Blueprint.CURB : Level94Blueprint.GRASS;
                assertEquals(expected, top, "Wrong surface at " + x + ", " + z);
                assertEquals(height + 1, sink.blocks.size(), "Holes in the column at " + x + ", " + z);
            }
        }
    }

    @Test
    @DisplayName("Lang file of the level's phrases is valid JSON")
    void phrasesAreTranslated() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("assets/spb-revamped/lang/en_us.json")) {
            assertNotNull(stream);
            JsonObject lang = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(lang.has("entity.spb-revamped.neighbor"));
        }
    }
}
