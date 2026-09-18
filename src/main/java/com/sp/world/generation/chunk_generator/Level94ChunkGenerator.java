package com.sp.world.generation.chunk_generator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sp.init.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.noise.PerlinNoiseSampler;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class Level94ChunkGenerator extends BackroomsChunkGenerator {

    public static final Codec<Level94ChunkGenerator> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
                            ChunkGeneratorSettings.REGISTRY_CODEC.fieldOf("settings").forGetter(generator -> generator.settings)
                    )
                    .apply(instance, instance.stable(Level94ChunkGenerator::new))
    );

    // The road runs along the X axis at z=0. The 3 special STOP signs lead to other levels.
    public static final int STOP_LEVEL1_X = 256;
    public static final int STOP_POOLROOMS_X = 512;
    public static final int STOP_LEVEL324_X = 1024;
    public static final int STOP_SIGN_Z = 7;

    // The floating castle hovers over the origin.
    public static final int CASTLE_Y = 124;
    public static final int CASTLE_RADIUS = 16;

    // Water towers scattered far along the road.
    public static final int WATER_TOWER_X1 = 1400;
    public static final int WATER_TOWER_X2 = -1400;

    // Terrain constants
    private static final int BASE_Y = 31;             // Road surface level
    private static final int ROAD_HALF_WIDTH = 4;      // |wz| <= 4 is road
    private static final int SIDEWALK_Z = 5;            // |wz| == 5 is sidewalk
    private static final int TRANSITION_START = 6;      // Hills start transitioning
    private static final int TRANSITION_END = 14;       // Hills reach full height
    private static final int MAX_HILL_HEIGHT = 22;      // Maximum hill elevation above BASE_Y

    private final RegistryEntry<ChunkGeneratorSettings> settings;

    public Level94ChunkGenerator(BiomeSource biomeSource, RegistryEntry<ChunkGeneratorSettings> settings) {
        super(biomeSource, 2);
        this.settings = settings;
    }

    /**
     * Computes the terrain height at world coordinates (wx, wz).
     * <p>
     * The road is flat at BASE_Y. Beyond the road, hills rise using a smooth
     * cubic transition and multi-octave harmonic noise for natural-looking
     * mountains.
     */
    public static int getTerrainHeight(int wx, int wz) {
        int absZ = Math.abs(wz);

        // Road and sidewalk are flat
        if (absZ <= SIDEWALK_Z) {
            return BASE_Y;
        }

        // Smooth cubic transition from road edge into hills
        double t;
        if (absZ < TRANSITION_END) {
            double raw = (double)(absZ - TRANSITION_START) / (TRANSITION_END - TRANSITION_START);
            raw = Math.max(0.0, Math.min(1.0, raw));
            t = raw * raw * (3.0 - 2.0 * raw);  // smoothstep
        } else {
            t = 1.0;
        }

        // Multi-octave harmonic noise for natural hill shapes
        double nx = wx * 0.008;
        double nz = wz * 0.008;
        double hill = 0.0;
        hill += Math.sin(nx * 1.0 + 0.3) * Math.cos(nz * 0.7 + 0.5) * 1.0;
        hill += Math.sin(nx * 2.3 + 1.2) * Math.cos(nz * 1.8 + 0.8) * 0.5;
        hill += Math.sin(nx * 4.7 + 2.1) * Math.cos(nz * 3.9 + 1.4) * 0.25;
        // Normalize from [-1.75, 1.75] to [0, 1]
        hill = (hill + 1.75) / 3.5;

        // Further shape: hills get taller the further from the road
        double distanceFactor = Math.min(1.0, (absZ - TRANSITION_START) / 60.0);
        double elevation = hill * MAX_HILL_HEIGHT * (0.4 + 0.6 * distanceFactor);

        return BASE_Y + (int)(t * elevation);
    }

    @Override
    public void generate(StructureWorldAccess world, Chunk chunk) {
        int x = chunk.getPos().getStartX();
        int z = chunk.getPos().getStartZ();

        Random random = Random.create(this.getChunkSeed(world, chunk));
        PerlinNoiseSampler noiseSampler = new PerlinNoiseSampler(random);
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        // Decide once per chunk if one ghost house hugs the road.
        double houseNoise = noiseSampler.sample(x * 0.015, 0, z * 0.015);
        boolean hasHouse = houseNoise > 0.55 && houseNoise < 0.72 && random.nextInt(100) < 7;
        int houseAnchorI = random.nextBetween(1, 8);
        boolean placedHouse = false;

        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int wx = x + i;
                int wz = z + j;

                int roadSide = ((wx / 64) & 1) == 0 ? STOP_SIGN_Z : -STOP_SIGN_Z;

                // Regular STOP posts along the road (including an early iconic one near spawn at x=16).
                if (wz == roadSide && ((wx > 0 && wx % 64 == 0) || (wx == 16 && wz == STOP_SIGN_Z))
                        && wx != STOP_LEVEL1_X && wx != STOP_POOLROOMS_X && wx != STOP_LEVEL324_X) {
                    placeStopPost(chunk, mutable, i, j);
                }

                // The 3 special STOP signs (the exit mechanics).
                if ((wx == STOP_LEVEL1_X && wz == STOP_SIGN_Z)
                        || (wx == STOP_POOLROOMS_X && wz == -STOP_SIGN_Z)
                        || (wx == STOP_LEVEL324_X && wz == STOP_SIGN_Z)) {
                    placeStopPost(chunk, mutable, i, j);
                    if (wx == STOP_POOLROOMS_X) {
                        chunk.setBlockState(mutable.set(i, BASE_Y + 4, j), ModBlocks.WALL_TEXT_99.getDefaultState(), false);
                    } else {
                        chunk.setBlockState(mutable.set(i, BASE_Y + 4, j), ModBlocks.RED_METAL_CASING.getDefaultState(), false);
                    }
                }

                // Ghost houses near the road on elevated terrain
                if (hasHouse && !placedHouse) {
                    if (z == 0 && wz >= 8 && wz <= 14) {
                        int terrainH = getTerrainHeight(wx, wz);
                        this.placeGhostHouse(chunk, mutable, random, false, houseAnchorI, terrainH);
                        placedHouse = true;
                    } else if (z == -16 && wz >= -14 && wz <= -8) {
                        int terrainH = getTerrainHeight(wx, wz);
                        this.placeGhostHouse(chunk, mutable, random, true, houseAnchorI, terrainH);
                        placedHouse = true;
                    }
                }
            }
        }

        // White picket fences along the road margins - adapted to terrain height
        if ((z == 0 || z == -16) && random.nextInt(100) < 18) {
            int fenceJ = z == 0 ? 6 : 10;
            int fenceWz = z + fenceJ;
            int startI = random.nextBetween(2, 6);
            int len = random.nextBetween(4, 7);
            for (int fi = startI; fi < startI + len && fi < 16; fi++) {
                int localFenceH = getTerrainHeight(x + fi, fenceWz);
                chunk.setBlockState(mutable.set(fi, localFenceH + 1, fenceJ), Blocks.BIRCH_FENCE.getDefaultState(), false);
            }
        }

        // Solitary oak trees on the grassy hills away from the road
        if (Math.abs(z) >= 32 && random.nextInt(100) < 4) {
            int treeI = random.nextBetween(3, 12);
            int treeJ = random.nextBetween(3, 12);
            int treeWz = z + treeJ;
            int treeH = getTerrainHeight(x + treeI, treeWz);
            placeSolitaryTree(chunk, mutable, treeI, treeJ, treeH);
        }

        this.placeCastle(chunk, mutable, x, z);

        placeWaterTower(chunk, mutable, x, z, WATER_TOWER_X1, 19);
        placeWaterTower(chunk, mutable, x, z, WATER_TOWER_X2, -19);
    }

    /**
     * Places a STOP sign post using the custom SignPoleBlock and StopSignBlock.
     * The pole is 2 blocks tall (cylindrical post), with a circular red STOP sign on top.
     */
    private void placeStopPost(Chunk chunk, BlockPos.Mutable mutable, int i, int j) {
        int wz = chunk.getPos().getStartZ() + j;
        int wx = chunk.getPos().getStartX() + i;
        int surfaceY = getTerrainHeight(wx, wz);

        // Pole: 2 blocks of SignPoleBlock
        chunk.setBlockState(mutable.set(i, surfaceY + 1, j), ModBlocks.SIGN_POLE.getDefaultState(), false);
        chunk.setBlockState(mutable.set(i, surfaceY + 2, j), ModBlocks.SIGN_POLE.getDefaultState(), false);
        // Head: circular red STOP sign
        chunk.setBlockState(mutable.set(i, surfaceY + 3, j), ModBlocks.STOP_SIGN.getDefaultState(), false);
    }

    private void placeSolitaryTree(Chunk chunk, BlockPos.Mutable mutable, int ti, int tj, int surfaceY) {
        // 4-block trunk
        for (int y = surfaceY + 1; y <= surfaceY + 4; y++) {
            chunk.setBlockState(mutable.set(ti, y, tj), Blocks.OAK_LOG.getDefaultState(), false);
        }
        // Rounded foliage canopy
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (ti + dx < 0 || ti + dx > 15 || tj + dz < 0 || tj + dz > 15) continue;
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                chunk.setBlockState(mutable.set(ti + dx, surfaceY + 4, tj + dz), Blocks.OAK_LEAVES.getDefaultState(), false);
                chunk.setBlockState(mutable.set(ti + dx, surfaceY + 5, tj + dz), Blocks.OAK_LEAVES.getDefaultState(), false);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (ti + dx < 0 || ti + dx > 15 || tj + dz < 0 || tj + dz > 15) continue;
                chunk.setBlockState(mutable.set(ti + dx, surfaceY + 6, tj + dz), Blocks.OAK_LEAVES.getDefaultState(), false);
            }
        }
        chunk.setBlockState(mutable.set(ti, surfaceY + 7, tj), Blocks.OAK_LEAVES.getDefaultState(), false);
    }

    private void placeCastle(Chunk chunk, BlockPos.Mutable mutable, int chunkX, int chunkZ) {
        if (chunkX > 0 || chunkZ > 0 || chunkX < -CASTLE_RADIUS || chunkZ < -CASTLE_RADIUS) {
            return;
        }

        BlockState rim = ModBlocks.WALL_BLOCK.getDefaultState();
        BlockState tower = ModBlocks.WALL_BLOCK.getDefaultState();
        BlockState glass = Blocks.GLASS.getDefaultState();

        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int wx = chunkX + i;
                int wz = chunkZ + j;
                double distanceSq = wx * wx + wz * wz;

                if (distanceSq <= CASTLE_RADIUS * CASTLE_RADIUS) {
                    int localI = wx - chunkX;
                    int localJ = wz - chunkZ;
                    boolean isRim = distanceSq > (CASTLE_RADIUS - 3) * (CASTLE_RADIUS - 3);

                    // Castle platform
                    chunk.setBlockState(mutable.set(localI, CASTLE_Y, localJ), ModBlocks.CONCRETE_BLOCK_9.getDefaultState(), false);
                    chunk.setBlockState(mutable.set(localI, CASTLE_Y + 1, localJ), isRim ? rim : Blocks.AIR.getDefaultState(), false);

                    // Four ruin towers on the cardinal rim for the silhouette.
                    boolean towerSpot = (wx == CASTLE_RADIUS - 1 && wz == 0)
                            || (wx == 0 && wz == CASTLE_RADIUS - 1)
                            || (wx == -(CASTLE_RADIUS - 1) && wz == 0)
                            || (wx == 0 && wz == -(CASTLE_RADIUS - 1));
                    if (towerSpot) {
                        for (int y = CASTLE_Y + 2; y < CASTLE_Y + 9; y++) {
                            chunk.setBlockState(mutable.set(localI, y, localJ),
                                    y == CASTLE_Y + 8 ? ModBlocks.LAMP.getDefaultState() : tower, false);
                        }
                    }

                    // Transparent Mountain (Montaña Transparente): glass structure ascending from ground to castle
                    if (distanceSq <= 6 * 6) {
                        int localTerrainH = getTerrainHeight(wx, wz);
                        for (int y = localTerrainH + 1; y < CASTLE_Y; y++) {
                            chunk.setBlockState(mutable.set(localI, y, localJ), glass, false);
                        }
                    }

                    // Central ascension column with ladder to allow climbing up to the castle
                    if (wx == 0 && wz == 0) {
                        for (int y = BASE_Y; y <= CASTLE_Y; y++) {
                            chunk.setBlockState(mutable.set(localI, y, localJ), ModBlocks.CONCRETE_BLOCK_9.getDefaultState(), false);
                        }
                    } else if (wx == 0 && wz == 1) {
                        for (int y = BASE_Y; y <= CASTLE_Y; y++) {
                            chunk.setBlockState(mutable.set(localI, y, localJ), Blocks.LADDER.getDefaultState(), false);
                        }
                    }
                }
            }
        }
    }

    private void placeWaterTower(Chunk chunk, BlockPos.Mutable mutable, int chunkX, int chunkZ, int signX, int signZ) {
        int localI = signX - chunkX;
        int localJ = signZ - chunkZ;
        if (localI < 0 || localI > 15 || localJ < 0 || localJ > 15) {
            return;
        }

        int surfaceY = getTerrainHeight(signX, signZ);

        BlockState leg = ModBlocks.CONCRETE_BLOCK_1.getDefaultState();
        BlockState tank = ModBlocks.PLASTIC.getDefaultState();

        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                boolean corner = dx == 0 || dx == 3;
                if (corner) {
                    chunk.setBlockState(mutable.set(localI + dx, surfaceY + 1, localJ + dz), leg, false);
                    chunk.setBlockState(mutable.set(localI + dx, surfaceY + 2, localJ + dz), leg, false);
                    chunk.setBlockState(mutable.set(localI + dx, surfaceY + 3, localJ + dz), leg, false);
                }
                for (int y = surfaceY + 4; y <= surfaceY + 7; y++) {
                    chunk.setBlockState(mutable.set(localI + dx, y, localJ + dz), tank, false);
                }
            }
        }
    }

    /**
     * Places a flat, mostly-empty "ghost house" facade next to the road.
     * Sometimes only a wall with furniture, sometimes a room corner.
     * Adapted to sit on the terrain surface.
     *
     * @param south whether the house sits on the negative-z side (facade facing the road)
     */
    private void placeGhostHouse(Chunk chunk, BlockPos.Mutable mutable, Random random, boolean south, int anchorI, int surfaceY) {
        int facadeJ = south ? 8 : 14;
        int interiorMin = south ? 2 : 8;
        int variant = random.nextInt(3);

        BlockState wall = ModBlocks.WALL_BLOCK.getDefaultState();
        BlockState floor = ModBlocks.PLASTIC.getDefaultState();
        BlockState drawingDoor = ModBlocks.WALL_DRAWING_DOOR.getDefaultState();
        BlockState drawingWindow = ModBlocks.WALL_DRAWING_WINDOW.getDefaultState();
        BlockState crate = ModBlocks.WOODEN_CRATE.getDefaultState();

        for (int i = anchorI; i <= anchorI + 4; i++) {
            int dy = i - anchorI;
            for (int j = interiorMin; j <= facadeJ; j++) {
                if (i == anchorI || i == anchorI + 4) {
                    // side walls
                    for (int y = surfaceY + 1; y <= surfaceY + 4; y++) {
                        chunk.setBlockState(mutable.set(i, y, j), wall, false);
                    }
                } else if (j == facadeJ) {
                    // facade facing the road
                    boolean door = variant == 0 && dy == 1;
                    boolean window = (variant == 0 && dy == 3) || (variant == 1 && dy == 2);
                    if (door) {
                        chunk.setBlockState(mutable.set(i, surfaceY + 1, j), drawingDoor, false);
                        chunk.setBlockState(mutable.set(i, surfaceY + 2, j), drawingDoor, false);
                    } else if (window) {
                        chunk.setBlockState(mutable.set(i, surfaceY + 1, j), drawingWindow, false);
                        chunk.setBlockState(mutable.set(i, surfaceY + 2, j), drawingWindow, false);
                    } else {
                        for (int k = surfaceY + 1; k <= surfaceY + 4; k++) {
                            chunk.setBlockState(mutable.set(i, k, j), wall, false);
                        }
                    }
                } else if (variant == 2 && j == (interiorMin + facadeJ) / 2) {
                    // half-room: an inner wall with a shelf
                    for (int y = surfaceY + 1; y <= surfaceY + 4; y++) {
                        chunk.setBlockState(mutable.set(i, y, j), wall, false);
                    }
                    chunk.setBlockState(mutable.set(i, surfaceY, j), ModBlocks.DOUBLE_SIDED_SHELF.getDefaultState(), false);
                } else {
                    chunk.setBlockState(mutable.set(i, surfaceY, j), floor, false);
                    if (variant == 1 && dy == 2 && j == interiorMin) {
                        chunk.setBlockState(mutable.set(i, surfaceY + 1, j), crate, false);
                    }
                }
            }
        }
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Executor executor, Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int wx = chunk.getPos().getStartX() + i;
                int wz = chunk.getPos().getStartZ() + j;

                int terrainHeight = getTerrainHeight(wx, wz);

                // Fill from y=0 to y=20 with stone
                for (int y = 0; y <= 20; y++) {
                    chunk.setBlockState(mutable.set(i, y, j), Blocks.STONE.getDefaultState(), false);
                }

                // Fill from y=21 to terrainHeight-1 with dirt
                for (int y = 21; y < terrainHeight; y++) {
                    chunk.setBlockState(mutable.set(i, y, j), ModBlocks.DIRT.getDefaultState(), false);
                }

                // Surface layer
                int absWz = Math.abs(wz);
                if (absWz <= ROAD_HALF_WIDTH) {
                    // Road surface
                    chunk.setBlockState(mutable.set(i, terrainHeight, j), ModBlocks.ROAD.getDefaultState(), false);
                } else if (absWz == SIDEWALK_Z) {
                    // Sidewalk / curb
                    chunk.setBlockState(mutable.set(i, terrainHeight, j), ModBlocks.CONCRETE_BLOCK_9.getDefaultState(), false);
                } else {
                    // Natural grass surface on hills and mountains
                    chunk.setBlockState(mutable.set(i, terrainHeight, j), Blocks.GRASS_BLOCK.getDefaultState(), false);
                }
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }
}