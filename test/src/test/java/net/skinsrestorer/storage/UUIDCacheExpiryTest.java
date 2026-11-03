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
package net.skinsrestorer.storage;

import ch.jalu.configme.SettingsManager;
import net.skinsrestorer.SRExtension;
import net.skinsrestorer.SettingsHelper;
import net.skinsrestorer.api.property.SkinProperty;
import net.skinsrestorer.shared.config.StorageConfig;
import net.skinsrestorer.shared.connections.MojangAPIImpl;
import net.skinsrestorer.shared.log.SRLogger;
import net.skinsrestorer.shared.plugin.SRPlugin;
import net.skinsrestorer.shared.storage.CacheStorageImpl;
import net.skinsrestorer.shared.storage.SkinStorageImpl;
import net.skinsrestorer.shared.storage.adapter.AdapterReference;
import net.skinsrestorer.shared.storage.adapter.file.FileAdapter;
import net.skinsrestorer.shared.storage.model.cache.MojangCacheData;
import net.skinsrestorer.shared.storage.model.skin.PlayerSkinData;
import net.skinsrestorer.shared.utils.SRHelpers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SRExtension.class)
class UUIDCacheExpiryTest {
    private static final long NOW = 1_800_000_000L;
    private static final UUID UUID_VALUE = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final String NAME = "CacheFixture";
    @TempDir
    private Path tempDir;
    private final SettingsManager settings = mock(SettingsManager.class);
    private final SRPlugin plugin = mock(SRPlugin.class);
    private final SRLogger logger = mock(SRLogger.class);
    private final MojangAPIImpl mojang = mock(MojangAPIImpl.class);
    private final AdapterReference reference = new AdapterReference();
    private CacheStorageImpl cache;

    @BeforeEach
    void setup() {
        SettingsHelper.returnDefaultsForAllProperties(settings);
        when(plugin.getDataFolder()).thenReturn(tempDir);
        reference.setAdapter(new FileAdapter(plugin, settings, logger));
        cache = new CacheStorageImpl(logger, mojang, settings, reference, null);
    }

    @ParameterizedTest
    @CsvSource({
            "60, 30, true", "60, 90, true", "60, 1800, true", "60, 3590, true",
            "60, 3600, true", "60, 3601, false", "60, 3610, false",
            "1, 59, true", "1, 60, true", "1, 61, false",
            "2147483647, 2147483648, true", "0, 999999, true"
    })
    void persistedCacheUsesConfiguredMinutes(int minutes, long ageSeconds, boolean expected) throws Exception {
        when(settings.getProperty(StorageConfig.UUID_EXPIRES_AFTER)).thenReturn(minutes);
        reference.get().setCachedUUID(NAME, MojangCacheData.of(UUID_VALUE, NOW - ageSeconds));
        // Re-open the real adapter so a purely in-memory fixture cannot satisfy the test.
        reference.setAdapter(new FileAdapter(plugin, settings, logger));
        try (var clock = mockStatic(SRHelpers.class, CALLS_REAL_METHODS)) {
            clock.when(SRHelpers::getEpochSecond).thenReturn(NOW);
            assertEquals(expected, cache.getCachedData(NAME, false).isPresent());
        }
        assertEquals(NOW - ageSeconds, reference.get().getCachedUUID(NAME).orElseThrow().getTimestamp());
    }

    @Test
    void allowExpiredStillReturnsOldPersistedEntry() throws Exception {
        reference.get().setCachedUUID(NAME, MojangCacheData.of(UUID_VALUE, NOW - 7200));
        try (var clock = mockStatic(SRHelpers.class, CALLS_REAL_METHODS)) {
            clock.when(SRHelpers::getEpochSecond).thenReturn(NOW);
            assertEquals(Optional.of(UUID_VALUE), cache.getUUID(NAME, true));
        }
        verifyNoInteractions(mojang);
    }

    @Test
    void ninetySecondUuidReadDoesNotRequestNetwork() throws Exception {
        reference.get().setCachedUUID(NAME, MojangCacheData.of(UUID_VALUE, NOW - 90));
        try (var clock = mockStatic(SRHelpers.class, CALLS_REAL_METHODS)) {
            clock.when(SRHelpers::getEpochSecond).thenReturn(NOW);
            assertEquals(Optional.of(UUID_VALUE), cache.getUUID(NAME, false));
        }
        verifyNoInteractions(mojang);
    }

    @Test
    void ninetySecondSkinLookupReturnsPersistedSkinWithoutNetwork() throws Exception {
        SkinProperty property = SkinProperty.of("fixture-value", "fixture-signature");
        reference.get().setCachedUUID(NAME, MojangCacheData.of(UUID_VALUE, NOW - 90));
        reference.get().setPlayerSkinData(UUID_VALUE, PlayerSkinData.of(UUID_VALUE, NAME, property, NOW));
        SkinStorageImpl skins = new SkinStorageImpl(logger, cache, mojang, null, settings, reference, null);
        try (var clock = mockStatic(SRHelpers.class, CALLS_REAL_METHODS)) {
            clock.when(SRHelpers::getEpochSecond).thenReturn(NOW);
            var result = skins.getPlayerSkin(NAME, false);
            assertTrue(result.isPresent());
            assertEquals(property, result.orElseThrow().getSkinProperty());
        }
        verifyNoInteractions(mojang);
    }

    @Test
    void expiredUuidEntryStillRefreshesAndPersistsNetworkResult() throws Exception {
        UUID refreshed = UUID.fromString("00000000-0000-0000-0000-000000000456");
        reference.get().setCachedUUID(NAME, MojangCacheData.of(UUID_VALUE, NOW - 3610));
        when(mojang.getUUID(NAME)).thenReturn(Optional.of(refreshed));
        try (var clock = mockStatic(SRHelpers.class, CALLS_REAL_METHODS)) {
            clock.when(SRHelpers::getEpochSecond).thenReturn(NOW);
            assertEquals(Optional.of(refreshed), cache.getUUID(NAME, false));
        }
        reference.setAdapter(new FileAdapter(plugin, settings, logger));
        assertEquals(Optional.of(refreshed), reference.get().getCachedUUID(NAME).orElseThrow().getUniqueId());
        assertEquals(NOW, reference.get().getCachedUUID(NAME).orElseThrow().getTimestamp());
    }

    @Test
    void negativeCacheRemainsValidForConfiguredMinutes() throws Exception {
        reference.get().setCachedUUID(NAME, MojangCacheData.of(null, NOW - 90));
        try (var clock = mockStatic(SRHelpers.class, CALLS_REAL_METHODS)) {
            clock.when(SRHelpers::getEpochSecond).thenReturn(NOW);
            assertTrue(cache.getCachedData(NAME, false).isPresent());
            assertTrue(cache.getUUID(NAME, false).isEmpty());
        }
        verifyNoInteractions(mojang);
    }
}
