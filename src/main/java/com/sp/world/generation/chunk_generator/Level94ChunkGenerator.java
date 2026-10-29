package com.sp.world.generation.chunk_generator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sp.world.generation.chunk_generator.Level94Blueprint.Piece;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Level 94 - "Motion".
 * <p>
 * An endless countryside of rolling hills crossed by a winding road with side streets. Every so often the road
 * passes a STOP sign, an intersection, a lonely house or a water tower on the hills.
 * <p>
 * {@link Level94Layout} decides where everything is and {@link Level94Blueprint} which blocks it is made of; this
 * class writes those blocks into chunks. Every chunk only writes the blocks that fall inside itself, so features
 * spanning several chunks come out the same regardless of the chunk generation order.
 */
public class Level94ChunkGenerator extends BackroomsChunkGenerator {

    public static final Codec<Level94ChunkGenerator> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
                            ChunkGeneratorSettings.REGISTRY_CODEC.fieldOf("settings").forGetter(generator -> generator.settings)
                    )
                    .apply(instance, instance.stable(Level94ChunkGenerator::new))
    );

    private static final Map<Piece, BlockState> STATES = new ConcurrentHashMap<>();

    private final RegistryEntry<ChunkGeneratorSettings> settings;

    public Level94ChunkGenerator(BiomeSource biomeSource, RegistryEntry<ChunkGeneratorSettings> settings) {
        super(biomeSource, 1);
        this.settings = settings;
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Executor executor, Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
        ChunkSink sink = new ChunkSink(chunk);

        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                Level94Blueprint.fillColumn(sink, sink.minX + i, sink.minZ + j, 0);
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void generate(StructureWorldAccess world, Chunk chunk) {
        ChunkSink sink = new ChunkSink(chunk);
        Level94Blueprint.placeFeatures(sink, sink.minX, sink.minZ, sink.minX + 15, sink.minZ + 15);
    }

    /**
     * The block state for a blueprint piece. Unknown ids or properties are a programming error and fail loudly.
     */
    private static BlockState resolve(Piece piece) {
        return STATES.computeIfAbsent(piece, p -> {
            Identifier id = new Identifier(p.id());
            if (!Registries.BLOCK.containsId(id)) {
                throw new IllegalStateException("Level 94 blueprint uses an unknown block: " + id);
            }

            Block block = Registries.BLOCK.get(id);
            BlockState state = block.getDefaultState();
            if (p.properties().isEmpty()) {
                return state;
            }

            for (String pair : p.properties().split(",")) {
                String[] keyValue = pair.split("=");
                Property<?> property = block.getStateManager().getProperty(keyValue[0]);
                if (property == null) {
                    throw new IllegalStateException("Block " + id + " has no property " + keyValue[0]);
                }
                state = with(state, property, keyValue[1]);
            }
            return state;
        });
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        return property.parse(value)
                .map(parsed -> state.with(property, parsed))
                .orElseThrow(() -> new IllegalStateException("Invalid value " + value + " for property " + property.getName()));
    }

    /**
     * Writes blueprint pieces into a single chunk, silently dropping everything outside of it.
     */
    private static final class ChunkSink implements Level94Blueprint.Sink {
        private final Chunk chunk;
        private final int minX;
        private final int minZ;
        private final BlockPos.Mutable mutable = new BlockPos.Mutable();

        ChunkSink(Chunk chunk) {
            this.chunk = chunk;
            this.minX = chunk.getPos().getStartX();
            this.minZ = chunk.getPos().getStartZ();
        }

        @Override
        public boolean contains(int x, int z) {
            return x >= minX && x < minX + 16 && z >= minZ && z < minZ + 16;
        }

        @Override
        public void set(int x, int y, int z, Piece piece) {
            if (contains(x, z) && y >= 0 && y < 384) {
                chunk.setBlockState(mutable.set(x, y, z), resolve(piece), false);
            }
        }
    }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }
}
