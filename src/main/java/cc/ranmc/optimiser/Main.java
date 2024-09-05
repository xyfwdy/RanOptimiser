package cc.ranmc.optimiser;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Breedable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Main extends JavaPlugin implements Listener {

    @Getter
    private String prefix;
    //TPS
    private Double tps = 19.99;
    private Double tpsCheck = 0.0;
    //生物堆叠器
    private List<String> stackerList;
    //限制生物过多
    private Map<String, Integer> mob = new HashMap<>();
    private int spawnTime = 1;
    //红石高频
    private final Map<Location, Long> redstone = new HashMap<>();
    private Map<Location, Integer> warning = new ConcurrentHashMap<>();
    private final boolean folia = isFolia();
    private GlobalRegionScheduler globalRegionScheduler;
    private BukkitScheduler bukkitScheduler;

    // 不会限制的生成方式
    private static final List<CreatureSpawnEvent.SpawnReason> REASONS = Arrays.asList(
            CreatureSpawnEvent.SpawnReason.METAMORPHOSIS,
            CreatureSpawnEvent.SpawnReason.BUILD_IRONGOLEM,
            CreatureSpawnEvent.SpawnReason.BUILD_SNOWMAN,
            CreatureSpawnEvent.SpawnReason.BUILD_WITHER,
            CreatureSpawnEvent.SpawnReason.BEEHIVE,
            CreatureSpawnEvent.SpawnReason.CUSTOM,
            CreatureSpawnEvent.SpawnReason.COMMAND,
            CreatureSpawnEvent.SpawnReason.SPAWNER_EGG,
            CreatureSpawnEvent.SpawnReason.CURED);

    @Override
    public void onEnable() {
        outPut("&e-----------------------");
        outPut("&b性能优化器 &dBy阿然");
        outPut("&b插件版本:" + getDescription().getVersion());
        outPut("&b服务器版本:" + getServer().getVersion());
        outPut("&cQQ 2263055528");
        outPut("&e-----------------------");

        // 加载数据
        loadConfig();

        // 检查更新
        //updateCheck();

        // 注册 event
        Bukkit.getPluginManager().registerEvents(this, this);

        // 计时器
        if (folia) {
            globalRegionScheduler = Bukkit.getServer().getGlobalRegionScheduler();
            globalRegionScheduler.runAtFixedRate(this,
                    scheduledTask -> tick(), 20 * 60, 20 * 60);
            getConfig().set("stacker", false);
        } else {
            bukkitScheduler = Bukkit.getServer().getScheduler();
            bukkitScheduler.runTaskTimer(this, this::tick, 0, 20 * 60);
        }
        super.onEnable();
    }

    private void tick() {
        warning = new HashMap<>();

        if (spawnTime > 0) spawnTime--;
        if (spawnTime == 0) {
            spawnTime = getConfig().getInt("spawnTime", 30);
            mob = new HashMap<>();
        }

        try {
            tps = Double.parseDouble(PlaceholderAPI.setPlaceholders(null, "%server_tps_1%"));
        } catch (Exception e) {
            //e.printStackTrace();
        }

        if (getConfig().getBoolean("stacker") && tps >= tpsCheck) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Entity[] entities = player.getLocation().getChunk().getEntities();
                for (Entity entity : entities) {
                    if (stackerList.contains(entity.getType().name())) {
                        String name = getEntityName(entity);
                        if (name != null && name.contains(color("&cx"))) {
                            int count = 0;
                            try {
                                count += Integer.parseInt(name.replace(color("&cx"), ""));
                            } catch (NumberFormatException ignored) {
                            }
                            for (int ii = 1; ii < count; ii++) {
                                Location location = entity.getLocation();
                                Objects.requireNonNull(location.getWorld()).spawnEntity(location, entity.getType());
                            }
                        }
                        setEntityName(entity, null);
                    }
                }
            }
        }
    }

    @Override
    public void onDisable() {
        if (folia) {
            globalRegionScheduler.cancelTasks(this);
        } else {
            bukkitScheduler.cancelTask(0);
        }
        super.onDisable();
    }

    /**
     * 是 Folia 端
     *
     * @return boolean
     */
    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    /**
     * 雪球刷怪塔
     */
    @EventHandler
    public void onProjectileLaunchEvent(ProjectileLaunchEvent event) {

        if (getConfig().getBoolean("snowball", true) &&
                event.getEntityType() == EntityType.SNOWBALL) {
            int delay = getConfig().getInt("snowballDisappearDely", 60);
            Entity entity = event.getEntity();
            if (folia) {
                entity.getScheduler().runDelayed(
                        this,
                        scheduledTask -> entity.remove(),
                        ()-> {},
                        delay);
            } else {
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                        this,
                        entity::remove,
                        delay);
            }
        }
    }

    @EventHandler
    public void onEntityPlaceEvent(EntityPlaceEvent event) {
        Entity en = event.getEntity();
        EntityType et = event.getEntityType();
        // 限制船过多
        if (getConfig().getBoolean("boatLimit", true) && et.toString().contains("BOAT")) {
            Entity[] entities = en.getLocation().getChunk().getEntities();
            int liveCount = 0;
            for (Entity entity : entities) {
                if (entity.getType().toString().contains("BOAT")) {
                    liveCount++;
                }
            }
            if (liveCount >= getConfig().getInt("chunkBoatLimit")) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 生物生成时间
     */

    @EventHandler
    public void onEntitySpawnEvent(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        if (event.isCancelled()) return;

        // 限制刷怪笼
        if (getConfig().getBoolean("spawner") && event.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.SPAWNER)) {
            int i = (int) (Math.random() * 100);
            if (i > getConfig().getInt("spawnerChange")){
                event.setCancelled(true);
                return;
            }
        }

        // 限制生物过多
        if (getConfig().getBoolean("mob") && !REASONS.contains(event.getSpawnReason())) {
            if (getConfig().getStringList("mobList").contains(entity.getType().toString())) {
                int num = (int) (Math.random() * 100);
                if (num > getConfig().getInt("spawnChange")) {
                    event.setCancelled(true);
                } else {
                    Location loc = event.getLocation();
                    int lx = loc.getBlockX() / 100;
                    int lz = loc.getBlockZ() / 100;
                    if (loc.getBlockX() < 0) {
                        lx--;
                    }
                    if (loc.getBlockZ() < 0) {
                        lz--;
                    }

                    Entity[] entities = event.getLocation().getChunk().getEntities();
                    int liveCount = 0;
                    for (Entity e : entities) {
                        if (e.getType() == entity.getType()) {
                            liveCount++;
                        }
                    }
                    if (liveCount >= getConfig().getInt("chunkLimit")) {
                        event.setCancelled(true);
                        return;
                    }

                    String name = lx + Objects.requireNonNull(loc.getWorld()).getName() + lz + entity.getType();
                    int count = 0;
                    if (mob.containsKey(name)) count = mob.get(name);
                    if (count >= getConfig().getInt("spawnLimit")) {
                        event.setCancelled(true);
                    } else {
                        count++;
                        mob.put(name, count);
                    }
                }
            }
        }

        // 生物堆叠器
        if (getConfig().getBoolean("stacker") && tps < tpsCheck) {
            if (stackerList.contains(event.getEntityType().toString())) {
                Entity[] entities = entity.getLocation().getChunk().getEntities();
                if (entities.length == 0) return;
                int liveCount = 0;
                int log = 0;
                int count = 0;
                int base = 0;
                for (int i = 0; i < entities.length; i++) {
                    if (event.getEntityType().equals(entities[i].getType())) {
                        String name = getEntityName(entities[i]);
                        if (name == null) {
                            liveCount++;
                            if (liveCount > 1) {
                                entities[i].remove();
                                count++;
                            } else if (liveCount == 1) log = i;
                        } else if (name.contains(color("&cx"))) base = i;
                    }
                }
                if (base == 0) {
                    count++;
                    if (!entities[log].isDead() && count>1) setEntityName(entities[log], color("&cx")+count);
                } else {
                    int baseCount = 0;
                    try {
                        baseCount += Integer.parseInt(getEntityName(entities[base]).replace(color("&cx"),""));
                    } catch (NumberFormatException ignored) {
                    }
                    count += baseCount;
                    if (!entities[base].isDead() && count>1) setEntityName(entities[base], color("&cx")+count);
                }
            }
        }
    }

    public void setEntityName(Entity entity, String name) {
        if (folia) {
            if (name == null) {
                entity.customName(null);
            } else {
                entity.customName(Component.text(name));
            }
        } else {
            entity.setCustomName(name);
        }
    }

    public String getEntityName(Entity entity) {
        if (folia) {
            if (entity.customName() == null) return null;
            return String.valueOf(entity.customName());
        } else {
            return entity.getCustomName();
        }
    }

    @EventHandler
    public void onEntityDeathEvent(EntityDeathEvent event){
        //生物堆叠器分离
        if (getConfig().getBoolean("stacker")) {
            Entity entity = event.getEntity();
            String name = getEntityName(entity);
            if (stackerList.contains(event.getEntityType().toString()) &&
                    name != null && name.contains(color("&cx"))) {
                int count = 0;
                try {
                    count += Integer.parseInt(name.replace(color("&cx"),""));
                } catch (NumberFormatException ignored) {
                }
                if (count>1) {
                    count--;
                    Location location = entity.getLocation();
                    LivingEntity newMob = (LivingEntity) Objects.requireNonNull(location.getWorld()).spawnEntity(location, entity.getType());
                    if (count>1) setEntityName(newMob, color("&cx") + count);
                }
            }
        }

    }

    @EventHandler
    public void onPistonExtendEvent(BlockPistonExtendEvent event) {
        if (getConfig().getBoolean("observerPiston", false)) {
            for (Block block : event.getBlocks()) {
                if (block.getType() == Material.OBSERVER) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        redstoneCheck(event.getBlock().getLocation());

    }

    private void removeRedstoneBlock(Block block) {
        block.setType(Material.AIR);
        if (folia) {
            Bukkit.getServer().getRegionScheduler().run(
                    this,
                    block.getLocation(),
                    scheduledTask -> block.setType(Material.AIR));
        } else {
            Bukkit.getServer().getScheduler().runTask(
                    this, () -> block.setType(Material.AIR));
        }
    }

    private void redstoneCheck(Location loc) {
        if (!loc.getChunk().isLoaded()) return;
        if (getConfig().getBoolean("redstoneClock") &&
                redstone.containsKey(loc) &&
                !getConfig().getStringList("redstoneDisabledWorld")
                        .contains(loc.getWorld().getName())) {
            long time = System.currentTimeMillis() - redstone.get(loc);
            long redstoneDely = getConfig().getInt("redstoneDely", 500);
            if (time > 1 && time < redstoneDely) {
                int count = warning.getOrDefault(loc, 0) + 1;
                if (count >= getConfig().getInt("redstoneHold", 10)) {
                    warning.remove(loc);
                    redstone.remove(loc);
                    removeRedstoneBlock(loc.getBlock());
                    if (getConfig().getBoolean("redstoneLightning", true))
                        loc.getWorld().strikeLightningEffect(loc);
                    if (getConfig().getBoolean("redstoneWarning", true))
                        outPut("&c检测到" + time + "ms红石高频 " + loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
                } else {
                    warning.put(loc, count);
                }
            } else if (time > (redstoneDely * getConfig().getInt("redstoneHold", 20))) {
                warning.remove(loc);
            }
        }
        redstone.put(loc, System.currentTimeMillis());
    }

    /**
     * 限制红石
     */
    @EventHandler
    public void onBlockRedstoneEvent(BlockRedstoneEvent event) {
        if (getConfig().getBoolean("redstone")) {
            redstoneCheck(event.getBlock().getLocation());

            if (tps < 16 && event.getBlock().getLocation().getBlockY() > 200) {
                event.setNewCurrent(0);
                return;
            }
            if (tps < 14 && event.getBlock().getLocation().getBlockY() > 150) {
                event.setNewCurrent(0);
                return;
            }
            if (tps < 12 && event.getBlock().getLocation().getBlockY() > 100) {
                event.setNewCurrent(0);
                return;
            }
            if (tps < 10) {
                event.setNewCurrent(0);
            }
        }

    }

    /**
     * 指令输入
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command cmd, @NotNull String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("ro")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")){
                if (sender.hasPermission("ro.admin")) {
                    loadConfig();
                    sender.sendMessage(prefix + color("&a重载成功"));
                    return true;
                } else {
                    sender.sendMessage(prefix + color("&c没有权限"));
                }
            }
        }

        if (!(sender instanceof Player)) {
            outPut("&c该指令不能在控制台输入");
            return true;
        }

        //Player player = (Player) sender;

        sender.sendMessage("未知指令");
        return true;
    }

    /**
     * 加载配置文件
     */
    private void loadConfig(){
        //加载config
        if (!new File(getDataFolder() + File.separator + "config.yml").exists()) {
            saveDefaultConfig();
        }
        reloadConfig();

        prefix = color(getConfig().getString("prefix", "&b性能优化器>>>"));

        stackerList = getConfig().getStringList("stackerList");
        tpsCheck = getConfig().getDouble("tpsCheck");
    }

    /**
     * 文本颜色
     */
    private static String color(String text){
        return text.replace("&","§");
    }

    /**
     * 后台信息
     */
    public void outPut(String msg){
        Bukkit.getConsoleSender().sendMessage(color(msg));
    }
}
