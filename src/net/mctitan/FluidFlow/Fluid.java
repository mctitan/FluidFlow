package net.mctitan.FluidFlow;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.lang.reflect.Field; 

/**
 * the base fluid that is inherited by other plugin devs to create fluids
 *
 * @author mindless728
 */
public abstract class Fluid extends JavaPlugin implements Runnable {
    /** the queued up flows */
    private LinkedList<FluidBlock> flows;

    /** this fluids changed blocks */
    private ChangedBlocks changedBlocks;

    /** all of the changed blocks */
    private static HashMap<Fluid, ChangedBlocks> allChanges;

    /** the FluidFlow plugin, needed for fluid registration */
    private FluidFlow plugin;

    /** tells whether the fluid is running or not */
    private boolean running;

    /** tells whether the fluid is stopped or not */
    private boolean stopped;

    /** the sleep time to wait if there are no flows, in nano-seconds */
    private int sleepTime = 100000;
    
    /** the number of threads teh fluid should use to calculate flows */
    private long numberThreads = 1;
    
    /** the length of time the fluid should delay, implementation specific */
    private long flowDelay = 500000000L;
    
    /** threads of fluid execution */
    private FluidThread[] threads;

    /**
     * lets all fluids know about each other's changes, for synchronizing between
     * fluids to let each other know about cahnged blocks
     *
     * @param ac the mapping from fluids to changed blocks
     */
    protected static void setAllChanges(HashMap<Fluid, ChangedBlocks> ac) {
        allChanges = ac;
    }

    /** default constructor */
    public Fluid() {
        //creates the list object to hold the flows in order
        flows = new LinkedList<>();

        running = false;
        stopped = false;
    }

    /** called when the fluid is enabled */
    @Override
    public void onEnable() {
        //get the fluid flow plugin
        plugin = (FluidFlow)getServer().getPluginManager().getPlugin("FluidFlow");

        //try to register the fluid with its material type
        if((changedBlocks = plugin.registerFluid(this, getMaterial())) == null) {
            //if it could not be registered, disable the plugin and return
            System.out.println("**** ERROR! "+getDescription().getName()+": could not register fluid, check Material."+getMaterial()+" and check for a conflict ****");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        //grab the sleep timer
        sleepTime = plugin.getConfig().getInt("Fluid.sleepTime",sleepTime);
        plugin.saveConfig();
                
        //setup the default config
        setConfigFile(); //hacks!!!
        getConfig().addDefault("Fluid.numberThreads", numberThreads);
        getConfig().addDefault("Fluid.flowDelay", flowDelay);
        getConfig().options().copyDefaults(true);
        numberThreads = getConfig().getLong("Fluid.numberThreads");
        flowDelay = getConfig().getLong("Fluid.flowDelay");
        saveConfig();
        
        //initialize the fluid
        init();
        
        //setup the fluid threads
        threads = new FluidThread[(int)numberThreads];
        for(int i = 0; i < numberThreads; ++i)
            threads[i] = new FluidThread(this);
                
        //start the fluid's asynchronous thread
        start();

        //tell the operator that the plugin is enabled
        //System.out.println(getDescription().getName()+" version "+getDescription().getVersion()+" enabled");
    }

    /** called when the fluid is disabled */
    @Override
    public void onDisable() {
        //tell the operator that the plugin is disabled
        //System.out.println(getDescription().getName()+" version "+getDescription().getVersion()+" disabled");
    }
        
    /** edits where the config file is store using magic hackery */
    private void setConfigFile() {
        Class clazz = JavaPlugin.class;
        try {
            Field field = clazz.getDeclaredField("configFile");
            field.setAccessible(true);
            field.set(this, getConfigFile());
        } catch(Exception e) {System.out.println(e);}
    }
        
    /** the config file we want, depends on the material type */
    private File getConfigFile() {
        File file = new File(plugin.getDataFolder().getPath()+File.separator+getMaterial().name().toLowerCase()+".yml");
        if(!file.exists())
            try {
                file.createNewFile();
            } catch(Exception e) {}
        return file;
    }

    /**
     * adds a flow to the flows list
     *
     * @param flow the flow to add
     */
    public void addFlow(FluidBlock flow) {
        long time = flowDelay+System.nanoTime();
        if(flow.data == null)
            flow.data = new BlockData();
        flow.data.flowDelay = time;
        flowChange(flow);
    }

    /**
     * removes the first flow in the list
     *
     * @return the flow if there is one, null otherwise
     */
    public FluidBlock getFlow() {
        return flowChange(null);
    }

    /**
     * controls the adding/removal of flows in the flows list
     *
     * @param flow the flow to add, leave null if removing
     *
     * @return the flow if removing a flow and one exists, null otherwise
     */
    public synchronized FluidBlock flowChange(FluidBlock flow) {
        FluidBlock ret = null;
        //check to see if you are adding or removing
        if(flow != null) {
            //adding
            flows.add(flow);
            ret = null;
        } else if(flows.size() > 0) {
            //removing
            ret = flows.remove();
        }
        return ret;
    }
    
    /** gets the flow delay */
    public long getFlowDelay() {
        return flowDelay;
    }

    /** the separate thread that runs the fluid */
    public void run() {
        FluidBlock temp;

        //set the state to running and not stopped
        running = true;
        stopped = false;

        //loop while the fluid is running
        while(running) {
            //get the next flow
            temp = getFlow();

            //test to see if there wasn't a flow
            if(temp == null) {
                //wait a small amount of time
                sleep(sleepTime);
                continue;
            }
            
            //wait until the flow is ready to go
            while(temp.data != null && temp.data.flowDelay > System.nanoTime())
                try{Thread.sleep(0, 1000);}catch(Exception e) {} //sleeps 1 micro-second

            //safegaurd the running thread by catching all exceptions and errors
            try {
                //pass the flow to the actual flow method
                flow(temp);
            //do nothing if something is caught, whatever
            } catch(Exception e) {
            } catch(StackOverflowError sofe) {
            }
        }

        //if you get here, the fluid is stopped
        stopped = true;
    }

    /**
     * sets the fluid block to a new type
     *
     * @param fb the fluid block to change
     * @param type the new type you want it changed to
     */
    public void setType(FluidBlock fb, Material type) {
        //set newType in the fluid block
        fb.newType = type;

        //lock the changed blocks
        synchronized(changedBlocks) {
            //add this changed block to the changed block list
            changedBlocks.add(fb);
        }
    }

    /**
     * gets the material type of a fluid block
     *
     * @param fb the fluid block to get the type from
     *
     * @return the type the fluid block points to
     */
    public Material getType(FluidBlock fb) {
        Material ret = null;
        ChangedBlocks temp = null;

        //loop through all of the materials to get a type, first one
        //that returns a type wins
        for(Fluid f : allChanges.keySet()) {
            temp = allChanges.get(f);
            synchronized(temp) {
                ret = temp.getType(fb);
            }
            if(ret != null)
                break;
        }

        //if there was no type from the changed blocks, grab the server type
        if(ret == null)
            ret = fb.loc.getBlock().getType();

        //return the type
        return ret;
    }

    /**
     * sleeps the fluid for an amount of time in nano-seconds
     * CAUTION: do not use in main server thread EVER!
     *
     * @param time the amount of time in nano-seconds to sleep for
     */
    private void sleep(int time) {
        try{Thread.sleep(0,time);}catch(Exception e){}
    }

    /** starts the asynchronous thread the fluid uses to run */
    private void start() {
        //(new Thread(this)).start();
        for(int i = 0; i < numberThreads; ++i)
            threads[i].start();
    }

    /** stops the asynchronous thread the fluid uses to run */
    public void stop() {
        //set running to false to stop the fluid
        running = false;

        //wait for it to stop
        //while(!stopped) sleep(sleepTime);
        for(int i = 0; i < numberThreads; ++i)
            while(!threads[i].stopped) sleep(sleepTime);
    }

    /**
     * abstract method to get the material used by the fluid
     *
     * @return the material type the fluid uses, DO NOT RETURN NULL
     */
    public abstract Material getMaterial();

    /**
     * abstract method that the fluid uses to flow a single block
     *
     * @param block the fluid block that is trying to flow
     */
    public abstract void flow(FluidBlock block);

    /**
     * called so that the fluid can initialize itself and load configuration
     */
    public abstract void init();
    
    /** wrapper for Fluid Class to allow multiple fluid threads */
    private class FluidThread extends Thread {
        public boolean stopped;
        Fluid fluid;
        
        public FluidThread(Fluid fluid) {
            stopped = false;
            this.fluid = fluid;
        }
        
        @Override
        public void run() {
            fluid.run();
            stopped = true;
        }
    }
}
