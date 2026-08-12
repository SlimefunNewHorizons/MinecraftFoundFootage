package com.sp.world.generation.chunk_generator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sp.SPBRevamped;
import com.sp.world.generation.maze_generator.Level1MazeGenerator;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.noise.PerlinNoiseSampler;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;

import java.util.Optional;

@SuppressWarnings("OptionalIsPresent")
public final class Level1ChunkGenerator extends BackroomsChunkGenerator {
    public static final Codec<Level1ChunkGenerator> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
                            ChunkGeneratorSettings.REGISTRY_CODEC.fieldOf("settings").forGetter(generator -> generator.settings)
                    )
                    .apply(instance, instance.stable(Level1ChunkGenerator::new))
    );
    private final RegistryEntry<ChunkGeneratorSettings> settings;
    Random random = Random.create();
    PerlinNoiseSampler noiseSampler = new PerlinNoiseSampler(random);

    public Level1ChunkGenerator(BiomeSource biomeSource, RegistryEntry<ChunkGeneratorSettings> settings) {
        super(biomeSource, 10);
        this.settings = settings;
    }

    public void generate(StructureWorldAccess world, Chunk chunk) {
        int x = chunk.getPos().getStartX();
        int z = chunk.getPos().getStartZ();

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        MinecraftServer server = world.getServer();

        StructureTemplateManager structureTemplateManager = world.getServer().getStructureTemplateManager();
        StructurePlacementData structurePlacementData = new StructurePlacementData();

        if (isStartChunk(chunk)) {
            Identifier roomIdentifier = new Identifier(SPBRevamped.MOD_ID, "level1/stairwell_1");
            structurePlacementData.setMirror(BlockMirror.NONE).setRotation(BlockRotation.NONE).setIgnoreEntities(true);
            Optional<StructureTemplate> stairwellStructureIn = structureTemplateManager.getTemplate(roomIdentifier);

            if (stairwellStructureIn.isPresent()) {
                stairwellStructureIn.get().place(
                        world,
                        mutable.set(-1,19,-1),
                        mutable.set(-1,19,-1),
                        structurePlacementData, random, 2
                );
            }

            Level1MazeGenerator level1MazeGenerator = new Level1MazeGenerator(8, 10, 10, x, z, "level1");
            level1MazeGenerator.setup(world, false, false, false);
            return;
        }

        if (!isOnMazeGrid(chunk)) {
            return;
        }

        double noise1 = noiseSampler.sample((x) * 0.002, 0, (z) * 0.002);
        if (server == null) {
            return;
        }

        if (!chunk.getPos().getBlockPos(0, 20, 0).isWithinDistance(new Vec3i(0, 20, 0), this.getExitSpawnRadius(world))) {
            if (noise1 <= 0) {
                boolean exitToLevel1 = random.nextBoolean();

                if (exitToLevel1) {
                    Identifier roomIdentifier = new Identifier(SPBRevamped.MOD_ID, "level1/stairwell2_1");
                    structurePlacementData.setMirror(BlockMirror.NONE).setRotation(BlockRotation.NONE).setIgnoreEntities(true);
                    Optional<StructureTemplate> stairwellStructureOutTo2 = structureTemplateManager.getTemplate(roomIdentifier);

                    if (stairwellStructureOutTo2.isPresent()) {
                        stairwellStructureOutTo2.get().place(
                                world,
                                mutable.set(x + 16, 11, z + 16),
                                mutable.set(x + 16, 11, z + 16),
                                structurePlacementData, random, 2
                        );
                    }
                }/* else {
                    Identifier roomIdentifier = new Identifier(SPBRevamped.MOD_ID, "level1/stairwell324_1");
                    Optional<StructureTemplate> stairwellStructureOutTo324 = structureTemplateManager.getTemplate(roomIdentifier);

                    if (stairwellStructureOutTo324.isPresent()) {
                        stairwellStructureOutTo324.get().place(
                                world,
                                mutable.set(x + 16, 20, z + 16),
                                mutable.set(x + 16, 20, z + 16),
                                structurePlacementData, random, 2
                        );
                    }
                }*/
            }
        }

        if (noise1 > 0) {
            Identifier roomIdentifier = new Identifier(SPBRevamped.MOD_ID, "level1/megaroom1");
            structurePlacementData.setMirror(BlockMirror.NONE).setRotation(BlockRotation.NONE).setIgnoreEntities(true);
            Optional<StructureTemplate> megaRoom = structureTemplateManager.getTemplate(roomIdentifier);

            if (megaRoom.isPresent()) {
                megaRoom.get().place(
                        world,
                        mutable.set(x - 32, 19, z - 32),
                        mutable.set(x - 32, 19, z - 32),
                        structurePlacementData, random, 2);
                megaRoom.get().place(
                        world,
                        mutable.set(x, 19, z - 32),
                        mutable.set(x, 19, z - 32),
                        structurePlacementData, random, 2);

                Identifier lightRoomIdentifier = new Identifier(SPBRevamped.MOD_ID, "level1/light" + random.nextBetween(1,6));
                structurePlacementData.setMirror(BlockMirror.NONE).setRotation(BlockRotation.NONE).setIgnoreEntities(true);

                Optional<StructureTemplate> lightStructure = structureTemplateManager.getTemplate(lightRoomIdentifier);

                if (lightStructure.isPresent()) {
                    lightStructure.get().place(
                            world,
                            mutable.set(x - 32, 19, z - 32),
                            mutable.set(x - 32, 19, z - 32),
                            structurePlacementData, random, 16);
                    lightStructure.get().place(
                            world,
                            mutable.set(x, 19, z - 32),
                            mutable.set(x, 19, z - 32),
                            structurePlacementData, random, 16);
                }
            } else {
                if (world.getBlockState(mutable.set(x, 19, z)) != Blocks.RED_WOOL.getDefaultState()) {
                    Level1MazeGenerator level1MazeGenerator = new Level1MazeGenerator(8, 10, 10, x, z, "level1");
                    level1MazeGenerator.setup(world, false, false, true);
                }
            }

            return;
        }

        if (world.getBlockState(mutable.set(x, 19, z)) != Blocks.RED_WOOL.getDefaultState()) {
            Level1MazeGenerator level1MazeGenerator = new Level1MazeGenerator(8, 10, 10, x, z, "level1");
            level1MazeGenerator.setup(world, false, false, true);
        }
    }

    private static boolean isStartChunk(Chunk chunk) {
        return (float) chunk.getPos().x == 0 && (float) chunk.getPos().z == 0;
    }

    private static boolean isOnMazeGrid(Chunk chunk) {
        return ((float) chunk.getPos().x) % SPBRevamped.FINAL_MAZE_SIZE == 0 && ((float) chunk.getPos().z) % SPBRevamped.FINAL_MAZE_SIZE == 0;
    }

    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }
}

