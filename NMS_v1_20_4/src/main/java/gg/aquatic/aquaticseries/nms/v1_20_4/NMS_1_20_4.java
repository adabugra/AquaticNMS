package gg.aquatic.aquaticseries.nms.v1_20_4;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import gg.aquatic.aquaticseries.lib.StringExtKt;
import gg.aquatic.aquaticseries.lib.adapt.AquaticString;
import gg.aquatic.aquaticseries.lib.audience.AquaticAudience;
import gg.aquatic.aquaticseries.lib.nms.NMSAdapter;
import gg.aquatic.aquaticseries.lib.nms.listener.PacketListenerAdapter;
import gg.aquatic.aquaticseries.lib.util.EventExtKt;
import gg.aquatic.aquaticseries.nms.v1_20_4.listener.PacketListenerAdapterImpl;
import gg.aquatic.aquaticseries.paper.adapt.PaperString;
import gg.aquatic.aquaticseries.spigot.adapt.SpigotString;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R3.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;

public class NMS_1_20_4 implements NMSAdapter {

    private final Map<Integer, Entity> entities = new HashMap<>();

    @Override
    public int spawnEntity(Location location, String s, AquaticAudience abstractAudience, Consumer<org.bukkit.entity.Entity> consumer) {
        final var entityOpt = EntityType.byString(s.toLowerCase());
        if (entityOpt.isEmpty()) {
            return -1;
        }

        final var worldServer = ((CraftWorld) Objects.requireNonNull(location.getWorld())).getHandle();
        final var entity = entityOpt.get().create(
                worldServer,
                null,
                null,
                new BlockPos((int) location.toVector().getX(), (int) location.toVector().getY(), (int) location.toVector().getZ()),
                MobSpawnType.COMMAND,
                true,
                false
        );

        entity.absMoveTo(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());

        if (consumer != null) {
            consumer.accept(entity.getBukkitEntity());
        }

        //final var packetData = new ClientboundSetEntityDataPacket(entity.getId(),Objects.requireNonNullElse(entity.getEntityData().getNonDefaultValues(), new LinkedList<>()));
        sendPacket(abstractAudience, entity.getAddEntityPacket(), true);
        try {
            var field = SynchedEntityData.class.getDeclaredField("e");
            field.setAccessible(true);
            var data = (Int2ObjectMap<SynchedEntityData.DataItem<?>>) field.get(entity.getEntityData());
            if (data != null) {
                var values = new ArrayList<SynchedEntityData.DataValue<?>>();
                data.forEach((key, value) -> {
                    values.add(value.value());
                });
                final var packetData = new ClientboundSetEntityDataPacket(entity.getId(), values);
                sendPacket(abstractAudience, packetData, true);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        if (entity instanceof LivingEntity livingEntity) {
            List<Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> list = new ArrayList<>();
            for (EquipmentSlot value : EquipmentSlot.values()) {
                list.add(Pair.of(value, livingEntity.getItemBySlot(value)));
            }
            final var packet = new ClientboundSetEquipmentPacket(entity.getId(), list);
            sendPacket(abstractAudience, packet, true);
        }

        entities.put(entity.getId(), entity);
        return entity.getId();
    }

    @Override
    public org.bukkit.entity.Entity getEntity(int i) {
        var entity = entities.get(i);
        if (entity == null) return null;
        return entity.getBukkitEntity();
    }

    @Override
    public void despawnEntity(List<Integer> list, AquaticAudience abstractAudience) {
        final var packet = new ClientboundRemoveEntitiesPacket(new IntArrayList(list));
        sendPacket(abstractAudience, packet, true);

    }

    @Override
    public void updateEntity(int i, Consumer<org.bukkit.entity.Entity> consumer, AquaticAudience abstractAudience) {
        net.minecraft.world.entity.Entity entity = entities.get(i);

        if (consumer != null) {
            consumer.accept(entity.getBukkitEntity());
        }

        //final var packetMetadata = new ClientboundSetEntityDataPacket(entity.getId(), Objects.requireNonNullElse(entity.getEntityData().getNonDefaultValues(), new LinkedList<>()));
        //sendPacket(new ArrayList<>(Bukkit.getOnlinePlayers()), packetMetadata, true);
        try {
            var field = SynchedEntityData.class.getDeclaredField("e");
            field.setAccessible(true);
            var data = (Int2ObjectMap<SynchedEntityData.DataItem<?>>) field.get(entity.getEntityData());
            if (data != null) {
                var values = new ArrayList<SynchedEntityData.DataValue<?>>();
                data.forEach((key, value) -> {
                    values.add(value.value());
                });
                final var packetData = new ClientboundSetEntityDataPacket(entity.getId(), values);
                sendPacket(abstractAudience, packetData, true);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }


        if (entity instanceof LivingEntity livingEntity) {
            final List<Pair<EquipmentSlot, ItemStack>> equipmentMap = new ArrayList<>();
            for (EquipmentSlot value : EquipmentSlot.values()) {
                equipmentMap.add(Pair.of(value, livingEntity.getItemBySlot(value)));
            }
            final var packet = new ClientboundSetEquipmentPacket(entity.getId(), equipmentMap);
            sendPacket(abstractAudience, packet, true);
        }
    }

    @Override
    public void updateEntityVelocity(int i, Vector vector, AquaticAudience abstractAudience) {
        net.minecraft.world.entity.Entity entity = entities.get(i);
        entity.getBukkitEntity().setVelocity(vector);
        final var packet = new ClientboundSetEntityMotionPacket(i, new Vec3(vector.getX(), vector.getY(), vector.getZ()));
        sendPacket(abstractAudience, packet, true);
    }


    @Override
    public void teleportEntity(int i, Location location, AquaticAudience abstractAudience) {
        if (!entities.containsKey(i)) {
            return;
        }
        net.minecraft.world.entity.Entity entity = entities.get(i);

        entity.getBukkitEntity().teleport(location);
        final var packet = new ClientboundTeleportEntityPacket(entity);

        sendPacket(abstractAudience, packet, true);
    }

    @Override
    public void moveEntity(int i, Location location, AquaticAudience abstractAudience) {
        if (!entities.containsKey(i)) {
            return;
        }
        net.minecraft.world.entity.Entity entity = entities.get(i);
        Location prevLoc = entity.getBukkitEntity().getLocation();

        entity.getBukkitEntity().teleport(location);
        final var packet = new ClientboundMoveEntityPacket.PosRot(
                i,
                (short) ((location.getX() * 32 - prevLoc.getX() * 32) * 128),
                (short) ((location.getY() * 32 - prevLoc.getY() * 32) * 128),
                (short) ((location.getZ() * 32 - prevLoc.getZ() * 32) * 128),
                (byte) ((int) (location.getYaw() * 256.0F / 360.0F)),
                (byte) ((int) (location.getPitch() * 256.0F / 360.0F)),
                true
        );

        sendPacket(abstractAudience, packet, true);
        sendPacket(abstractAudience,
                new ClientboundRotateHeadPacket(entities.get(i), (byte) ((int) (location.getYaw() * 256.0F / 360.0F))), true
        );
    }

    @Override
    public void setSpectatorTarget(int i, AquaticAudience abstractAudience) {
        net.minecraft.world.entity.Entity entity = entities.get(i);
        if (entity == null) {
            for (UUID uuid : abstractAudience.getUuids()) {
                Player player = Bukkit.getPlayer(uuid);
                entity = ((CraftPlayer) Objects.requireNonNull(player)).getHandle();

                final var packet = new ClientboundSetCameraPacket(entity);
                sendPacket(List.of(player), packet, true);
            }
            return;
        }

        final var packet = new ClientboundSetCameraPacket(entity);
        sendPacket(abstractAudience, packet, true);

    }

    @Override
    public void setGamemode(GameMode gameMode, Player player) {
        final var packet = new ClientboundGameEventPacket(new ClientboundGameEventPacket.Type(3), gameMode.getValue());
        sendPacket(Arrays.asList(player), packet, true);
    }

    @Override
    public void setPlayerInfoGamemode(GameMode gameMode, Player player) {
        final var playerHandle = ((CraftPlayer) player).getHandle();

        ClientboundPlayerInfoUpdatePacket.Action action2 = ClientboundPlayerInfoUpdatePacket.Action.valueOf("UPDATE_GAME_MODE");
        final var packet = new ClientboundPlayerInfoUpdatePacket(action2, playerHandle);

        try {
            final Field packetsField;
            packetsField = packet.getClass().getDeclaredField("b");
            packetsField.setAccessible(true);

            List<ClientboundPlayerInfoUpdatePacket.Entry> list = new ArrayList<>();
            list.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                            playerHandle.getUUID(),
                            playerHandle.getGameProfile(),
                            true,
                            player.getPing(),
                            GameType.valueOf(gameMode.toString().toUpperCase()),
                            playerHandle.listName,
                            null
                    )
            );

            packetsField.set(packet, list);
            sendPacket(Arrays.asList(player), packet, true);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendPacket(List<Player> players, Packet packet, boolean isProtected) {
        if (isProtected) {
            var protectedPacket = new ProtectedPacket(packet);
            players.forEach(player -> {
                var craftPlayer = (CraftPlayer) player;
                var packetListener = craftPlayer.getHandle().connection;
                var connection = getConnection(packetListener);
                var pipeline = connection.channel.pipeline();
                pipeline.writeAndFlush(protectedPacket);
            });
            return;
        }
        players.forEach(player -> {
            ((CraftPlayer) player).getHandle().connection.send(packet);
        });
    }

    private void sendPacket(AquaticAudience audience, Packet packet, boolean isProtected) {
        sendPacket(audience.getUuids().stream().map(Bukkit::getPlayer
        ).filter(Objects::nonNull).toList(), packet, isProtected);
    }

    @Override
    public void setContainerItem(Player player, org.bukkit.inventory.ItemStack itemStack, int i) {
        var serverPlayer = ((CraftPlayer) player).getHandle();
        var container = serverPlayer.containerMenu;
        var containerId = container.containerId;

        var packet = new ClientboundContainerSetSlotPacket(containerId, container.getStateId(), i, CraftItemStack.asNMSCopy(itemStack));
        sendPacket(List.of(player), packet, true);
    }

    @Override
    public void setInventoryContent(AquaticAudience abstractAudience, InventoryType inventoryType, Collection<? extends org.bukkit.inventory.ItemStack> collection, org.bukkit.inventory.ItemStack itemStack) {

    }

    private Field connectionField;

    private Connection getConnection(final ServerGamePacketListenerImpl playerConnection) {
        try {
            if (connectionField == null) {
                for (Field declaredField : ServerCommonPacketListenerImpl.class.getDeclaredFields()) {
                    if (declaredField.getType().equals(Connection.class)) {
                        connectionField = declaredField;
                        connectionField.setAccessible(true);
                        break;
                    }
                }
            }
            if (connectionField == null) {
                throw new Exception("Could not find connection field in ServerGamePacketListenerImpl");
            }

            return (Connection) connectionField.get(playerConnection);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void sendTitleUpdate(Player player, AquaticString aquaticString) {
        var serverPlayer = ((CraftPlayer) player).getHandle();
        if (serverPlayer.containerMenu == null) {
            return;
        }
        var container = serverPlayer.containerMenu;
        var containerId = container.containerId;
        var title = serverPlayer.containerMenu.getTitle();

        Component serializedTitle = null;
        if (aquaticString instanceof PaperString paperString) {
            serializedTitle = net.minecraft.network.chat.Component.Serializer.fromJson(
                    paperString.toJson()
            );
        } else if (aquaticString instanceof SpigotString spigotString) {
            serializedTitle = CraftChatMessage.fromJSONOrString(spigotString.getFormatted());
        }

        if (serializedTitle == null) {
            return;
        }

        var packet = new ClientboundOpenScreenPacket(
                containerId,
                serverPlayer.containerMenu.getType(),
                serializedTitle
        );
        sendPacket(List.of(player), packet, true);

    }
    @Override
    public void modifyTabCompletion(TabCompletionAction tabCompletionAction, List<String> list, Player... players) {
        switch (tabCompletionAction) {
            case ADD -> {
                for (Player player : players) {
                    player.addCustomChatCompletions(list);
                }
            }
            case SET -> {
                for (Player player : players) {
                    player.setCustomChatCompletions(list);
                }
            }
            case REMOVE -> {
                for (Player player : players) {
                    player.removeCustomChatCompletions(list);
                }
            }
        }
    }

    /*
    public static final AdvancementProgress NOTIFICATION_PROGRESS = new AdvancementProgress();
    private static final AdvancementRewards advancementRewards = new AdvancementRewards(0, new ArrayList<>(), new ArrayList<>(), Optional.empty());

    public void updateScoreboardTeam(Player player) {

        var team = new PlayerTeam(new Scoreboard(),"example");
        team.setNameTagVisibility(Team.Visibility.NEVER);
        ClientboundSetPlayerTeamPacket.createPlayerPacket(team, player.getName(), ClientboundSetPlayerTeamPacket.Action.ADD);

    }

    public void sendToastMessage(Player player, AquaticString aquaticString, org.bukkit.inventory.ItemStack itemStack, boolean add, boolean isProtected) {

        var advancements = new ArrayList<AdvancementHolder>();
        var progress = new HashMap<ResourceLocation, AdvancementProgress>();

        var pluginKey = new ResourceLocation("aquatic_series_lib","toast_notification");

        ItemStack icon = CraftItemStack.asNMSCopy(itemStack);

        var message = aquaticString.getString();
        if (aquaticString instanceof PaperString paperString) {
            message = paperString.toJson();
        }
        net.minecraft.advancements.DisplayInfo advDisplay = new net.minecraft.advancements.DisplayInfo(icon, CraftChatMessage.fromJSONOrString(message), CraftChatMessage.fromJSONOrString("Toast Notification"), Optional.empty(), AdvancementType.GOAL, true, false, true);

        final HashMap<String, Criterion<?>> criteria = new HashMap<>();
        net.minecraft.advancements.Advancement adv = new net.minecraft.advancements.Advancement(Optional.empty(), Optional.of(advDisplay), advancementRewards, criteria, new AdvancementRequirements(new ArrayList<>()), false);

        advancements.add(new AdvancementHolder(pluginKey, adv));
        var packet = new ClientboundUpdateAdvancementsPacket(false, advancements, new HashSet<>(), progress);
        sendPacket(List.of(player), packet, isProtected);

        new BukkitRunnable() {

            @Override
            public void run() {
                var removed = new HashSet<ResourceLocation>();
                removed.add(pluginKey);
                var packet = new ClientboundUpdateAdvancementsPacket(false, advancements, removed, progress);
                sendPacket(List.of(player), packet, isProtected);
            }
        }.runTaskLater(AbstractAquaticSeriesLib.Companion.getINSTANCE().getPlugin(), 2);

    }
     */

    private PacketListenerAdapterImpl packetListenerAdapter = new PacketListenerAdapterImpl(this);

    @Override
    public PacketListenerAdapter packetListenerAdapter() {
        return packetListenerAdapter;
    }

    @Override
    public void resendEntitySpawnPacket(Player player, int i) {
        var entity = entities.get(i);
        if (entity == null) {
            return;
        }
        var packet = entity.getAddEntityPacket();
        sendPacket(List.of(player), packet, true);

        var dataPacket = new ClientboundSetEntityDataPacket(i, Objects.requireNonNullElse(entity.getEntityData().getNonDefaultValues(), new LinkedList<>()));
        sendPacket(List.of(player), dataPacket, true);
    }
}
