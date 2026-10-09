/*
 * SkinsRestorer
 * Copyright (C) 2026  SkinsRestorer Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.skinsrestorer.bukkit.paper;

import io.papermc.paper.ServerBuildInfo;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Optional;

public final class PaperUtil {
    public static InventoryHolder getInventoryHolder(Inventory inventory) {
        // Menu identity checks do not need a snapshot of a block container and its items.
        return inventory.getHolder(false);
    }

    @SuppressWarnings("removal")
    public static Optional<YamlConfiguration> getPaperConfig(Server server) {
        try {
            return Optional.of(server.spigot().getPaperConfig());
        } catch (Throwable ignored) { // We're not running paper
            return Optional.empty();
        }
    }

    public static Optional<String> getMinecraftVersionId() {
        try {
            return ServerBuildInfo.buildInfo().minecraftVersionId().describeConstable();
        } catch (Throwable ignored) { // We're not running Paper or a version that doesn't support this
            return Optional.empty();
        }
    }

    private PaperUtil() {
    }
}
