package net.mctitan.FluidFlow;

import java.util.HashMap;
import java.util.LinkedList;
import org.bukkit.block.Block;

/**
 * Changes the blocks from the asynchronous threads that the fluids flow int
 *
 * @author mindless728
 */
public class BlockChanger implements Runnable {
    /** the mapping of the fluids to their respective changed blocks */
    private HashMap<Fluid, ChangedBlocks> blockChanges;

    /** the amount of time per server tick the block changer runs for in nano-seconds */
    private int runTime = 10000000;

    /** the amount of blocks that are changed per round through the block changer */
    private int changeCountPerIter = 1000;

    /** the FluidFlow plugin object */
    FluidFlow plugin;
    

    /**
     * Default constructor taking in the mapping from fluids to the changed blocks
     *
     * @param bc the mapping from fluids to changed blocks
     */
    public BlockChanger(HashMap<Fluid, ChangedBlocks> bc, FluidFlow p) {
        blockChanges = bc;
        plugin = p;
                
        runTime = plugin.getConfig().getInt("BlockChanger.runTime");
        changeCountPerIter = plugin.getConfig().getInt("BlockChanger.changeCountPerIter");
        plugin.saveConfig();
    }

    /**
     * The code that changes the blocks, this is run every server tick
     */
    public void run() {
        long start = System.nanoTime();
        ChangedBlocks temp;
        FluidBlock block;
        long changes;
        Block btemp;

        //loop while there is time left in this server tick
        while((System.nanoTime() - start) < runTime) {
            //reset the changes variable
            changes = 0;
            
            //loop through the fluids constantly
            for(Fluid f : blockChanges.keySet()) {
                //get the changed blocks from the fluid and lock it
                temp = blockChanges.get(f);
                synchronized(temp) {
                    //only change so many blocks per round to give fluids an "equal" share of time
                    for(int i = 0; i < changeCountPerIter; ++i) {
                        if(!temp.isEmpty()) {
                            //remove the changed block from the queue and set the type on the servers end
                            block = temp.remove();
                            btemp = block.loc.getBlock();
                            btemp.setType(block.newType.getItemType());
                            btemp.setData(block.newType.getData());
                            
                            //increment the change counter
                            ++changes;
                        } else //if there are no changes, go to next fluid
                            break;
                    }
                }
            }
            
            //if there were no changes, break out
            if(changes == 0)
                break;
        }
    }
}
