package net.earthmc.mycelium.utilities.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import net.earthmc.mycelium.api.Mycelium;
import net.earthmc.mycelium.utilities.NetworkUtilities;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * Automatically shuts down the proxy when the
 */
public class TriggerShutdownListener {
    private final NetworkUtilities plugin;
    private final ProxyServer proxy;

    public TriggerShutdownListener(NetworkUtilities plugin) {
        this.plugin = plugin;
        proxy = plugin.proxy();
    }

    @Subscribe
    public void playerDisconnect(DisconnectEvent event) {
        // The player is already removed from the player count at this point
        if (canShutdown()) {
            plugin.logger().info("The last player has disconnected from the proxy, attempting to shut down in 5 seconds...");
            proxy.getScheduler().buildTask(plugin, this::checkShutdown).delay(5, TimeUnit.SECONDS).schedule();
        }
    }

    public void checkShutdown() {
        if (!canShutdown()) {
            return;
        }

        final Logger logger = plugin.logger();
        logger.info("======================================================");
        logger.info("");
        logger.info("This proxy ({}) has emptied, shutting down...", Mycelium.api().platform().id());
        logger.info("");
        logger.info("======================================================");

        proxy.shutdown();
    }

    private boolean canShutdown() {
        return !proxy.isShuttingDown() && proxy.getPlayerCount() == 0 && !plugin.portCurrentlyBound();
    }
}
