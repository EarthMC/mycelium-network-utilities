package net.earthmc.velocitycommands;

import com.google.inject.Inject;
import com.imaginarycode.minecraft.redisbungee.RedisBungeeAPI;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.earthmc.velocitycommands.commands.AlertCommand;
import net.earthmc.velocitycommands.commands.SendCommand;
import net.earthmc.velocitycommands.commands.ServerCommand;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Plugin(id = "velocitycommands", name = "VelocityCommands", version = "0.0.2", authors = {"Warriorrr"}, dependencies = { @Dependency(id = "redisbungee", optional = true) })
public class VelocityCommands {
    private final ProxyServer proxy;
    private boolean usingRedisBungee;

    @Inject
    public VelocityCommands(ProxyServer proxy) {
        this.proxy = proxy;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        final SendCommand sendCommand = new SendCommand(this);
        final AlertCommand alertCommand = new AlertCommand(this);

        if (proxy.getPluginManager().isLoaded("redisbungee")) {
            usingRedisBungee = true;

            proxy.getEventManager().register(this, sendCommand);
            proxy.getEventManager().register(this, alertCommand);

            RedisBungeeAPI.getRedisBungeeApi().registerPubSubChannels(SendCommand.REDIS_CHANNEL);
            RedisBungeeAPI.getRedisBungeeApi().registerPubSubChannels(AlertCommand.REDIS_CHANNEL);
        }

        proxy.getCommandManager().register("send", sendCommand);
        proxy.getCommandManager().register("alert", alertCommand);

        // Remove the original server command and replace it with ours
        proxy.getCommandManager().unregister("server");
        proxy.getCommandManager().register("server", new ServerCommand(this));
    }

    public ProxyServer proxy() {
        return this.proxy;
    }

    public Set<UUID> getPlayersOnServer(RegisteredServer server) {
        if (usingRedisBungee)
            return RedisBungeeAPI.getRedisBungeeApi().getPlayersOnServer(server.getServerInfo().getName());
        else
            return server.getPlayersConnected().stream().map(Player::getUniqueId).collect(Collectors.toSet());
    }

    public Set<UUID> getAllPlayers() {
        if (usingRedisBungee)
            return RedisBungeeAPI.getRedisBungeeApi().getPlayersOnline();
        else
            return proxy.getAllPlayers().stream().map(Player::getUniqueId).collect(Collectors.toSet());
    }

    public Collection<String> getAllPlayerNames() {
        if (usingRedisBungee)
            return RedisBungeeAPI.getRedisBungeeApi().getHumanPlayersOnline();
        else
            return proxy.getAllPlayers().stream().map(Player::getUsername).collect(Collectors.toSet());
    }

    public Optional<String> getServerForPlayer(UUID uuid) {
        final Optional<Player> player = proxy.getPlayer(uuid);
        if (player.isPresent())
            return player.get().getCurrentServer().map(conn -> conn.getServerInfo().getName());
        else if (usingRedisBungee())
            return Optional.ofNullable(RedisBungeeAPI.getRedisBungeeApi().getServerNameFor(uuid));

        return Optional.empty();
    }

    public Optional<UUID> getUUIDForPlayer(String name) {
        final Optional<Player> player = proxy.getPlayer(name);
        if (player.isPresent())
            return player.map(Player::getUniqueId);
        else if (usingRedisBungee())
            return Optional.ofNullable(RedisBungeeAPI.getRedisBungeeApi().getUuidFromName(name, false)).filter(uuid -> RedisBungeeAPI.getRedisBungeeApi().isPlayerOnline(uuid));

        return Optional.empty();
    }

    public boolean usingRedisBungee() {
        return this.usingRedisBungee;
    }
}
