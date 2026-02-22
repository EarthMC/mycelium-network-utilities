package net.earthmc.mycelium.utilities;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.earthmc.mycelium.api.Mycelium;
import net.earthmc.mycelium.api.messaging.ChannelIdentifier;
import net.earthmc.mycelium.api.messaging.MessagingRegistrar;
import net.earthmc.mycelium.api.network.Proxy;
import net.earthmc.mycelium.api.serialization.Codecs;
import net.earthmc.mycelium.utilities.commands.AlertCommand;
import net.earthmc.mycelium.utilities.commands.GlistCommand;
import net.earthmc.mycelium.utilities.commands.SendCommand;
import net.earthmc.mycelium.utilities.commands.ServerCommand;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Plugin(id = "mycelium-network-utilities", name = "Mycelium Network Utilities", version = "0.0.1", authors = {"Warriorrr"}, dependencies = { @Dependency(id = "mycelium") })
public class NetworkUtilities {
    private final ProxyServer proxy;

    private final Logger logger;

    private boolean portCurrentlyBound = true;

    @Inject
    public NetworkUtilities(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        final SendCommand sendCommand = new SendCommand(this);
        final AlertCommand alertCommand = new AlertCommand(this);

        final CommandManager commands = proxy.getCommandManager();

        commands.unregister("send"); // there can only be one
        commands.register(commands.metaBuilder("send").build(), sendCommand);
        commands.register(commands.metaBuilder("alert").build(), alertCommand);

        // Remove the original server command and replace it with ours
        commands.unregister("server");
        commands.register(commands.metaBuilder("server").build(), new ServerCommand(this));

        commands.unregister("glist");
        final GlistCommand glistCommand = new GlistCommand(proxy, this);
        glistCommand.register();
    }

    @Subscribe(priority = Short.MIN_VALUE) // always run this last
    public void killOtherProxy(ProxyInitializeEvent event) {
        final Mycelium client = Mycelium.api();

        final MessagingRegistrar registrar = client.messaging();

        // begin checking to see if there's any other active proxies that need to close their listener
        final Collection<Proxy> proxies = client.network().proxies();
        final ChannelIdentifier.Bound<String> takeoverChannel = registrar.bind(ChannelIdentifier.identifier("takeover"), Codecs.STRING);

        if (proxies.size() > 1) {
            logger.info("Another proxy is still running, asking them to kindly turn off their listener...");
            final CompletableFuture<Void> finished = new CompletableFuture<>();

            for (final Proxy proxy : proxies) {
                if (proxy.id().equals(client.platform().id())) {
                    continue;
                }

                proxy.message(takeoverChannel, "my turn").callback(response -> {
                    logger.info("Successfully shut down the other proxy's listener.");
                    logger.info("Message from the proxy shutting down: {} ", response.data());
                    finished.complete(null);
                }).send();
            }

            try {
                finished.get(5L, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                logger.warn("Timed out waiting for other proxies to close their listeners after 5 seconds, continuing as normal");
            } catch (ExecutionException | InterruptedException e) {
                logger.error("An exception occurred while waiting for other proxies to close their listeners", e);
            }
        }

        registrar.registerPlatformChannel(takeoverChannel, incoming -> {
            if (!this.portCurrentlyBound || incoming.sender().isSelf()) {
                return;
            }

            this.proxy.closeListeners();
            this.portCurrentlyBound = false;
            incoming.buildResponse("o7").send();
        });
    }

    public ProxyServer proxy() {
        return this.proxy;
    }
}
