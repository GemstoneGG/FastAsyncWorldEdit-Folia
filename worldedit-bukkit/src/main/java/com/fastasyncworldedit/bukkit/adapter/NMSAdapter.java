package com.fastasyncworldedit.bukkit.adapter;

import com.fastasyncworldedit.bukkit.FaweBukkitWorld;
import com.fastasyncworldedit.core.FAWEPlatformAdapterImpl;
import com.fastasyncworldedit.core.Fawe;
import com.fastasyncworldedit.core.math.IntPair;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.util.MathMan;
import com.fastasyncworldedit.core.util.ReflectionUtils;
import com.sk89q.worldedit.world.block.BlockTypesCache;

import java.util.concurrent.locks.StampedLock;
import java.util.function.IntFunction;

public class NMSAdapter implements FAWEPlatformAdapterImpl {

    public static int createPalette(
            int[] blockToPalette,
            int[] paletteToBlock,
            int[] blocksCopy,
            char[] set,
            CachedBukkitAdapter adapter,
            final boolean globalKindaDoesNotExist
    ) {
        int numPaletteEntries = 0;
        for (int i = 0; i < 4096; i++) {
            int ordinal = set[i];
            ordinal = Math.max(ordinal, BlockTypesCache.ReservedIDs.AIR);
            int palette = blockToPalette[ordinal];
            if (palette == Integer.MAX_VALUE) {
                blockToPalette[ordinal] = numPaletteEntries;
                paletteToBlock[numPaletteEntries] = ordinal;
                numPaletteEntries++;
            }
        }
        mapPalette(blockToPalette, paletteToBlock, blocksCopy, set, adapter, numPaletteEntries, globalKindaDoesNotExist);

        return numPaletteEntries;
    }

    public static int createPalette(
            int layer,
            int[] blockToPalette,
            int[] paletteToBlock,
            int[] blocksCopy,
            IntFunction<char[]> get,
            char[] set,
            CachedBukkitAdapter adapter,
            final boolean globalKindaDoesNotExist
    ) {
        int numPaletteEntries = 0;
        char[] getArr = null;
        for (int i = 0; i < 4096; i++) {
            char ordinal = set[i];
            if (ordinal == BlockTypesCache.ReservedIDs.__RESERVED__) {
                if (getArr == null) {
                    getArr = get.apply(layer);
                }
                ordinal = getArr[i];
                // write to set array as this should be a copied array, and will be important when the changes are written
                // to the GET chunk cached by FAWE. Future dords, actually read this comment please.
                set[i] = (char) Math.max(ordinal, BlockTypesCache.ReservedIDs.AIR);
            }
            int palette = blockToPalette[ordinal];
            if (palette == Integer.MAX_VALUE) {
                blockToPalette[ordinal] = numPaletteEntries;
                paletteToBlock[numPaletteEntries] = ordinal;
                numPaletteEntries++;
            }
        }
        mapPalette(blockToPalette, paletteToBlock, blocksCopy, set, adapter, numPaletteEntries, globalKindaDoesNotExist);

        return numPaletteEntries;
    }

    private static void mapPalette(
            int[] blockToPalette,
            int[] paletteToBlock,
            int[] blocksCopy,
            char[] set,
            CachedBukkitAdapter adapter,
            int numPaletteEntries,
            boolean globalKindaDoesNotExist
    ) {
        int bitsPerEntry = MathMan.log2nlz(numPaletteEntries - 1);
        // If bits per entry is over 8, the game uses the global palette.
        if (!globalKindaDoesNotExist && bitsPerEntry > 8 && adapter != null) {
            System.arraycopy(adapter.getIbdToOrdinal(), 0, paletteToBlock, 0, adapter.getIbdToOrdinal().length);
            System.arraycopy(adapter.getOrdinalToIbdID(), 0, blockToPalette, 0, adapter.getOrdinalToIbdID().length);
        }
        int oldVal = blockToPalette[BlockTypesCache.ReservedIDs.__RESERVED__];
        blockToPalette[BlockTypesCache.ReservedIDs.__RESERVED__] = blockToPalette[BlockTypesCache.ReservedIDs.AIR];
        for (int i = 0; i < 4096; i++) {
            int ordinal = set[i];
            int palette = blockToPalette[ordinal];
            blocksCopy[i] = palette;
        }
        blockToPalette[BlockTypesCache.ReservedIDs.__RESERVED__] = oldVal;
    }

    @Override
    public void sendChunk(IChunkGet chunk, int mask, boolean lighting) {
        if (!(chunk instanceof AbstractBukkitGetBlocks)) {
            throw new IllegalArgumentException("(IChunkGet) chunk not of type BukkitGetBlocks");
        }
        ((AbstractBukkitGetBlocks<?, ?>) chunk).send();
    }

    /**
     * Atomically set the given chunk section to the chunk section array stored in the chunk, given the expected existing chunk
     * section instance at the given layer position.
     * <p>
     * Acquires a (FAWE-implemented only) write-lock on the chunk packet lock, waiting if required before writing, then freeing
     * the lock. Also sets a boolean to indicate a write is waiting and therefore reads should not occur.
     * <p>
     * Utilises ConcurrentHashMap#compute for easy synchronisation for all of the above. Only tryWriteLock is used in blocks
     * synchronised using ConcurrentHashMap methods.
     *
     * @since 2.12.0
     */
    protected static <LevelChunkSection> boolean setSectionAtomic(
            String worldName,
            IntPair pair,
            LevelChunkSection[] sections,
            LevelChunkSection expected,
            LevelChunkSection value,
            int layer
    ) {
        if (layer < 0 || layer >= sections.length) {
            return false;
        }
        if (Fawe.isMainThread()) {
            return ReflectionUtils.compareAndSet(sections, expected, value, layer);
        }
        ChunkSendLock chunkLock = FaweBukkitWorld.getWorldSendingChunksMap(worldName)
                .computeIfAbsent(pair, k -> new ChunkSendLock());
        long stamp = chunkLock.lock.tryWriteLock();
        for (int spins = 0; stamp == 0L && spins < 64; spins++) {
            Thread.onSpinWait();
            stamp = chunkLock.lock.tryWriteLock();
        }
        if (stamp != 0L) {
            try {
                return ReflectionUtils.compareAndSet(sections, expected, value, layer);
            } finally {
                chunkLock.lock.unlockWrite(stamp);
            }
        }
        return ReflectionUtils.compareAndSet(sections, expected, value, layer);
    }

    /**
     * Called before sending a chunk packet, filling the given stamp and stampedLock arrays' zeroth indices if the chunk packet
     * send should go ahead.
     * <p>
     * Chunk packets should be sent if both of the following are met:
     *  - There is no more than one current packet send ongoing
     *  - There is no chunk section "write" waiting or ongoing,
     * which are determined by the number of readers currently locking the StampedLock (i.e. the number of sends), if the
     * stamped lock is currently write-locked and if the boolean for waiting write is true.
     * <p>
     * Utilises ConcurrentHashMap#compute for easy synchronisation
     *
     * @since 2.12.0
     */
    protected static void beginChunkPacketSend(String worldName, IntPair pair, StampLockHolder holder) {
        holder.chunkLock = FaweBukkitWorld.getWorldSendingChunksMap(worldName)
                .computeIfAbsent(pair, k -> new ChunkSendLock());
    }

    /**
     * Opens an optimistic read window immediately before a chunk packet is built. Pairs with
     * {@link #validateChunkPacketSend}. Acquires nothing - safe to use across a region handoff.
     *
     * @since 2.x.x
     */
    protected static void markChunkPacketRead(StampLockHolder holder) {
        if (holder.chunkLock != null) {
            holder.stamp = holder.chunkLock.lock.tryOptimisticRead();
        }
    }

    /**
     * Validates, immediately after a chunk packet is built, that no section swap occurred during
     * the build. If this returns {@code false} the packet was assembled from a torn section array
     * and must be discarded rather than sent.
     *
     * @return {@code true} if the built packet is consistent and safe to send
     * @since 2.x.x
     */
    protected static boolean validateChunkPacketSend(StampLockHolder holder) {
        return holder.chunkLock != null && holder.chunkLock.lock.validate(holder.stamp);
    }

    /**
     * Releases the read lock acquired when sending a chunk packet for a chunk
     *
     * @since 2.12.0
     */
    protected static void endChunkPacketSend(String worldName, IntPair pair, StampLockHolder holder) {
        if (holder.chunkLock != null) {
            FaweBukkitWorld.getWorldSendingChunksMap(worldName).remove(pair, holder.chunkLock);
        }
    }

    public static final class StampLockHolder {
        public long stamp;
        public ChunkSendLock chunkLock = null;
    }

    public static final class ChunkSendLock {

        public final StampedLock lock = new StampedLock();

    }

}
