package de.dasjeff.warpMaster;

import de.dasjeff.warpMaster.api.ApiManager;
import de.dasjeff.warpMaster.command.SetWarpCommand;
import de.dasjeff.warpMaster.command.WarpCommand;
import de.dasjeff.warpMaster.command.WarpMasterCommand;
import de.dasjeff.warpMaster.command.WarpsCommand;
import de.dasjeff.warpMaster.database.DatabaseManager;
import de.dasjeff.warpMaster.database.PlayerRepository;
import de.dasjeff.warpMaster.database.WarpRepository;
import de.dasjeff.warpMaster.listener.InventoryListener;
import de.dasjeff.warpMaster.listener.PlayerJoinListener;
import de.dasjeff.warpMaster.service.WarpService;
import de.dasjeff.warpMaster.util.ConfigUtil;
import de.dasjeff.warpMaster.util.MessageUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;

/**
 * Main class for the WarpMaster plugin.
 */
public final class WarpMaster extends JavaPlugin {
    private ConfigUtil configUtil;
    private MessageUtil messageUtil;
    private DatabaseManager databaseManager;
    private WarpRepository warpRepository;
    private PlayerRepository playerRepository;
    private WarpService warpService;
    private ApiManager apiManager;
    private ExecutorService databaseExecutor;

    private static NamespacedKey warpNameKey;

    @Override
    public void onEnable() {
        // Initialize configuration
        configUtil = new ConfigUtil(this);
        messageUtil = new MessageUtil(configUtil.getConfig());

        // Initialize NamespacedKey
        warpNameKey = new NamespacedKey(this, "warp_name");

        // Initialize database
        databaseManager = new DatabaseManager(this, configUtil);
        if (!databaseManager.initialize()) {
            getLogger().severe("Failed to initialize database. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Get the executor from DatabaseManager
        databaseExecutor = databaseManager.getDatabaseExecutor();

        // Initialize repositories with executor
        warpRepository = new WarpRepository(databaseManager, getLogger(), databaseExecutor);
        playerRepository = new PlayerRepository(databaseManager, getLogger(), configUtil.getDefaultWarpLimit(), databaseExecutor);

        // Initialize services with executor and database manager
        warpService = new WarpService(warpRepository, playerRepository, configUtil, this, databaseExecutor, databaseManager);

        // Register commands
        getCommand("setwarp").setExecutor(new SetWarpCommand(messageUtil, warpService));
        getCommand("warp").setExecutor(new WarpCommand(messageUtil, warpService));
        getCommand("warps").setExecutor(new WarpsCommand(messageUtil, warpService));
        getCommand("warpmaster").setExecutor(new WarpMasterCommand(messageUtil, warpService, configUtil));

        // Register listeners
        getServer().getPluginManager().registerEvents(new InventoryListener(warpService, messageUtil, this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(warpService, this), this);

        // Initialize API
        if (configUtil.isApiEnabled()) {
            apiManager = new ApiManager(this, configUtil, warpService, databaseExecutor);
            apiManager.start();
        }

        getLogger().info("WarpMaster has been enabled!");
    }

    @Override
    public void onDisable() {
        // Shutdown API
        if (apiManager != null) {
            apiManager.stop();
        }

        // Close database connections and shutdown executor
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("WarpMaster has been disabled!");
    }

    /**
     * Gets the NamespacedKey used for storing warp names in item metadata.
     * @return The NamespacedKey for warp names.
     */
    public static NamespacedKey getWarpNameKey() {
        return warpNameKey;
    }
}
