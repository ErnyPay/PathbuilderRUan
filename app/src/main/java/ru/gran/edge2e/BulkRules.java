package ru.gran.edge2e;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** PF2e Bulk projection for the current inventory, including stowing containers. */
public final class BulkRules {
    private BulkRules() { }

    public static final class ContainerLoad {
        public final RuleItem item;
        public final int rawContentsLight;
        public final int countedContentsLight;
        public final int capacityLight;
        public final int ignoredLight;
        public final int totalLight;
        public final boolean overCapacity;
        public final boolean worn;
        ContainerLoad(RuleItem item,int raw,int counted,int capacity,int ignored,int total,boolean over,boolean worn){
            this.item=item;this.rawContentsLight=raw;this.countedContentsLight=counted;this.capacityLight=capacity;this.ignoredLight=ignored;this.totalLight=total;this.overCapacity=over;this.worn=worn;
        }
    }

    public static final class Summary {
        public final int totalLight;
        public final int encumberedAfterLight;
        public final int maxLight;
        public final boolean encumbered;
        public final boolean overMaximum;
        public final List<ContainerLoad> containers;
        Summary(int total,int enc,int max,List<ContainerLoad> containers){
            totalLight=total;encumberedAfterLight=enc;maxLight=max;this.containers=containers;
            encumbered=total>enc;overMaximum=total>max;
        }
        public String status(){return overMaximum?"ВЫШЕ МАКСИМУМА":encumbered?"ПЕРЕГРУЖЕН":"НОРМА";}
    }

    public static Summary calculate(RuleStore store,CharacterState state,StatsState stats,InventoryState inventory){
        if(store==null||state==null||inventory==null)return new Summary(0,50,100,new ArrayList<>());
        LinkedHashMap<String,RuleItem> items=new LinkedHashMap<>();
        Set<String> ids=new LinkedHashSet<>(),containerIds=new LinkedHashSet<>();
        for(int i=0;i<state.inventory.length();i++){
            RuleItem item=store.findById(storedId(state.inventory.optString(i,"")));if(item==null)continue;
            items.put(item.id,item);ids.add(item.id);if(isContainer(item))containerIds.add(item.id);
        }
        inventory.sanitize(ids,containerIds);

        List<ContainerLoad> loads=new ArrayList<>();Map<String,ContainerLoad> loadById=new LinkedHashMap<>();
        for(String id:containerIds){ContainerLoad load=containerLoad(items.get(id),items,inventory);loads.add(load);loadById.put(id,load);}

        int total=coinLightUnits(inventory);
        for(RuleItem item:items.values()){
            if(!inventory.containerFor(item.id).isEmpty())continue;
            if(isContainer(item)){ContainerLoad load=loadById.get(item.id);if(load!=null)total+=load.totalLight;}
            else total+=itemLightUnits(item,inventory.quantity(item.id));
        }
        int str=stats==null?0:stats.ability("str");
        int enc=Math.max(0,5+str)*10,max=Math.max(0,10+str)*10;
        return new Summary(total,enc,max,loads);
    }

    public static ContainerLoad containerLoad(RuleItem container,Map<String,RuleItem> items,InventoryState inventory){
        if(container==null)return null;
        int raw=0;
        for(RuleItem item:items.values())if(container.id.equals(inventory.containerFor(item.id))&&!container.id.equals(item.id))raw+=itemLightUnits(item,inventory.quantity(item.id));
        int capacity=toLight(container.meta.optDouble("bulkCapacity",0));
        int ignored=toLight(container.meta.optDouble("bulkIgnored",0));
        boolean over=capacity>0&&raw>capacity;
        int counted=over?raw:Math.max(0,raw-ignored);
        boolean worn=inventory.isContainerWorn(container.id);
        double ownValue=worn?container.meta.optDouble("bulkValue",0):container.meta.optDouble("bulkHeldOrStowed",container.meta.optDouble("bulkValue",0));
        int own=toLight(ownValue)*Math.max(1,inventory.quantity(container.id));
        return new ContainerLoad(container,raw,counted,capacity,ignored,own+counted,over,worn);
    }

    public static int itemLightUnits(RuleItem item,int quantity){
        if(item==null||quantity<=0)return 0;
        String base=slug(item.meta.optString("baseItem",""));
        int per=stackSize(base);
        if(per>0)return (quantity/per)*stackLight(base);
        double value=item.meta.has("bulkValue")?item.meta.optDouble("bulkValue",0):bulkNumber(item.meta.opt("bulk"));
        return Math.max(0,(int)Math.round(value*10*quantity));
    }

    public static boolean isContainer(RuleItem item){return item!=null&&(item.meta.optDouble("bulkCapacity",0)>0||"backpack".equalsIgnoreCase(item.subtype));}
    public static String label(int lightUnits){
        int v=Math.max(0,lightUnits),normal=v/10,light=v%10;
        if(v==0)return "—";
        if(normal==0)return light==1?"L":light+"L";
        if(light==0)return String.valueOf(normal);
        return normal+"; "+(light==1?"L":light+"L");
    }
    public static String itemBulkLabel(RuleItem item,int quantity){return label(itemLightUnits(item,quantity));}

    private static int coinLightUnits(InventoryState i){int coins=Math.max(0,i.pp)+Math.max(0,i.gp)+Math.max(0,i.sp)+Math.max(0,i.cp);return (coins/1000)*10;}
    private static int toLight(double bulk){return Math.max(0,(int)Math.round(bulk*10));}
    private static double bulkNumber(Object raw){if(raw instanceof Number)return ((Number)raw).doubleValue();return 0;}
    private static int stackSize(String base){switch(base){case"arrows":case"bolts":case"sling-bullets":case"blowgun-darts":case"wooden-taws":case"rounds10":return 10;case"rounds5":return 5;default:return 0;}}
    private static int stackLight(String base){return 1;}
    private static String storedId(String raw){int i=raw==null?-1:raw.indexOf('\u001f');return i>=0?raw.substring(0,i):raw;}
    private static String slug(String v){return v==null?"":v.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");}
}
