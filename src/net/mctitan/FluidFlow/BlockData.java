package net.mctitan.FluidFlow;

import java.io.Serializable;

/**
 * A place holder for the block data that each fluid may need
 *
 * @author mindless728
 */
public class BlockData implements Serializable {
    private static final long serialVersionUID = 1;
    public long flowDelay;
    
    public BlockData() {
        this(0);
    }
    
    public BlockData(long delta) {
        flowDelay = System.nanoTime()+delta;
    }
}
