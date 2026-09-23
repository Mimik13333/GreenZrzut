package pl.mimik.greenzrzut;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;

public class GreenZrzut extends JavaPlugin implements Listener, CommandExecutor {
    private static final int MAX = 5;
    private final Map<Integer, Drop> drops = new LinkedHashMap<>();
    private final Map<UUID, Input> input = new HashMap<>();
    private NamespacedKey key;
    private File file;

    public void onEnable() {
        saveDefaultConfig();
        key = new NamespacedKey(this, "drop_id");
        file = new File(getDataFolder(), "zrzuty.dat");
        loadDrops();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("greenzrzut").setExecutor(this);
    }
    public void onDisable(){ saveDrops(); }

    private String msg(String k,String... r){
        String s=ChatColor.translateAlternateColorCodes('&',getConfig().getString("messages."+k,""));
        for(int i=0;i+1<r.length;i+=2)s=s.replace(r[i],r[i+1]);
        return s;
    }
    private ItemStack item(Material m,String n,String... lore){
        ItemStack i=new ItemStack(m); ItemMeta meta=i.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',n));
        List<String> l=new ArrayList<>(); for(String x:lore)l.add(ChatColor.translateAlternateColorCodes('&',x));
        meta.setLore(l); i.setItemMeta(meta); return i;
    }
    private void fill(Inventory inv){
        ItemStack x=item(Material.GRAY_STAINED_GLASS_PANE," ");
        for(int i=0;i<inv.getSize();i++)inv.setItem(i,x);
    }

    public boolean onCommand(CommandSender s,Command c,String label,String[] a){
        if(!(s instanceof Player p)){s.sendMessage(msg("only-player"));return true;}
        if(!p.hasPermission("greenzrzut.admin")){p.sendMessage(msg("no-permission"));return true;}
        main(p); return true;
    }

    private void main(Player p){
        Inventory i=Bukkit.createInventory(null,27,"§2§lGreenZrzut"); fill(i);
        i.setItem(11,item(Material.BARREL,"&a➕ Stwórz zrzut","&7Maksymalnie 5 zrzutów."));
        i.setItem(13,item(Material.CHEST,"&e📦 Zarządzaj zrzutami","&7Aktywne: &f"+drops.size()+"&7/&f5"));
        i.setItem(22,item(Material.LIME_DYE,"&b🔄 Reload"));
        p.openInventory(i);
    }
    private void createMenu(Player p){
        Input s=input.computeIfAbsent(p.getUniqueId(),x->new Input());
        Inventory i=Bukkit.createInventory(null,36,"§2§lNowy zrzut");fill(i);
        i.setItem(10,item(Material.COMPASS,"&a📍 Lokalizacja",s.loc==null?"&7Nie ustawiono":"&7"+loc(s.loc)));
        i.setItem(13,item(Material.CLOCK,"&e⏱ Czas otwarcia",s.time<0?"&7Nie ustawiono":"&7"+s.time+" sekund"));
        i.setItem(16,item(Material.BARREL,"&b🛢 Loot","&7Ustaw zawartość beczki."));
        i.setItem(22,item(Material.LIME_DYE,"&a💾 Stwórz zrzut"));
        i.setItem(31,item(Material.RED_DYE,"&c✕ Anuluj"));
        p.openInventory(i);
    }
    private void manage(Player p){
        Inventory i=Bukkit.createInventory(null,27,"§2§lZrzuty");fill(i);int slot=10;
        for(Drop d:drops.values()) if(slot<18)i.setItem(slot++,item(Material.BARREL,"&a📦 Zrzut #"+d.id,
            "&7XYZ: &f"+loc(d.loc),"&7Otwarcie: &f"+d.time+"s","&eKliknij, aby edytować."));
        i.setItem(22,item(Material.ARROW,"&c← Powrót"));p.openInventory(i);
    }
    private void edit(Player p,Drop d){
        Input s=input.computeIfAbsent(p.getUniqueId(),x->new Input());s.drop=d;s.creating=false;
        Inventory i=Bukkit.createInventory(null,36,"§2§lEdycja #"+d.id);fill(i);
        i.setItem(10,item(Material.COMPASS,"&a📍 Zmień kordy","&7"+loc(d.loc)));
        i.setItem(13,item(Material.CLOCK,"&e⏱ Zmień czas","&7"+d.time+"s"));
        i.setItem(16,item(Material.BARREL,"&b🛢 Edytuj loot"));
        i.setItem(22,item(Material.LIME_DYE,"&a💾 Zapisz zmiany"));
        i.setItem(24,item(Material.RED_DYE,"&c🗑 Usuń zrzut"));
        i.setItem(31,item(Material.ARROW,"&c← Powrót"));p.openInventory(i);
    }
    private void loot(Player p,Drop d,boolean creating){
        Inventory i=Bukkit.createInventory(null,54,"§2§lLoot #"+(creating?"NOWY":d.id));
        if(d!=null&&d.loot!=null)i.setContents(d.loot.clone());
        Input s=input.computeIfAbsent(p.getUniqueId(),x->new Input());s.drop=d;s.creating=creating;
        p.openInventory(i);
    }
    private void askLoc(Player p){
        Input s=input.computeIfAbsent(p.getUniqueId(),x->new Input());s.mode=1;p.closeInventory();p.sendMessage(msg("prompt-location"));
    }
    private void askTime(Player p){
        Input s=input.computeIfAbsent(p.getUniqueId(),x->new Input());s.mode=2;p.closeInventory();p.sendMessage(msg("prompt-time"));
    }

    @EventHandler public void chat(AsyncPlayerChatEvent e){
        Player p=e.getPlayer();Input s=input.get(p.getUniqueId());if(s==null||s.mode==0)return;e.setCancelled(true);
        String t=e.getMessage().trim();
        if(t.equalsIgnoreCase("anuluj")){s.mode=0;Bukkit.getScheduler().runTask(this,()->{p.sendMessage(msg("cancelled"));if(s.creating)createMenu(p);else if(s.drop!=null)edit(p,s.drop);else main(p);});return;}
        if(s.mode==1){
            String[] a=t.split("\\s+");if(a.length!=3){p.sendMessage(msg("invalid-coords"));return;}
            try{s.loc=new Location(p.getWorld(),Double.parseDouble(a[0]),Double.parseDouble(a[1]),Double.parseDouble(a[2]));s.mode=0;
                Bukkit.getScheduler().runTask(this,()->{p.sendMessage(msg("location-set"));if(s.creating)createMenu(p);else edit(p,s.drop);});
            }catch(Exception x){p.sendMessage(msg("invalid-coords"));}
        }else{
            try{int n=Integer.parseInt(t);if(n<0)throw new Exception();s.time=n;s.mode=0;
                Bukkit.getScheduler().runTask(this,()->{p.sendMessage(msg("time-set","%seconds%",String.valueOf(n)));if(s.creating)createMenu(p);else edit(p,s.drop);});
            }catch(Exception x){p.sendMessage(msg("invalid-time"));}
        }
    }

    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        String t=ChatColor.stripColor(e.getView().getTitle());int slot=e.getRawSlot();
        if(t.equals("GreenZrzut")){
            e.setCancelled(true);
            if(slot==11){if(drops.size()>=MAX){p.sendMessage(msg("max"));return;}Input s=new Input();s.creating=true;s.time=-1;input.put(p.getUniqueId(),s);createMenu(p);}
            else if(slot==13)manage(p);
            else if(slot==22){reloadConfig();p.sendMessage(msg("reload"));main(p);}
        }else if(t.equals("Nowy zrzut")){
            e.setCancelled(true);Input s=input.get(p.getUniqueId());
            if(slot==10)askLoc(p);else if(slot==13)askTime(p);else if(slot==16)loot(p,null,true);
            else if(slot==22)make(p,s);else if(slot==31){input.remove(p.getUniqueId());p.closeInventory();p.sendMessage(msg("cancelled"));}
        }else if(t.equals("Zrzuty")){
            e.setCancelled(true);if(slot==22){main(p);return;}
            if(slot>=10&&slot<18){int n=slot-10;if(n<drops.size())edit(p,new ArrayList<>(drops.values()).get(n));}
        }else if(t.startsWith("Edycja #")){
            e.setCancelled(true);int id=Integer.parseInt(t.substring(7));Drop d=drops.get(id);Input s=input.get(p.getUniqueId());if(d==null)return;
            if(slot==10)askLoc(p);else if(slot==13)askTime(p);else if(slot==16)loot(p,d,false);
            else if(slot==22){if(s.loc!=null)d.loc=s.loc.clone();if(s.time>=0)d.time=s.time;if(s.loot!=null)d.loot=s.loot.clone();place(d);saveDrops();p.sendMessage(msg("edited"));manage(p);}
            else if(slot==24){remove(d);drops.remove(d.id);saveDrops();input.remove(p.getUniqueId());p.sendMessage(msg("deleted","%id%",String.valueOf(d.id)));manage(p);}
            else if(slot==31)manage(p);
        }
    }

    @EventHandler public void close(InventoryCloseEvent e){
        if(!(e.getPlayer() instanceof Player p))return;String t=ChatColor.stripColor(e.getView().getTitle());if(!t.startsWith("Loot #"))return;
        Input s=input.get(p.getUniqueId());if(s==null)return;s.loot=e.getInventory().getContents().clone();
        if(s.creating){p.sendMessage(msg("loot-saved"));createMenu(p);}else if(s.drop!=null){s.drop.loot=s.loot.clone();p.sendMessage(msg("loot-saved"));edit(p,s.drop);}
    }

    private void make(Player p,Input s){
        if(s.loc==null){p.sendMessage(msg("missing-location"));return;}if(s.time<0){p.sendMessage(msg("missing-time"));return;}if(drops.size()>=MAX){p.sendMessage(msg("max"));return;}
        int id=1;while(drops.containsKey(id))id++;Drop d=new Drop(id,s.loc.clone(),s.time,s.loot==null?new ItemStack[54]:s.loot.clone());drops.put(id,d);
        if(!place(d)){drops.remove(id);p.sendMessage(msg("place-failed"));return;}saveDrops();input.remove(p.getUniqueId());p.sendMessage(msg("created","%id%",String.valueOf(id)));p.closeInventory();
    }
    private boolean place(Drop d){
        Block b=d.loc.getBlock();if(!b.isEmpty()&&b.getType()!=Material.BARREL)return false;b.setType(Material.BARREL);
        if(b.getState() instanceof Container c){c.getPersistentDataContainer().set(key,PersistentDataType.INTEGER,d.id);c.update();if(d.loot!=null)c.getInventory().setContents(d.loot.clone());}return true;
    }
    private void remove(Drop d){if(d.loc.getBlock().getType()==Material.BARREL)d.loc.getBlock().setType(Material.AIR);}
    @EventHandler public void breakBarrel(BlockBreakEvent e){
        if(e.getBlock().getType()!=Material.BARREL)return;Container c=(Container)e.getBlock().getState();Integer id=c.getPersistentDataContainer().get(key,PersistentDataType.INTEGER);
        if(id!=null&&drops.containsKey(id)){e.setCancelled(true);e.getPlayer().sendMessage(msg("not-ready"));}
    }
    @EventHandler public void open(InventoryOpenEvent e){
        if(!(e.getInventory().getHolder() instanceof Container c))return;Integer id=c.getPersistentDataContainer().get(key,PersistentDataType.INTEGER);if(id==null)return;Drop d=drops.get(id);if(d==null)return;
        long left=d.time*1000L-(System.currentTimeMillis()-d.created);if(left>0){e.setCancelled(true);if(e.getPlayer() instanceof Player p)p.sendMessage(msg("not-ready","%seconds%",String.valueOf((left+999)/1000)));return;}
        if(d.opened){e.setCancelled(true);if(e.getPlayer() instanceof Player p)p.sendMessage(msg("already-opened"));return;}d.opened=true;saveDrops();
    }

    private String loc(Location l){return l.getBlockX()+" "+l.getBlockY()+" "+l.getBlockZ();}
    private void saveDrops(){
        try{getDataFolder().mkdirs();DataOutputStream o=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));o.writeInt(drops.size());
            for(Drop d:drops.values()){o.writeInt(d.id);o.writeUTF(d.loc.getWorld().getName());o.writeDouble(d.loc.getX());o.writeDouble(d.loc.getY());o.writeDouble(d.loc.getZ());o.writeInt(d.time);o.writeLong(d.created);o.writeBoolean(d.opened);
                for(int i=0;i<54;i++){ItemStack x=d.loot!=null&&i<d.loot.length?d.loot[i]:null;o.writeBoolean(x!=null);if(x!=null){byte[] b=serialize(x);o.writeInt(b.length);o.write(b);}}}o.close();
        }catch(Exception x){getLogger().warning("Save error: "+x.getMessage());}
    }
    private byte[] serialize(ItemStack i)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();ObjectOutputStream o=new ObjectOutputStream(b);o.writeObject(i);o.close();return b.toByteArray();}
    private ItemStack deserialize(byte[] b)throws Exception{ObjectInputStream o=new ObjectInputStream(new ByteArrayInputStream(b));return (ItemStack)o.readObject();}
    private void loadDrops(){
        if(!file.exists())return;try{DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(file)));int n=in.readInt();
            for(int z=0;z<n;z++){int id=in.readInt();World w=Bukkit.getWorld(in.readUTF());double x=in.readDouble(),y=in.readDouble(),zz=in.readDouble();int time=in.readInt();long created=in.readLong();boolean opened=in.readBoolean();ItemStack[] loot=new ItemStack[54];
                for(int i=0;i<54;i++)if(in.readBoolean()){int len=in.readInt();byte[] b=in.readNBytes(len);loot[i]=deserialize(b);}
                if(w!=null){Drop d=new Drop(id,new Location(w,x,y,zz),time,loot);d.created=created;d.opened=opened;drops.put(id,d);if(!opened)place(d);}
            }in.close();
        }catch(Exception x){getLogger().warning("Load error: "+x.getMessage());}
    }
    static class Drop{int id,time;Location loc;long created=System.currentTimeMillis();boolean opened;ItemStack[] loot;Drop(int i,Location l,int t,ItemStack[] q){id=i;loc=l;time=t;loot=q;}}
    static class Input{int mode=0,time=-1;Location loc;Drop drop;boolean creating;ItemStack[] loot;}
}
