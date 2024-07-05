package org.dynmapblockscan.forge_1_19_3;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dynmapblockscan.core.AbstractBlockScanBase;
import org.dynmapblockscan.core.BlockScanLog;
import org.dynmapblockscan.core.BlockStateOverrides.BlockStateOverride;
import org.dynmapblockscan.core.blockstate.BSBlockState;
import org.dynmapblockscan.core.blockstate.VariantList;
import org.dynmapblockscan.core.model.BlockModel;
import org.dynmapblockscan.core.statehandlers.StateContainer.StateRec;
import org.dynmapblockscan.forge_1_19_3.statehandlers.ForgeStateContainer;

import com.google.common.collect.ImmutableMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.IdMapper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraft.world.level.block.state.BlockState;

public class DynmapBlockScanPlugin extends AbstractBlockScanBase
{
    public static DynmapBlockScanPlugin plugin;
        
    public DynmapBlockScanPlugin(MinecraftServer srv)
    {
        plugin = this;
        logger = new OurLog();
    }
            
    public void buildAssetMap() {
    	assetmap = new HashMap<String, PathElement>();
        List<IModInfo> mcl = ModList.get().getMods();
        for (IModInfo mc : mcl) {
        	String mid = mc.getModId().toLowerCase();
        	IModFileInfo mfi = mc.getOwningFile();
        	if (mfi == null) continue;
        	IModFile mf = mfi.getFile();
        	if (mf == null) continue;
            try {
            	File src = mf.getFilePath().toFile();
            	// Process mod file
            	processModFile(mid, src);
            }
            catch (UnsupportedOperationException ex) {
            	
            }
        }
    }
    
    public void onEnable() {
    }
    public void onDisable() {
    }
    public void serverStarted() {
    }
    public void serverStarting() {
    	
    	buildAssetMap();
    	
        // Load override resources
    	loadOverrideResources();
    	
        // Scan other modules for block overrides
        for (IModInfo mod : ModList.get().getMods()) {
        	loadModuleOverrideResources(mod.getModId());
        }
        Map<String, BlockRecord> blockRecords = new LinkedHashMap<String, BlockRecord>();

    	

        // Now process models from block records
        Map<String, BlockModel> models = new LinkedHashMap<String, BlockModel>();

    	IdMapper<BlockState> bsids = Block.BLOCK_STATE_REGISTRY;
        Block baseb = null;
        
        Iterator<BlockState> iter = bsids.iterator();
        // Scan blocks and block states
        while (iter.hasNext()) {
            BlockState blkstate = iter.next();
            Block b = blkstate.getBlock();
            if (b == baseb) { continue; }
            baseb = b;
            ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(b);
            StateDefinition<Block, BlockState> bsc = b.getStateDefinition();
            // See if any of the block states use MODEL
            boolean uses_model = false;
            boolean uses_nonmodel = false;
            for (BlockState bs : bsc.getPossibleStates()) {
            	switch (bs.getRenderShape()) {
            		case MODEL:
            			uses_model = true;
            			break;
            		case INVISIBLE:
            			uses_nonmodel = true;
            			if (verboselogging)
            			   
            			break;
            		case ENTITYBLOCK_ANIMATED:
            			uses_nonmodel = true;
                        if (verboselogging)
                   			
            			break;
//            		case LIQUID:
//            			uses_nonmodel = true;
//                        if (DynmapBlockScanMod.verboselogging)
//                            logger.info(String.format("%s: Liquid block - special handling", rl));
//            			break;
            	}
            }
            // Not model block - nothing else to do yet
            if (!uses_model) {
            	continue;
            }
            else if (uses_nonmodel) {
            	
            }
            // Generate property value map
            Map<String, List<String>> propMap = buildPropoertyMap(bsc);
            // Try to find blockstate file
            Material mat = blkstate.getMaterial();
            MaterialColor matcol = mat.getColor();
            BSBlockState blockstate = loadBlockState(rl.getNamespace(), rl.getPath(), overrides, propMap);
            // Build block record
            BlockRecord br = new BlockRecord();
            // Process blockstate
        	if (blockstate != null) {
                br.renderProps = blockstate.getRenderProps();
                br.materialColorID = MaterialColorID.byID(matcol.id);
                br.lightAttenuation = 15;
                try {	// Workaround for mods with broken block state logic...
                	br.lightAttenuation = blkstate.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) ? 15 : (blkstate.propagatesSkylightDown(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) ? 0 : 1);
                } catch (Exception x) {
                	
                }
        	}
        	// Build generic block state container for block
        	br.sc = new ForgeStateContainer(b, br.renderProps, propMap);
        	if (blockstate != null) {
                BlockStateOverride ovr = overrides.getOverride(rl.getNamespace(), rl.getPath());
            	br.varList = new LinkedHashMap<StateRec, List<VariantList>>();
        		// Loop through rendering states in state container
        		for (StateRec sr : br.sc.getValidStates()) {
                    Map<String, String> prop = sr.getProperties();
                    // If we've got key=value for block (multiple blocks in same state file)
                    if ((ovr != null) && (ovr.blockStateKey != null) && (ovr.blockStateValue != null)) {
                        prop = new HashMap<String, String>(prop);
                        prop.put(ovr.blockStateKey, ovr.blockStateValue);
                    }
        			List<VariantList> vlist = blockstate.getMatchingVariants(prop, models);
        			br.varList.put(sr, vlist);
        		}
        	}
        	else {
        	    br.varList = Collections.emptyMap();
        	}
            // Check for matching handler
            blockRecords.put(rl.toString(), br);
        }
        
     
        loadModels(blockRecords, models);

        // Now, resolve all parent references - load additional models
        resolveParentReferences(models);

        resolveAllElements(blockRecords, models);
       
        
        publishDynmapModData();
        
        assetmap = null;
    }
        
    @Override
    public InputStream openResource(String modid, String rname) {
        if (modid.equals("minecraft")) modid = "dynmapblockscan";   // We supply resources (1.13.2 doesn't have assets in server jar)
        String rname_lc = rname.toLowerCase();
        Optional<? extends ModContainer> mc = ModList.get().getModContainerById(modid);
        Object mod = (mc.isPresent())?mc.get().getMod():null;
        ClassLoader cl = MinecraftServer.class.getClassLoader();
        if (mod != null) {
            cl = mod.getClass().getClassLoader();
        }
        if (cl != null) {
            InputStream is = cl.getResourceAsStream(rname_lc);
            if (is == null) {
                is = cl.getResourceAsStream(rname);
            }
            if (is != null) {
                return is;
            }
        }
        return null;
    }
    
    public Map<String, List<String>> buildPropoertyMap(StateDefinition<Block, BlockState> bsc) {
    	Map<String, List<String>> renderProperties = new LinkedHashMap<String, List<String>>();
		// Build table of render properties and valid values
		for (Property<?> p : bsc.getProperties()) {
			String pn = p.getName();
			ArrayList<String> pvals = new ArrayList<String>();
			for (Comparable<?> val : p.getPossibleValues()) {
				if (val instanceof StringRepresentable) {
					pvals.add(((StringRepresentable)val).getSerializedName());
				}
				else {
					pvals.add(val.toString());
				}
			}
			renderProperties.put(pn, pvals);
		}
		return renderProperties;
    }
    
    // Build Map<String, String> from properties in BlockState
    public Map<String, String> fromBlockState(BlockState bs) {
    	ImmutableMap.Builder<String,String> bld = ImmutableMap.builder();
    	for (Property<?> x : bs.getProperties()) {
    	    Object v = bs.getValue(x);
    		if (v instanceof StringRepresentable) {
    			bld.put(x.getName(), ((StringRepresentable)v).getSerializedName());
    		}
    		else {
    			bld.put(x.getName(), v.toString());
    		}
    	}
    	return bld.build();
    }
    
    public static class OurLog implements BlockScanLog {
        Logger log;
        public static final String DM = "[DynmapBlockScan] ";
        OurLog() {
            log = LogManager.getLogger("DynmapBlockScan");
        }
        public void debug(String s) {
            log.debug(DM + s);
        }
        public void info(String s) {
            log.info(DM + s);
        }
        public void severe(Throwable t) {
            log.fatal(t);
        }
        public void severe(String s) {
            log.fatal(DM + s);
        }
        public void severe(String s, Throwable t) {
            log.fatal(DM + s, t);
        }
        public void verboseinfo(String s) {
            log.info(DM + s);
        }
        public void warning(String s) {
            log.warn(DM + s);
        }
        public void warning(String s, Throwable t) {
            log.warn(DM + s, t);
        }
    }
}

